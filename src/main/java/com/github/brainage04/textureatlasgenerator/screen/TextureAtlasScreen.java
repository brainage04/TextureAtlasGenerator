package com.github.brainage04.textureatlasgenerator.screen;

import com.github.brainage04.textureatlasgenerator.TextureAtlasGenerator;
import com.github.brainage04.textureatlasgenerator.screen.core.FloatSliderWidget;
import com.github.brainage04.textureatlasgenerator.util.ChatUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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

    public static NativeImage subtract(NativeImage imageA, NativeImage imageB) {
        int width  = imageA.getWidth();
        int height = imageA.getHeight();

        if (width != imageB.getWidth() || height != imageB.getHeight()) {
            throw new IllegalArgumentException("Images must be the same size");
        }

        NativeImage out = new NativeImage(width, height, true);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pxA = imageA.getColorArgb(x, y);
                int pxB = imageB.getColorArgb(x, y);

                int a = (pxA >> 24) & 0xFF;
                int r = clamp(((pxA >> 16) & 0xFF) - ((pxB >> 16) & 0xFF));
                int g = clamp(((pxA >> 8)  & 0xFF) - ((pxB >> 8)  & 0xFF));
                int b = clamp((pxA & 0xFF) - (pxB & 0xFF));

                out.setColorArgb(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }

        return out;
    }

    private static int clamp(int value) {
        return value < 0 ? 0 : Math.min(value, 255);
    }

    private static void renderTextureAtlas(DrawContext context) {
        int pixelsPerItem = Math.round((SIZE + PADDING) * scale);
        int width = rowsColumns * pixelsPerItem;
        int height = rowsColumns * pixelsPerItem;

        TextureAtlasGenerator.LOGGER.info("Row/column count: {}, Dimensions per item: {}x{}, Dimensions: {}x{}, Total pixels: {}", rowsColumns, pixelsPerItem, pixelsPerItem, width, height, width * height);

        AtomicReference<NativeImage> before = new AtomicReference<>();
        ScreenshotRecorder.takeScreenshot(MinecraftClient.getInstance().getFramebuffer(), before::set);

        int x = 0, y = 0;
        for (ItemStack stack : vanillaItems) {
            context.drawItem(stack, x * SIZE + PADDING, y * SIZE + PADDING);

            x++;
            if (x == rowsColumns) {
                x = 0;
                y++;
            }
        }

        ScreenshotRecorder.takeScreenshot(MinecraftClient.getInstance().getFramebuffer(), nativeImage -> {
            try (nativeImage) {
                NativeImage result = subtract(nativeImage, before.get());
                File output = new File("texture_atlas_vanilla.png");
                result.writeTo(output);
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

        context.drawItem(Items.CRAFTING_TABLE.getDefaultStack(), 0, 0);

        if (shouldExport) {
            renderTextureAtlas(context);
            shouldExport = false;
        }
    }

    @Override
    public void close() {
        // todo: suppress?
        client.setScreen(parent);
    }
}