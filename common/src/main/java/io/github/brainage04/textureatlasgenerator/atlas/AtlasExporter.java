package io.github.brainage04.textureatlasgenerator.atlas;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AtlasExporter {
    public static final int MIN_PIXEL_SIZE = 8;
    public static final int MAX_PIXEL_SIZE = 256;
    public static final int DEFAULT_PIXEL_SIZE = 64;
    public static final int COLUMNS = 32;

    private static final int MAX_PAGE_SIZE = 4096;
    private static final int MAX_CONCURRENT_SKIN_LOADS = 8;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AtomicBoolean EXPORTING = new AtomicBoolean();

    private final Minecraft client;
    private final AtlasKind kind;
    private final int pixelSize;
    private final Listener listener;
    private final AtlasItemRenderer renderer;
    private final List<AtlasCatalog.Entry> entries;
    private final NativeImage atlas;
    private final int atlasWidth;
    private final int atlasHeight;
    private final int columnsPerPage;
    private final int itemsPerPage;

    private int nextSkinIndex;
    private int activeSkinLoads;
    private int completedSkinLoads;
    private int failedSkinLoads;
    private int nextRenderIndex;

    private AtlasExporter(Minecraft client, AtlasKind kind, int pixelSize, Listener listener) {
        this.client = client;
        this.kind = kind;
        this.pixelSize = pixelSize;
        this.listener = listener;
        this.renderer = new AtlasItemRenderer(client);
        this.entries = AtlasCatalog.create(kind);

        int rows = Math.ceilDiv(entries.size(), COLUMNS);
        this.atlasWidth = COLUMNS * pixelSize;
        this.atlasHeight = rows * pixelSize;
        // A power-of-two page width divides the 32-column atlas, even for non-power-of-two pixel sizes.
        this.columnsPerPage = Math.min(COLUMNS, Integer.highestOneBit(MAX_PAGE_SIZE / pixelSize));
        this.itemsPerPage = columnsPerPage * (MAX_PAGE_SIZE / pixelSize);
        this.atlas = new NativeImage(atlasWidth, atlasHeight, true);
    }

    public static boolean start(Minecraft client, AtlasKind kind, int pixelSize, Listener listener) {
        if (pixelSize < MIN_PIXEL_SIZE || pixelSize > MAX_PIXEL_SIZE) {
            throw new IllegalArgumentException(
                    "Pixel size must be between " + MIN_PIXEL_SIZE + " and " + MAX_PIXEL_SIZE
            );
        }
        if (!EXPORTING.compareAndSet(false, true)) {
            return false;
        }

        try {
            AtlasExporter exporter = new AtlasExporter(client, kind, pixelSize, listener);
            exporter.start();
            return true;
        } catch (Throwable error) {
            EXPORTING.set(false);
            listener.onFailure(error);
            return true;
        }
    }

    public static boolean isExporting() {
        return EXPORTING.get();
    }

    private void start() {
        if (entries.isEmpty()) {
            fail(new IllegalStateException("The selected atlas has no items"));
            return;
        }

        if (kind == AtlasKind.VANILLA) {
            renderNextPage();
            return;
        }

        listener.onProgress(new Progress(
                Stage.DOWNLOADING_SKINS,
                0,
                entries.size(),
                "Downloading player-head textures…"
        ));
        startMoreSkinLoads();
    }

    private void startMoreSkinLoads() {
        while (activeSkinLoads < MAX_CONCURRENT_SKIN_LOADS && nextSkinIndex < entries.size()) {
            AtlasCatalog.Entry entry = entries.get(nextSkinIndex++);
            activeSkinLoads++;
            client.getSkinManager().get(entry.profile()).whenCompleteAsync(
                    (skin, error) -> client.execute(() -> completeSkinLoad(skin, error)),
                    Util.ioPool()
            );
        }
    }

    private void completeSkinLoad(Optional<?> skin, Throwable error) {
        activeSkinLoads--;
        completedSkinLoads++;
        if (error != null || skin.isEmpty()) {
            failedSkinLoads++;
        }

        if (completedSkinLoads == entries.size()) {
            renderNextPage();
            return;
        }

        if (completedSkinLoads == 1 || completedSkinLoads % 16 == 0) {
            listener.onProgress(new Progress(
                    Stage.DOWNLOADING_SKINS,
                    completedSkinLoads,
                    entries.size(),
                    failedSkinLoads == 0
                            ? "Downloading player-head textures…"
                            : "Downloading player-head textures (" + failedSkinLoads + " failed)…"
            ));
        }
        startMoreSkinLoads();
    }

    private void renderNextPage() {
        if (nextRenderIndex >= entries.size()) {
            writeOutputs();
            return;
        }

        int pageStart = nextRenderIndex;
        int pageItems = Math.min(itemsPerPage, entries.size() - pageStart);
        int pageRows = Math.ceilDiv(pageItems, columnsPerPage);
        int pageHeight = pageRows * pixelSize;
        listener.onProgress(new Progress(
                Stage.RENDERING,
                pageStart,
                entries.size(),
                "Rendering item models…"
        ));

        renderer.renderPage(
                entries,
                pageStart,
                pageItems,
                pixelSize,
                columnsPerPage,
                columnsPerPage * pixelSize,
                pageHeight,
                (page, error) -> {
                    if (error != null) {
                        fail(error);
                        return;
                    }
                    try (page) {
                        copyPage(page, pageStart, pageItems);
                    } catch (Throwable copyError) {
                        fail(copyError);
                        return;
                    }

                    nextRenderIndex = pageStart + pageItems;
                    renderNextPage();
                }
        );
    }

    private void copyPage(NativeImage page, int pageStart, int pageItems) {
        int sourceStride = page.getWidth() * 4;
        int destinationStride = atlasWidth * 4;
        ByteBuffer source = page.getPixelBytes();
        ByteBuffer destination = atlas.getPixelBytes();
        for (int firstItem = 0; firstItem < pageItems; firstItem += columnsPerPage) {
            int destinationItem = pageStart + firstItem;
            int destinationX = destinationItem % COLUMNS * pixelSize * 4;
            int destinationY = destinationItem / COLUMNS * pixelSize;
            int sourceY = firstItem / columnsPerPage * pixelSize;
            int bytes = Math.min(columnsPerPage, pageItems - firstItem) * pixelSize * 4;
            for (int y = 0; y < pixelSize; y++) {
                destination.put(
                        (destinationY + y) * destinationStride + destinationX,
                        source,
                        (sourceY + y) * sourceStride,
                        bytes
                );
            }
        }
    }

    private void writeOutputs() {
        listener.onProgress(new Progress(Stage.WRITING, entries.size(), entries.size(), "Writing PNG and mapping…"));
        Util.ioPool().execute(() -> {
            try {
                Path outputDirectory = client.gameDirectory.toPath().resolve("texture-atlases");
                Files.createDirectories(outputDirectory);
                String fileBase = kind.fileStem() + '_' + pixelSize + 'x' + pixelSize;
                Path png = outputDirectory.resolve(fileBase + ".png");
                Path mapping = outputDirectory.resolve(fileBase + ".json");
                Path temporaryPng = outputDirectory.resolve(fileBase + ".tmp.png");
                Path temporaryMapping = outputDirectory.resolve(fileBase + ".tmp.json");

                try {
                    atlas.writeToFile(temporaryPng);
                    Files.writeString(temporaryMapping, createMappingJson(), StandardCharsets.UTF_8);
                    replace(temporaryPng, png);
                    replace(temporaryMapping, mapping);
                } finally {
                    Files.deleteIfExists(temporaryPng);
                    Files.deleteIfExists(temporaryMapping);
                }

                client.execute(() -> succeed(new Result(
                        kind,
                        pixelSize,
                        entries.size(),
                        atlasWidth,
                        atlasHeight,
                        failedSkinLoads,
                        png,
                        mapping
                )));
            } catch (Throwable error) {
                client.execute(() -> fail(error));
            }
        });
    }

    private String createMappingJson() {
        JsonObject root = new JsonObject();
        root.addProperty("atlas", kind.fileStem());
        root.addProperty("pixelSize", pixelSize);
        root.addProperty("columns", COLUMNS);
        root.addProperty("rows", Math.ceilDiv(entries.size(), COLUMNS));
        root.addProperty("width", atlasWidth);
        root.addProperty("height", atlasHeight);

        JsonArray items = new JsonArray();
        for (int index = 0; index < entries.size(); index++) {
            JsonObject item = new JsonObject();
            item.addProperty("index", index);
            item.addProperty("name", entries.get(index).name());
            item.addProperty("column", index % COLUMNS);
            item.addProperty("row", index / COLUMNS);
            item.addProperty("x", index % COLUMNS * pixelSize);
            item.addProperty("y", index / COLUMNS * pixelSize);
            item.addProperty("width", pixelSize);
            item.addProperty("height", pixelSize);
            items.add(item);
        }
        root.add("items", items);
        return GSON.toJson(root) + System.lineSeparator();
    }

    private static void replace(Path source, Path destination) throws IOException {
        try {
            Files.move(
                    source,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void succeed(Result result) {
        atlas.close();
        EXPORTING.set(false);
        listener.onSuccess(result);
    }

    private void fail(Throwable error) {
        atlas.close();
        EXPORTING.set(false);
        listener.onFailure(error);
    }

    public enum Stage {
        DOWNLOADING_SKINS,
        RENDERING,
        WRITING
    }

    public record Progress(Stage stage, int completed, int total, String message) {
        public int percentage() {
            return total == 0 ? 0 : Math.clamp((int) ((long) completed * 100 / total), 0, 100);
        }
    }

    public record Result(
            AtlasKind kind,
            int pixelSize,
            int itemCount,
            int width,
            int height,
            int failedSkinLoads,
            Path png,
            Path mapping
    ) {}

    public interface Listener {
        void onProgress(Progress progress);

        void onSuccess(Result result);

        void onFailure(Throwable error);
    }
}
