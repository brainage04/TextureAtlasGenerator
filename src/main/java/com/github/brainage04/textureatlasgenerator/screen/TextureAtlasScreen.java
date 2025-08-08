package com.github.brainage04.textureatlasgenerator.screen;

import com.github.brainage04.textureatlasgenerator.TextureAtlasGenerator;
import com.github.brainage04.textureatlasgenerator.screen.core.FloatSliderWidget;
import com.github.brainage04.textureatlasgenerator.util.ChatUtils;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class TextureAtlasScreen extends Screen {
    private static List<ItemStack> vanillaItems;
    private static int rowsColumns;
    private static final int SIZE = 16;
    private static float scale = 0.5f;
    private static final int PADDING = 2;

    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;

    private final Screen parent;
    private SliderWidget scaleSlider;
    private ButtonWidget exportButton;
    private boolean shouldExport = false;

    public TextureAtlasScreen(Screen parent) {
        super(Text.literal("Texture Atlas Screen"));
        this.parent = parent;
    }

    private static void exportTextureAtlas(DrawContext context) {
        String atlasName = "texture_atlas_vanilla";

        int pixelsPerItem = Math.round((SIZE + PADDING) * scale);
        int width = rowsColumns * pixelsPerItem;
        int height = rowsColumns * pixelsPerItem;

        TextureAtlasGenerator.LOGGER.info("Row/column count: {}, Dimensions per item: {}x{}, Dimensions: {}x{}, Total pixels: {}", rowsColumns, pixelsPerItem, pixelsPerItem, width, height, width * height);

        SimpleFramebuffer framebuffer = new SimpleFramebuffer(atlasName, width, height, true);
        // todo: set framebuffer background to transparent (0,0,0,0 in RGBA format)
        // todo: start writing to framebuffer

        int x = 0, y = 0;
        for (ItemStack stack : vanillaItems) {
            context.drawItem(stack, x * SIZE + PADDING, y * SIZE + PADDING);

            x++;
            if (x == rowsColumns) {
                x = 0;
                y++;
            }
        }

        // todo: stop writing to framebuffer

        ScreenshotRecorder.takeScreenshot(framebuffer, nativeImage -> {
            try (nativeImage) {
                File output = new File(String.format("%s.png", atlasName));
                nativeImage.writeTo(output);
                ChatUtils.addAtlasComponent(output, "texture atlas");
            } catch (IOException e) {
                TextureAtlasGenerator.LOGGER.error(
                        "Failed to save atlas: {}",
                        e.getMessage()
                );
            }
        });
    }

    @Override
    protected void init() {
        vanillaItems = Registries.ITEM.stream().map(Item::getDefaultStack).toList();
        rowsColumns = (int) Math.floor(Math.sqrt(vanillaItems.size()));

        scaleSlider = new FloatSliderWidget(
                width - BUTTON_WIDTH, height - BUTTON_HEIGHT,
                BUTTON_WIDTH, BUTTON_HEIGHT,
                "Scale",
                0.1f, 10, scale, 0.1f,
                val -> scale = val
        );
        exportButton = ButtonWidget.builder(
                Text.literal("Export"),
                        button -> shouldExport = true
                )
                .dimensions(
                        width - BUTTON_WIDTH, height - BUTTON_HEIGHT * 2,
                        BUTTON_WIDTH, BUTTON_HEIGHT
                )
                .build();

        addDrawableChild(scaleSlider);
        addDrawableChild(exportButton);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        super.render(context, mouseX, mouseY, deltaTicks);

        if (shouldExport) {
            exportTextureAtlas(context);
            shouldExport = false;
        }
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}