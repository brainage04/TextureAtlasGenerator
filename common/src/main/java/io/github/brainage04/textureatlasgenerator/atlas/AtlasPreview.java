package io.github.brainage04.textureatlasgenerator.atlas;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.brainage04.textureatlasgenerator.TextureAtlasGenerator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The generator screen's preview: the visible cells drawn exactly as the export draws them — at
 * the chosen pixel size (up to the size they are shown at) — and shown scaled up without
 * smoothing.
 */
public final class AtlasPreview implements AutoCloseable {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(TextureAtlasGenerator.MOD_ID, "atlas_preview");
    private static final int MAX_TILE_SIZE = 4096;

    private final Minecraft client;
    private final AtlasItemRenderer renderer;

    private AtlasKind kind;
    private List<AtlasCatalog.Entry> entries = List.of();
    private int pixelSize = 1;
    /** Changes whenever the drawn items change; renders of an older generation are dropped. */
    private int generation;
    /** The stack drawn for each entry, or {@code null} before {@link #configure}. */
    private List<ItemStack> stacks;
    private PlayerHeadSkins skins;
    private String problem;
    private boolean closed;

    private int[] wanted = new int[0];
    private int shownPixels = 1;

    private boolean rendering;
    private boolean failed;
    private DynamicTexture texture;
    private int textureSide;
    private int renderedGeneration = -1;
    private int renderedPixels;
    private int slotColumns = 1;
    private final Map<Integer, Integer> slots = new HashMap<>();

    public AtlasPreview(Minecraft client) {
        this.client = client;
        this.renderer = new AtlasItemRenderer(client);
    }

    /** Sets what the preview draws. */
    public void configure(AtlasKind newKind, List<AtlasCatalog.Entry> newEntries, int newPixelSize) {
        boolean itemsChanged = newKind != kind || newEntries != entries;
        pixelSize = newPixelSize;
        failed = false;
        if (!itemsChanged) {
            return;
        }
        kind = newKind;
        entries = newEntries;
        generation++;
        slots.clear();
        problem = null;
        stacks = entries.stream().map(AtlasCatalog.Entry::stack).toList();
        skins = kind.usesPlayerHeads() ? new PlayerHeadSkins(client, entries) : null;
    }

    /**
     * Requests the cells about to be drawn.
     *
     * @param indices catalog indexes of the visible entries
     * @param displayPixels the size (in screen pixels) each cell is shown at
     */
    public void show(int[] indices, int displayPixels) {
        wanted = indices;
        shownPixels = Math.max(1, displayPixels);
    }

    /** Why the preview cannot be drawn, or {@code null}. */
    public String status() {
        return problem;
    }

    /** Starts a render when the shown cells are not all up to date. Call on the render thread. */
    public void tick() {
        if (closed || rendering || failed || stacks == null || AtlasExporter.isExporting()) {
            return;
        }
        int cellPixels = cellPixels();
        boolean current = renderedGeneration == generation && renderedPixels == cellPixels;
        if (current && Arrays.stream(wanted).allMatch(slots::containsKey)) {
            return;
        }
        render(wanted.clone(), cellPixels);
    }

    private int cellPixels() {
        return Math.min(pixelSize, shownPixels);
    }

    /** Draws cell {@code index} at ({@code x}, {@code y}); returns {@code false} if it is not rendered yet. */
    public boolean draw(GuiGraphicsExtractor graphics, int index, int x, int y, int size) {
        Integer slot = renderedGeneration == generation ? slots.get(index) : null;
        if (slot == null || texture == null) {
            return false;
        }
        float scale = 1.0F / textureSide;
        int left = slot % slotColumns * renderedPixels;
        int top = slot / slotColumns * renderedPixels;
        graphics.blit(TEXTURE, x, y, x + size, y + size,
                left * scale, (left + renderedPixels) * scale, top * scale, (top + renderedPixels) * scale);
        return true;
    }

    private void render(int[] indices, int cellPixels) {
        if (indices.length == 0) {
            return;
        }
        rendering = true;
        int requested = generation;
        int renderSize = AtlasItemRenderer.renderSize(cellPixels);
        int columns = AtlasLayout.columnsFor(indices.length);
        int side = columns * cellPixels;
        byte[] pixels = new byte[Math.multiplyExact(Math.multiplyExact(side, side), 4)];
        int tileColumns = Math.max(1, MAX_TILE_SIZE / renderSize);
        renderTile(indices, 0, tileColumns, renderSize, cellPixels, columns, pixels, requested);
    }

