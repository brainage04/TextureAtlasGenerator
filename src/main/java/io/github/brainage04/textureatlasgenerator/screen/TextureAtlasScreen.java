package io.github.brainage04.textureatlasgenerator.screen;

import io.github.brainage04.textureatlasgenerator.TextureAtlasGenerator;
import io.github.brainage04.textureatlasgenerator.atlas.AtlasCatalog;
import io.github.brainage04.textureatlasgenerator.atlas.AtlasExporter;
import io.github.brainage04.textureatlasgenerator.atlas.AtlasKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TextureAtlasScreen extends Screen implements AtlasExporter.Listener {
    private static final int CONTROL_HEIGHT = 20;
    private static final int PREVIEW_CELL = 36;
    private static final int PREVIEW_ITEM_SIZE = 32;

    private final Screen parent;

    private AtlasKind selectedKind = AtlasKind.VANILLA;
    private int pixelSize = AtlasExporter.DEFAULT_PIXEL_SIZE;
    private List<AtlasCatalog.Entry> catalog = List.of();
    private List<AtlasCatalog.Entry> filteredCatalog = List.of();
    private int scrollRow;
    private AtlasExporter.Progress progress;
    private Component status = Component.literal("Choose an atlas and export size.");
    private boolean statusIsError;
    private boolean exportOnOpen;

    private CycleButton<AtlasKind> atlasButton;
    private Button decreaseSizeButton;
    private Button increaseSizeButton;
    private Button exportButton;
    private EditBox searchBox;

    public TextureAtlasScreen(Screen parent) {
        super(Component.literal(TextureAtlasGenerator.MOD_NAME));
        this.parent = parent;
    }

    public void setSelection(AtlasKind kind, int requestedPixelSize) {
        selectedKind = kind;
        pixelSize = Math.clamp(
                requestedPixelSize,
                AtlasExporter.MIN_PIXEL_SIZE,
                AtlasExporter.MAX_PIXEL_SIZE
        );
    }

    public void startExportOnOpen() {
        exportOnOpen = true;
    }

    @Override
    protected void init() {
        int left = Math.max(12, width / 2 - 260);
        int controlsWidth = Math.min(520, width - 24);
        int typeWidth = Math.max(96, controlsWidth - 199);
        int controlsY = 34;

        atlasButton = addRenderableWidget(CycleButton.builder(
                        kind -> Component.literal(kind.displayName()),
                        selectedKind
                )
                .withValues(List.of(AtlasKind.values()))
                .create(
                        left,
                        controlsY,
                        typeWidth,
                        CONTROL_HEIGHT,
                        Component.literal("Atlas"),
                        (button, kind) -> {
                            selectedKind = kind;
                            reloadCatalog();
                        }
                ));

        int sizeX = left + typeWidth + 6;
        decreaseSizeButton = addRenderableWidget(Button.builder(
                Component.literal("−"),
                button -> changePixelSize(-8)
        ).bounds(sizeX, controlsY, 20, CONTROL_HEIGHT).build());
        increaseSizeButton = addRenderableWidget(Button.builder(
                Component.literal("+"),
                button -> changePixelSize(8)
        ).bounds(sizeX + 58, controlsY, 20, CONTROL_HEIGHT).build());
        exportButton = addRenderableWidget(Button.builder(
                Component.literal("Export atlas"),
                button -> export()
        ).bounds(sizeX + 82, controlsY, 111, CONTROL_HEIGHT).build());

        searchBox = addRenderableWidget(new EditBox(
                font,
                left,
                controlsY + 28,
                controlsWidth,
                CONTROL_HEIGHT,
                Component.literal("Search atlas items")
        ));
        searchBox.setHint(Component.literal("Search by item name or identifier"));
        searchBox.setResponder(ignored -> applySearch());

        reloadCatalog();
        updateControls();
        if (exportOnOpen) {
            exportOnOpen = false;
            export();
        }
    }

    private void reloadCatalog() {
        try {
            catalog = AtlasCatalog.create(selectedKind);
            applySearch();
            status = Component.literal(
                    selectedKind.displayName() + ": " + catalog.size() + " items ready for preview."
            );
            statusIsError = false;
        } catch (Throwable error) {
            catalog = List.of();
            filteredCatalog = List.of();
            reportFailure(error);
        }
    }

    private void applySearch() {
        String query = searchBox == null ? "" : searchBox.getValue().strip().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            filteredCatalog = catalog;
        } else {
            List<AtlasCatalog.Entry> matches = new ArrayList<>();
            for (AtlasCatalog.Entry entry : catalog) {
                if (entry.searchText().contains(query)) {
                    matches.add(entry);
                }
            }
            filteredCatalog = List.copyOf(matches);
        }
        scrollRow = 0;
    }

    private void changePixelSize(int amount) {
        pixelSize = Math.clamp(
                pixelSize + amount,
                AtlasExporter.MIN_PIXEL_SIZE,
                AtlasExporter.MAX_PIXEL_SIZE
        );
        updateControls();
    }

    private void export() {
        Minecraft client = Minecraft.getInstance();
        if (!AtlasExporter.start(client, selectedKind, pixelSize, this)) {
            status = Component.literal("Another atlas export is already running.");
            statusIsError = true;
        }
        updateControls();
    }

    private void updateControls() {
        boolean exporting = AtlasExporter.isExporting();
        if (atlasButton != null) {
            atlasButton.active = !exporting;
            decreaseSizeButton.active = !exporting && pixelSize > AtlasExporter.MIN_PIXEL_SIZE;
            increaseSizeButton.active = !exporting && pixelSize < AtlasExporter.MAX_PIXEL_SIZE;
            exportButton.active = !exporting && !catalog.isEmpty();
            searchBox.setEditable(!exporting);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        extractBackground(graphics, mouseX, mouseY, delta);
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        graphics.centeredText(font, title, width / 2, 14, 0xFFFFFFFF);
        int sizeX = decreaseSizeButton.getX() + decreaseSizeButton.getWidth();
        graphics.centeredText(font, pixelSize + " px", sizeX + 19, decreaseSizeButton.getY() + 6, 0xFFFFFFFF);

        renderPreview(graphics, mouseX, mouseY);
        renderStatus(graphics);
    }

    private void renderPreview(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int left = 12;
        int right = width - 12;
        int top = 92;
        int bottom = Math.max(top, height - 42);
        int previewWidth = right - left;
        int columns = Math.max(1, previewWidth / PREVIEW_CELL);
        int visibleRows = Math.max(1, (bottom - top) / PREVIEW_CELL);
        int totalRows = Math.ceilDiv(filteredCatalog.size(), columns);
        scrollRow = Math.clamp(scrollRow, 0, Math.max(0, totalRows - visibleRows));

        graphics.fill(left - 2, top - 2, right + 2, bottom + 2, 0x90000000);
        graphics.enableScissor(left, top, right, bottom);
        int firstIndex = scrollRow * columns;
        int visibleItems = visibleRows * columns;
        int lastIndex = Math.min(filteredCatalog.size(), firstIndex + visibleItems);
        for (int index = firstIndex; index < lastIndex; index++) {
            int localIndex = index - firstIndex;
            int row = localIndex / columns;
            int column = localIndex % columns;
            int x = left + column * PREVIEW_CELL + (PREVIEW_CELL - PREVIEW_ITEM_SIZE) / 2;
            int y = top + row * PREVIEW_CELL + (PREVIEW_CELL - PREVIEW_ITEM_SIZE) / 2;
            AtlasCatalog.Entry entry = filteredCatalog.get(index);

            graphics.pose().pushMatrix();
            graphics.pose().translate(x, y);
            graphics.pose().scale(2.0F, 2.0F);
            graphics.item(entry.stack(), 0, 0);
            graphics.pose().popMatrix();

            if (mouseX >= x && mouseX < x + PREVIEW_ITEM_SIZE
                    && mouseY >= y && mouseY < y + PREVIEW_ITEM_SIZE) {
                graphics.fill(x - 1, y - 1, x + PREVIEW_ITEM_SIZE + 1, y + PREVIEW_ITEM_SIZE + 1, 0x60FFFFFF);
                graphics.setTooltipForNextFrame(font, Component.literal(entry.name()), mouseX, mouseY);
            }
        }
        graphics.disableScissor();

        String count = filteredCatalog.size() == catalog.size()
                ? catalog.size() + " items"
                : filteredCatalog.size() + " of " + catalog.size() + " items";
        graphics.text(font, count, left, bottom + 6, 0xFFA0A0A0);
    }

    private void renderStatus(GuiGraphicsExtractor graphics) {
        int bottom = height - 10;
        if (progress != null) {
            int barWidth = Math.min(240, width / 3);
            int barLeft = width - barWidth - 12;
            graphics.fill(barLeft, bottom - 3, width - 12, bottom + 3, 0xFF303030);
            int completedWidth = barWidth * progress.percentage() / 100;
            graphics.fill(barLeft, bottom - 3, barLeft + completedWidth, bottom + 3, 0xFF4CAF50);
        }
        graphics.centeredText(font, status, width / 2, height - 32, statusIsError ? 0xFFFF7070 : 0xFFB0B0B0);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        if (mouseY >= 92 && mouseY < height - 42 && verticalAmount != 0.0) {
            scrollRow = Math.max(0, scrollRow - (int) Math.signum(verticalAmount));
            return true;
        }
        return false;
    }

    @Override
    public void onProgress(AtlasExporter.Progress currentProgress) {
        progress = currentProgress;
        status = Component.literal(
                currentProgress.message() + " " + currentProgress.completed() + '/' + currentProgress.total()
        );
        statusIsError = false;
        updateControls();
    }

    @Override
    public void onSuccess(AtlasExporter.Result result) {
        progress = null;
        status = Component.literal(
                "Saved " + result.itemCount() + " items to " + result.png().getFileName()
                        + (result.failedSkinLoads() == 0
                        ? "."
                        : " (" + result.failedSkinLoads() + " skins failed to download.)")
        );
        statusIsError = result.failedSkinLoads() > 0;
        updateControls();
        sendOutputLink(result.png(), result.mapping());
    }

    @Override
    public void onFailure(Throwable error) {
        progress = null;
        reportFailure(error);
        updateControls();
    }

    private void reportFailure(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            message = error.getClass().getSimpleName();
        }
        status = Component.literal("Export failed: " + message);
        statusIsError = true;
        TextureAtlasGenerator.LOGGER.error("Atlas generation failed", error);
    }

    private void sendOutputLink(Path png, Path mapping) {
        MutableComponent message = Component.literal("[Texture Atlas Generator] Saved ")
                .append(openFileLink(png))
                .append(Component.literal(" and "))
                .append(openFileLink(mapping));
        Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(message);
    }

    private static Component openFileLink(Path path) {
        return Component.literal(path.getFileName().toString()).withStyle(style -> style
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.OpenFile(path))
        );
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }
}
