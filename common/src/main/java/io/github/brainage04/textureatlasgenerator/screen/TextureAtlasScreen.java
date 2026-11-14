package io.github.brainage04.textureatlasgenerator.screen;

import io.github.brainage04.textureatlasgenerator.TextureAtlasGenerator;
import io.github.brainage04.textureatlasgenerator.atlas.AtlasCatalog;
import io.github.brainage04.textureatlasgenerator.atlas.AtlasExporter;
import io.github.brainage04.textureatlasgenerator.atlas.AtlasKind;
import io.github.brainage04.textureatlasgenerator.atlas.AtlasPreview;
import io.github.brainage04.textureatlasgenerator.atlas.GlintExporter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

public final class TextureAtlasScreen extends Screen implements AtlasExporter.Listener {
    private static final int CONTROL_HEIGHT = 20;
    private static final int PREVIEW_CELL = 36;
    private static final int PREVIEW_ITEM_SIZE = 32;
    private static final int PREVIEW_TOP = 90;

    private final Screen parent;

    private AtlasKind selectedKind = AtlasKind.VANILLA;
    private int pixelSize = AtlasExporter.DEFAULT_PIXEL_SIZE;
    private List<AtlasCatalog.Entry> catalog = List.of();
    private AtlasKind catalogKind;
    private int[] filtered = new int[0];
    private AtlasPreview preview;
    private int scrollRow;
    private AtlasExporter.Progress progress;
    private Component status = Component.translatable("textureatlasgenerator.status.choose");
    private boolean statusIsError;
    private boolean exportOnOpen;
    private int glintExportOnOpen;
    private boolean controlsExporting;

    private CycleButton<AtlasKind> atlasButton;
    private Button decreaseSizeButton;
    private Button increaseSizeButton;
    private EditBox sizeBox;
    private Button exportButton;
    private Button glintButton;
    private EditBox searchBox;

    public TextureAtlasScreen(Screen parent) {
        super(Component.translatable("textureatlasgenerator.screen.title"));
        this.parent = parent;
    }

    public void setSelection(AtlasKind kind, int requestedPixelSize) {
        selectedKind = kind;
        pixelSize =
                Math.clamp(
                        requestedPixelSize,
                        AtlasExporter.MIN_PIXEL_SIZE,
                        AtlasExporter.MAX_PIXEL_SIZE);
    }

    public void startExportOnOpen() {
        exportOnOpen = true;
    }

    public void startGlintExportOnOpen(int requestedPixelSize) {
        setPixelSize(requestedPixelSize);
        glintExportOnOpen = pixelSize;
    }

    private void configurePreview() {
        if (preview != null && !catalog.isEmpty()) {
            preview.configure(selectedKind, catalog, pixelSize);
        }
    }