    /** Renders the cells from {@code first}, a tile at a time, then uploads them all. */
    private void renderTile(int[] indices, int first, int tileColumns, int renderSize, int cellPixels, int columns, byte[] pixels, int requested) {
        if (closed || requested != generation) {
            rendering = false;
            return;
        }
        if (first >= indices.length) {
            upload(indices, cellPixels, columns, pixels);
            rendering = false;
            return;
        }
        int count = Math.min(indices.length - first, tileColumns * tileColumns);
        int tileWidth = Math.min(count, tileColumns);
        int tileRows = Math.ceilDiv(count, tileWidth);
        List<ItemStack> tileStacks = new ArrayList<>(tileWidth * tileRows);
        List<AtlasCatalog.Entry> tileEntries = new ArrayList<>(count);
        for (int cell = 0; cell < tileWidth * tileRows; cell++) {
            if (cell < count) {
                tileStacks.add(stacks.get(indices[first + cell]));
                tileEntries.add(entries.get(indices[first + cell]));
            } else {
                tileStacks.add(null);
            }
        }
        Runnable draw = () -> renderer.renderPage(tileStacks, tileWidth, tileRows, renderSize, (rgba, width, height, error) -> {
            if (error != null) {
                fail(error, requested);
                return;
            }
            int tileRowBytes = tileWidth * cellPixels * 4;
            byte[] tile = new byte[tileRowBytes * tileRows * cellPixels];
            AtlasItemRenderer.downsample(rgba, width, height, renderSize / cellPixels, tile, tileRowBytes, 0);
            int rowBytes = columns * cellPixels * 4;
            for (int cell = 0; cell < count; cell++) {
                int sourceX = cell % tileWidth * cellPixels * 4;
                int sourceY = cell / tileWidth * cellPixels;
                int slot = first + cell;
                int targetX = slot % columns * cellPixels * 4;
                int targetY = slot / columns * cellPixels;
                for (int y = 0; y < cellPixels; y++) {
                    System.arraycopy(tile, (sourceY + y) * tileRowBytes + sourceX, pixels, (targetY + y) * rowBytes + targetX, cellPixels * 4);
                }
            }
            client.schedule(() -> renderTile(indices, first + count, tileColumns, renderSize, cellPixels, columns, pixels, requested));
        });
        if (skins != null) {
            skins.whenReady(tileEntries, draw, error -> fail(error, requested));
        } else {
            draw.run();
        }
    }

    private void fail(Throwable error, int requested) {
        rendering = false;
        if (closed || requested != generation) {
            return;
        }
        failed = true;
        problem = "Preview failed: " + message(error);
        TextureAtlasGenerator.LOGGER.error("Rendering the atlas preview failed", error);
    }

    private void upload(int[] indices, int cellPixels, int columns, byte[] rgba) {
        int side = columns * cellPixels;
        NativeImage image = new NativeImage(side, side, false);
        for (int y = 0, at = 0; y < side; y++) {
            for (int x = 0; x < side; x++, at += 4) {
                image.setPixel(x, y, (rgba[at + 3] & 0xFF) << 24 | (rgba[at] & 0xFF) << 16 | (rgba[at + 1] & 0xFF) << 8 | (rgba[at + 2] & 0xFF));
            }
        }
        if (texture != null && textureSide == side) {
            texture.setPixels(image);
            texture.upload();
        } else {
            texture = new DynamicTexture(() -> "Texture Atlas Generator preview", image);
            client.getTextureManager().register(TEXTURE, texture);
            textureSide = side;
        }
        slots.clear();
        for (int slot = 0; slot < indices.length; slot++) {
            slots.put(indices[slot], slot);
        }
        slotColumns = columns;
        renderedPixels = cellPixels;
        renderedGeneration = generation;
    }

    private static String message(Throwable error) {
        Throwable cause = error instanceof java.util.concurrent.CompletionException && error.getCause() != null ? error.getCause() : error;
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    /** Releases the texture. */
    @Override
    public void close() {
        closed = true;
        if (texture != null) {
            client.getTextureManager().release(TEXTURE);
            texture = null;
        }
    }
}
