package io.github.brainage04.textureatlasgenerator.atlas;

import com.google.gson.JsonObject;
import io.github.brainage04.textureatlasgenerator.TextureAtlasGenerator;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Renders an atlas — one square image, or several square pages when it would exceed
 * {@link AtlasLayout#MAX_PAGE_SIDE} — in horizontal bands and writes each page as lossless WebP
 * (or a streamed PNG when the heap cannot hold one), so neither an image nor a GPU render target
 * has to be huge.
 */
public final class AtlasExporter {
    public static final int MIN_PIXEL_SIZE = 1;
    public static final int MAX_PIXEL_SIZE = 1024;
    public static final int DEFAULT_PIXEL_SIZE = 256;

    private static final int MAX_TILE_SIZE = 4096;
    private static final int MAX_BAND_HEIGHT = 2048;
    private static final AtomicBoolean EXPORTING = new AtomicBoolean();

    private final Minecraft client;
    private final AtlasKind kind;
    private final int pixelSize;
    private final Listener listener;
    private final AtlasItemRenderer renderer;
    private final List<AtlasCatalog.Entry> entries;
    private final AtlasLayout layout;
    /** Cell size drawn on the GPU; a multiple of {@link #pixelSize}. */
    private final int renderSize;
    private final PlayerHeadSkins skins;
    private final Path outputDirectory;
    private final String fileBase;
    private final AtlasImage.Choice format;
    private final List<ItemStack> stacks;
    private final List<Path> temporaryImages = new ArrayList<>();

    /** The page being rendered and its band and tile geometry. */
    private int pageIndex;
    private AtlasLayout.Page page;
    private int side;
    private int bandRows;
    private int bandCount;
    private int tileColumns;
    private byte[] band;
    private AtlasImage image;
    private int bandIndex;
    private int tileColumn;
    private boolean finished;

    private AtlasExporter(Minecraft client, AtlasKind kind, int pixelSize, Listener listener) {
        this.client = client;
        this.kind = kind;
        this.pixelSize = pixelSize;
        this.listener = listener;
        this.renderer = new AtlasItemRenderer(client);
        this.entries = AtlasCatalog.create(kind);
        this.layout = AtlasLayout.of(entries.size(), pixelSize);
        this.renderSize = AtlasItemRenderer.renderSize(pixelSize);
        this.skins = kind.usesPlayerHeads() ? new PlayerHeadSkins(client, entries) : null;
        this.outputDirectory = client.gameDirectory.toPath().resolve("texture-atlases");
        this.fileBase = fileBase(kind, pixelSize);
        this.format = AtlasImage.choose(layout.pages().getFirst().side(pixelSize));
        this.stacks = entries.stream().map(AtlasCatalog.Entry::stack).toList();
    }

    /** {@code <stem>_<size>x<size>}, the name shared by the images and their mappings. */
    public static String fileBase(AtlasKind kind, int pixelSize) {
        return kind.fileStem() + '_' + pixelSize + 'x' + pixelSize;
    }

    /**
     * Starts an export on the render thread. Returns {@code false} if another export is running.
     *
     * @throws IllegalArgumentException if {@code pixelSize} is out of range
     */
    public static boolean start(Minecraft client, AtlasKind kind, int pixelSize, Listener listener) {
        if (pixelSize < MIN_PIXEL_SIZE || pixelSize > MAX_PIXEL_SIZE) {
            throw new IllegalArgumentException(
                    "Pixel size must be between " + MIN_PIXEL_SIZE + " and " + MAX_PIXEL_SIZE
            );
        }
        if (!EXPORTING.compareAndSet(false, true)) {
            return false;
        }

        AtlasExporter exporter;
        try {
            exporter = new AtlasExporter(client, kind, pixelSize, listener);
        } catch (Throwable error) {
            EXPORTING.set(false);
            listener.onFailure(error);
            return true;
        }
        exporter.run(exporter::begin);
        return true;
    }

    public static boolean isExporting() {
        return EXPORTING.get();
    }

    /** Claims the single export slot for another exporter; see {@link #release()}. */
    static boolean claim() {
        return EXPORTING.compareAndSet(false, true);
    }

    static void release() {
        EXPORTING.set(false);
    }

    private void begin() throws IOException {
        if (entries.isEmpty()) {
            throw new IllegalStateException("The selected atlas has no items");
        }
        prepareSkins();
    }

    private void prepareSkins() throws IOException {
        if (skins == null) {
            openPage(0);
            return;
        }
        progress(Stage.DOWNLOADING_SKINS, 0, entries.size(), "Downloading player-head textures…");
        skins.preload(completed -> {
            if (completed == 1 || completed % 16 == 0) {
                progress(Stage.DOWNLOADING_SKINS, completed, entries.size(), "Downloading player-head textures…");
            }
        }).whenComplete((ignored, error) -> client.schedule(() -> run(() -> {
            if (error != null) {
                throw error instanceof RuntimeException runtime ? runtime : new IllegalStateException(error);
            }
            openPage(0);
        })));
    }

    /** Removes images of an earlier export at this size whose format or page count differed. */
    private void deleteStaleImages(List<String> current) throws IOException {
        Pattern ours = Pattern.compile(Pattern.quote(fileBase) + "(_\\d+)?\\.(" + Arrays.stream(AtlasImage.Format.values())
                .map(AtlasImage.Format::extension).reduce((a, b) -> a + '|' + b).orElseThrow() + ")");
        try (Stream<Path> files = Files.list(outputDirectory)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (ours.matcher(name).matches() && !current.contains(name)) {
                    Files.deleteIfExists(file);
                }
            }
        }
    }

    private void openPage(int index) throws IOException {
        pageIndex = index;
        page = layout.pages().get(index);
        side = page.side(pixelSize);
        bandRows = Math.min(page.columns(), Math.max(1, MAX_BAND_HEIGHT / renderSize));
        bandCount = Math.ceilDiv(page.columns(), bandRows);
        tileColumns = Math.min(page.columns(), Math.max(1, MAX_TILE_SIZE / renderSize));
        bandIndex = 0;
        tileColumn = 0;
        Files.createDirectories(outputDirectory);
        String name = layout.imageFileName(fileBase, index, format.format().extension());
        Path temporary = outputDirectory.resolve(name + ".tmp");
        temporaryImages.add(temporary);
        image = AtlasImage.open(format.format(), temporary, side, side);
        band = new byte[Math.multiplyExact(Math.multiplyExact(bandRows * pixelSize, side), 4)];
        renderNextTile();
    }

    /** Renders the tile at ({@link #bandIndex}, {@link #tileColumn}), or skips it when it is empty. */
    private void renderNextTile() {
        int columns = page.columns();
        int firstRow = bandIndex * bandRows;
        int tileRows = Math.min(bandRows, columns - firstRow);
        int tileWidth = Math.min(tileColumns, columns - tileColumn);
        List<ItemStack> tileStacks = new ArrayList<>(tileRows * tileWidth);
        List<AtlasCatalog.Entry> tileEntries = new ArrayList<>(tileRows * tileWidth);
        for (int row = 0; row < tileRows; row++) {
            for (int column = 0; column < tileWidth; column++) {
                int local = (firstRow + row) * columns + tileColumn + column;
                if (local < page.entryCount()) {
                    tileStacks.add(stacks.get(page.firstEntry() + local));
                    tileEntries.add(entries.get(page.firstEntry() + local));
                } else {
                    tileStacks.add(null);
                }
            }
        }
        int xOffset = tileColumn * pixelSize * 4;
        if (tileEntries.isEmpty()) {
            for (int y = 0; y < tileRows * pixelSize; y++) {
                Arrays.fill(band, y * side * 4 + xOffset, y * side * 4 + xOffset + tileWidth * pixelSize * 4, (byte) 0);
            }
            advance();
            return;
        }

        long done = page.firstEntry() + Math.min(page.entryCount(), (long) firstRow * columns);
        progress(Stage.RENDERING, done, entries.size(), layout.pages().size() == 1
                ? "Rendering item models…"
                : "Rendering page " + (pageIndex + 1) + " of " + layout.pages().size() + "…");
        Runnable render = () -> renderer.renderPage(tileStacks, tileWidth, tileRows, renderSize,
                (pixels, width, height, error) -> run(() -> {
                    if (error != null) {
                        throw error instanceof RuntimeException runtime ? runtime : new IllegalStateException(error);
                    }
                    AtlasItemRenderer.downsample(pixels, width, height, renderSize / pixelSize, band, side * 4, xOffset);
                    advance();
                }));
        if (skins != null) {
            skins.whenReady(tileEntries, render, this::fail);
        } else {
            render.run();
        }
    }

    private void advance() {
        tileColumn += tileColumns;
        if (tileColumn < page.columns()) {
            renderNextTile();
            return;
        }
        tileColumn = 0;
        int bandHeight = Math.min(bandRows, page.columns() - bandIndex * bandRows) * pixelSize;
        boolean lastBand = bandIndex + 1 == bandCount;
        AtlasImage current = image;
        byte[] rows = band;
        Util.ioPool().execute(() -> {
            try {
                current.writeRows(rows, 0, bandHeight);
                if (lastBand) {
                    current.finish();
                }
                client.schedule(() -> run(this::nextBand));
            } catch (Throwable error) {
                client.schedule(() -> fail(error));
            }
        });
    }

    private void nextBand() throws IOException {
        bandIndex++;
        if (bandIndex < bandCount) {
            renderNextTile();
            return;
        }
        image = null;
        band = null;
        if (pageIndex + 1 < layout.pages().size()) {
            openPage(pageIndex + 1);
        } else {
            writeOutputs();
        }
    }

    private void writeOutputs() {
        String extension = format.format().extension();
        progress(Stage.WRITING, entries.size(), entries.size(), "Writing mappings…");
        JsonObject extra = new JsonObject();
        if (format.fallbackReason() != null) {
            extra.addProperty("formatNote", "PNG because " + format.fallbackReason());
        }
        List<String> imageNames = new ArrayList<>();
        for (int index = 0; index < layout.pages().size(); index++) {
            imageNames.add(layout.imageFileName(fileBase, index, extension));
        }
        AtlasMappings mappings = new AtlasMappings(kind, entries, layout, imageNames, extra);
        Util.ioPool().execute(() -> {
            List<Path> temporaryMappings = new ArrayList<>();
            try {
                Path json = outputDirectory.resolve(fileBase + ".json");
                Path text = outputDirectory.resolve(fileBase + ".txt");
                Path css = outputDirectory.resolve(fileBase + ".css");
                temporaryMappings.add(writeTemporary(json, mappings.json()));
                temporaryMappings.add(writeTemporary(text, mappings.text()));
                temporaryMappings.add(writeTemporary(css, mappings.css()));
                List<Path> images = new ArrayList<>();
                for (int index = 0; index < imageNames.size(); index++) {
                    Path imagePath = outputDirectory.resolve(imageNames.get(index));
                    replace(temporaryImages.get(index), imagePath);
                    images.add(imagePath);
                }
                replace(temporaryMappings.get(0), json);
                replace(temporaryMappings.get(1), text);
                replace(temporaryMappings.get(2), css);
                deleteStaleImages(imageNames);
                int pageSide = layout.pages().getFirst().side(pixelSize);
                String summary = entries.size() + " items ("
                        + (images.size() == 1 ? pageSide + "x" + pageSide + " px" : images.size() + " pages of up to " + pageSide + "x" + pageSide + " px")
                        + (format.fallbackReason() == null ? "" : ", PNG because " + format.fallbackReason()) + ")";
                Result result = new Result(summary, List.copyOf(images), List.of(json, text, css), outputDirectory);
                client.schedule(() -> finish(result, null));
            } catch (Throwable error) {
                temporaryMappings.forEach(AtlasExporter::deleteQuietly);
                client.schedule(() -> fail(error));
            }
        });
    }

    static Path writeTemporary(Path destination, String contents) throws IOException {
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        Files.writeString(temporary, contents, StandardCharsets.UTF_8);
        return temporary;
    }

    static void replace(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Runs a step on the render thread, turning any exception into a failed export. */
    private void run(Step step) {
        if (finished) {
            return;
        }
        try {
            step.run();
        } catch (Throwable error) {
            fail(error);
        }
    }

    private void fail(Throwable error) {
        if (image != null) {
            image.abort();
        }
        temporaryImages.forEach(AtlasExporter::deleteQuietly);
        finish(null, error);
    }

    /** Reports the outcome once. */
    private void finish(Result result, Throwable error) {
        if (finished) {
            return;
        }
        finished = true;
        band = null;
        EXPORTING.set(false);
        if (error != null) {
            listener.onFailure(error);
        } else {
            listener.onSuccess(result);
        }
    }

    private void progress(Stage stage, long completed, long total, String message) {
        listener.onProgress(new Progress(stage, completed, total, message));
    }

    static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException error) {
            TextureAtlasGenerator.LOGGER.warn("Could not delete {}", path, error);
        }
    }

    @FunctionalInterface
    interface Step {
        void run() throws Exception;
    }

    public enum Stage {
        DOWNLOADING_SKINS,
        RENDERING,
        WRITING
    }

    public record Progress(Stage stage, long completed, long total, String message) {
        public int percentage() {
            return total == 0 ? 0 : (int) Math.clamp(completed * 100 / total, 0, 100);
        }
    }

    /**
     * @param summary what was exported, e.g. {@code 1720 items (2688x2688 px)}
     * @param images the written images, in page order
     * @param mappings the written mapping files
     * @param directory the folder holding them
     */
    public record Result(String summary, List<Path> images, List<Path> mappings, Path directory) {}

    public interface Listener {
        void onProgress(Progress progress);

        void onSuccess(Result result);

        void onFailure(Throwable error);
    }
}