    @Override
    protected void init() {
        String search = searchBox == null ? "" : searchBox.getValue();
        if (preview == null) {
            preview = new AtlasPreview(Minecraft.getInstance());
        }
        int left = Math.max(12, width / 2 - 260);
        int controlsWidth = Math.min(520, width - 24);
        int typeWidth = Math.max(96, controlsWidth - 215);
        int controlsY = 34;

        atlasButton =
                addRenderableWidget(
                        CycleButton.builder(AtlasKind::displayName, selectedKind)
                                .withValues(List.of(AtlasKind.values()))
                                .create(
                                        left,
                                        controlsY,
                                        typeWidth,
                                        CONTROL_HEIGHT,
                                        Component.translatable(
                                                "textureatlasgenerator.screen.atlas"),
                                        (button, kind) -> {
                                            selectedKind = kind;
                                            reloadCatalog();
                                            updateControls();
                                        }));

        int sizeX = left + typeWidth + 6;
        decreaseSizeButton =
                addRenderableWidget(
                        Button.builder(
                                        Component.literal("−"),
                                        button -> setPixelSize(Math.max(1, pixelSize / 2)))
                                .bounds(sizeX, controlsY, 20, CONTROL_HEIGHT)
                                .build());
        sizeBox =
                addRenderableWidget(
                        new EditBox(
                                font,
                                sizeX + 22,
                                controlsY,
                                50,
                                CONTROL_HEIGHT,
                                Component.translatable("textureatlasgenerator.screen.pixel_size")));
        sizeBox.setMaxLength(4);
        sizeBox.setValue(Integer.toString(pixelSize));
        sizeBox.setResponder(this::onSizeTyped);
        increaseSizeButton =
                addRenderableWidget(
                        Button.builder(
                                        Component.literal("+"),
                                        button -> setPixelSize(pixelSize * 2))
                                .bounds(sizeX + 74, controlsY, 20, CONTROL_HEIGHT)
                                .build());
        exportButton =
                addRenderableWidget(
                        Button.builder(
                                        Component.translatable(
                                                "textureatlasgenerator.screen.export_atlas"),
                                        button -> export())
                                .bounds(sizeX + 98, controlsY, 111, CONTROL_HEIGHT)
                                .build());

        int secondRowY = controlsY + 26;
        int glintWidth = 100;
        searchBox =
                addRenderableWidget(
                        new EditBox(
                                font,
                                left,
                                secondRowY,
                                controlsWidth - glintWidth - 6,
                                CONTROL_HEIGHT,
                                Component.translatable("textureatlasgenerator.screen.search")));
        searchBox.setHint(Component.translatable("textureatlasgenerator.screen.search_hint"));
        searchBox.setValue(search);
        searchBox.setResponder(ignored -> applySearch());
        glintButton =
                addRenderableWidget(
                        Button.builder(
                                        Component.translatable(
                                                "textureatlasgenerator.screen.export_glint"),
                                        button -> exportGlint())
                                .bounds(
                                        left + controlsWidth - glintWidth,
                                        secondRowY,
                                        glintWidth,
                                        CONTROL_HEIGHT)
                                .tooltip(
                                        Tooltip.create(
                                                Component.translatable(
                                                        "textureatlasgenerator.screen.export_glint.tooltip")))
                                .build());

        // init() runs again whenever resources reload; keep the catalog and search across it.
        if (catalogKind != selectedKind) {
            reloadCatalog();
        } else {
            applySearch();
            configurePreview();
        }
        updateControls();
        if (exportOnOpen) {
            exportOnOpen = false;
            export();
        } else if (glintExportOnOpen > 0) {
            glintExportOnOpen = 0;
            exportGlint();
        }
    }

    private void reloadCatalog() {
        try {
            catalog = AtlasCatalog.create(selectedKind);
            catalogKind = selectedKind;
            applySearch();
            status =
                    Component.translatable(
                            "textureatlasgenerator.status.catalog",
                            selectedKind.displayName(),
                            catalog.size());
            statusIsError = false;
            configurePreview();
        } catch (Throwable error) {
            catalog = List.of();
            catalogKind = null;
            filtered = new int[0];
            reportFailure(error);
        }
    }

    private void applySearch() {
        String query =
                searchBox == null ? "" : searchBox.getValue().strip().toLowerCase(Locale.ROOT);
        List<Integer> matches = new ArrayList<>();
        for (int index = 0; index < catalog.size(); index++) {
            if (query.isEmpty() || catalog.get(index).searchText().contains(query)) {
                matches.add(index);
            }
        }
        filtered = matches.stream().mapToInt(Integer::intValue).toArray();
        scrollRow = 0;
    }

    private void setPixelSize(int requested) {
        pixelSize =
                Math.clamp(requested, AtlasExporter.MIN_PIXEL_SIZE, AtlasExporter.MAX_PIXEL_SIZE);
        if (sizeBox != null && !sizeBox.getValue().equals(Integer.toString(pixelSize))) {
            sizeBox.setValue(Integer.toString(pixelSize));
        }
        configurePreview();
        updateControls();
    }

    private void onSizeTyped(String value) {
        if (value.matches("\\d{1,4}")) {
            int typed = Integer.parseInt(value);
            if (typed >= AtlasExporter.MIN_PIXEL_SIZE && typed <= AtlasExporter.MAX_PIXEL_SIZE) {
                pixelSize = typed;
                configurePreview();
            }
        }
        updateControls();
    }

    private boolean sizeIsValid() {
        return sizeBox == null || sizeBox.getValue().equals(Integer.toString(pixelSize));
    }

    private void export() {
        if (!sizeIsValid()) {
            status =
                    Component.translatable(
                            "textureatlasgenerator.status.invalid_size",
                            AtlasExporter.MIN_PIXEL_SIZE,
                            AtlasExporter.MAX_PIXEL_SIZE);
            statusIsError = true;
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (!AtlasExporter.start(client, selectedKind, pixelSize, this)) {
            status = Component.translatable("textureatlasgenerator.status.busy");
            statusIsError = true;
        }
        updateControls();
    }

    private void exportGlint() {
        if (!sizeIsValid()) {
            status =
                    Component.translatable(
                            "textureatlasgenerator.status.invalid_size",
                            AtlasExporter.MIN_PIXEL_SIZE,
                            AtlasExporter.MAX_PIXEL_SIZE);
            statusIsError = true;
            return;
        }
        if (!GlintExporter.start(Minecraft.getInstance(), pixelSize, this)) {
            status = Component.translatable("textureatlasgenerator.status.busy");
            statusIsError = true;
        }
        updateControls();
    }

    private void updateControls() {
        boolean exporting = AtlasExporter.isExporting();
        controlsExporting = exporting;
        if (atlasButton != null) {
            atlasButton.active = !exporting;
            decreaseSizeButton.active = !exporting && pixelSize > AtlasExporter.MIN_PIXEL_SIZE;
            increaseSizeButton.active = !exporting && pixelSize < AtlasExporter.MAX_PIXEL_SIZE;
            sizeBox.setEditable(!exporting);
            exportButton.active = !exporting && !catalog.isEmpty() && sizeIsValid();
            glintButton.active = !exporting && sizeIsValid();
            searchBox.setEditable(!exporting);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (controlsExporting != AtlasExporter.isExporting()) {
            updateControls();
        }
        preview.tick();
    }

    @Override
    public void removed() {
        super.removed();
        if (preview != null) {
            preview.close();
            preview = null;
        }
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        extractTransparentBackground(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        graphics.centeredText(font, title, width / 2, 14, 0xFFFFFFFF);
        graphics.text(
                font,
                Component.translatable("textureatlasgenerator.screen.pixel_unit"),
                sizeBox.getX() + sizeBox.getWidth() - 14,
                sizeBox.getY() + 6,
                0xFFA0A0A0);

        renderPreview(graphics, mouseX, mouseY);
        renderStatus(graphics);
    }

    private void renderPreview(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int left = 12;
        int right = width - 12;
        int top = PREVIEW_TOP;
        int bottom = Math.max(top, height - 54);
        int previewWidth = right - left;
        int columns = Math.max(1, previewWidth / PREVIEW_CELL);
        int visibleRows = Math.max(1, (bottom - top) / PREVIEW_CELL);
        int totalRows = Math.ceilDiv(filtered.length, columns);
        scrollRow = Math.clamp(scrollRow, 0, Math.max(0, totalRows - visibleRows));

        graphics.fill(left - 2, top - 2, right + 2, bottom + 2, 0x90000000);
        graphics.enableScissor(left, top, right, bottom);
        int firstIndex = scrollRow * columns;
        int visibleItems = visibleRows * columns;
        int lastIndex = Math.min(filtered.length, firstIndex + visibleItems);
        preview.show(
                Arrays.copyOfRange(filtered, Math.min(firstIndex, lastIndex), lastIndex),
                PREVIEW_ITEM_SIZE * Minecraft.getInstance().getWindow().getGuiScale());
        for (int index = firstIndex; index < lastIndex; index++) {
            int localIndex = index - firstIndex;
            int row = localIndex / columns;
            int column = localIndex % columns;
            int x = left + column * PREVIEW_CELL + (PREVIEW_CELL - PREVIEW_ITEM_SIZE) / 2;
            int y = top + row * PREVIEW_CELL + (PREVIEW_CELL - PREVIEW_ITEM_SIZE) / 2;
            AtlasCatalog.Entry entry = catalog.get(filtered[index]);
            preview.draw(graphics, filtered[index], x, y, PREVIEW_ITEM_SIZE);

            if (mouseX >= x
                    && mouseX < x + PREVIEW_ITEM_SIZE
                    && mouseY >= y
                    && mouseY < y + PREVIEW_ITEM_SIZE) {
                graphics.fill(
                        x - 1,
                        y - 1,
                        x + PREVIEW_ITEM_SIZE + 1,
                        y + PREVIEW_ITEM_SIZE + 1,
                        0x60FFFFFF);
                graphics.setTooltipForNextFrame(
                        font, Component.literal(entry.label()), mouseX, mouseY);
            }
        }
        graphics.disableScissor();

        Component count =
                filtered.length == catalog.size()
                        ? Component.translatable(
                                "textureatlasgenerator.screen.count", catalog.size())
                        : Component.translatable(
                                "textureatlasgenerator.screen.count_filtered",
                                filtered.length,
                                catalog.size());
        Component previewStatus = preview.status();
        graphics.text(
                font,
                previewStatus == null
                        ? count
                        : Component.translatable(
                                "textureatlasgenerator.screen.count_with_preview",
                                count,
                                previewStatus),
                left,
                bottom + 6,
                0xFFA0A0A0);
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
        int color = statusIsError ? 0xFFFF7070 : 0xFFB0B0B0;
        List<FormattedCharSequence> lines = font.split(status, width - 24);
        if (lines.size() > 2) {
            lines =
                    List.of(
                            lines.getFirst(),
                            Language.getInstance()
                                    .getVisualOrder(
                                            Component.translatable(
                                                    "textureatlasgenerator.status.truncated")));
        }
        for (int line = 0; line < lines.size(); line++) {
            graphics.centeredText(font, lines.get(line), width / 2, height - 36 + line * 10, color);
        }
    }

    @Override
    public boolean mouseScrolled(
            double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        if (mouseY >= PREVIEW_TOP && mouseY < height - 54 && verticalAmount != 0.0) {
            scrollRow = Math.max(0, scrollRow - (int) Math.signum(verticalAmount));
            return true;
        }
        return false;
    }

    @Override
    public void onProgress(AtlasExporter.Progress currentProgress) {
        progress = currentProgress;
        status =
                Component.translatable(
                        "textureatlasgenerator.status.progress",
                        currentProgress.message(),
                        currentProgress.completed(),
                        currentProgress.total());
        statusIsError = false;
        updateControls();
    }

    @Override
    public void onSuccess(AtlasExporter.Result result) {
        progress = null;
        status =
                result.images().size() > 1
                        ? Component.translatable(
                                "textureatlasgenerator.status.saved_pages",
                                result.summary(),
                                result.images().getFirst().getFileName().toString(),
                                result.images().size() - 1)
                        : Component.translatable(
                                "textureatlasgenerator.status.saved",
                                result.summary(),
                                result.images().getFirst().getFileName().toString());
        statusIsError = false;
        updateControls();
        announceSuccess(result);
    }

    @Override
    public void onFailure(Throwable error) {
        progress = null;
        reportFailure(error);
        updateControls();
        announceFailure(error);
    }

    private void reportFailure(Throwable error) {
        status = Component.translatable("textureatlasgenerator.export_failed", describe(error));
        statusIsError = true;
        TextureAtlasGenerator.LOGGER.error("Atlas generation failed", error);
    }

    public static String describe(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    public static void announceSuccess(AtlasExporter.Result result) {
        List<Component> links = new ArrayList<>();
        if (result.images().size() == 1) {
            links.add(openFileLink(result.images().getFirst()));
        } else {
            links.add(openFileLink(result.images().getFirst()));
            links.add(
                    openLink(
                            result.directory(),
                            Component.translatable(
                                    "textureatlasgenerator.chat.other_pages",
                                    result.images().size() - 1)));
        }
        result.mappings().forEach(path -> links.add(openFileLink(path)));
        MutableComponent message =
                Component.translatable("textureatlasgenerator.chat.prefix")
                        .append(
                                Component.translatable(
                                        "textureatlasgenerator.chat.saved", result.summary()));
        for (int index = 0; index < links.size(); index++) {
            if (index > 0) {
                message.append(
                        Component.translatable(
                                index == links.size() - 1
                                        ? "textureatlasgenerator.chat.list_last_separator"
                                        : "textureatlasgenerator.chat.list_separator"));
            }
            message.append(links.get(index));
        }
        Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(message);
    }

    public static void announceFailure(Throwable error) {
        Minecraft.getInstance()
                .gui
                .hud
                .getChat()
                .addClientSystemMessage(
                        Component.translatable("textureatlasgenerator.chat.prefix")
                                .append(
                                        Component.translatable(
                                                "textureatlasgenerator.export_failed",
                                                describe(error)))
                                .withStyle(style -> style.withColor(0xFF7070)));
    }

    private static Component openFileLink(Path path) {
        return openLink(path, Component.literal(path.getFileName().toString()));
    }

    private static Component openLink(Path path, MutableComponent text) {
        return text.withStyle(
                style -> style.withUnderlined(true).withClickEvent(new ClickEvent.OpenFile(path)));
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }
}
