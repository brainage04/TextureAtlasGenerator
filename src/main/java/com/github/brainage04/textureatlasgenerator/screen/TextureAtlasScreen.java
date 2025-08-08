package com.github.brainage04.textureatlasgenerator.screen;

import com.github.brainage04.textureatlasgenerator.TextureAtlasGenerator;
import com.github.brainage04.textureatlasgenerator.screen.core.FloatSliderWidget;
import com.github.brainage04.textureatlasgenerator.util.ChatUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.List;

import static org.lwjgl.opengl.GL30.*;

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

    public static void saveTextureAtlas(int[] pixels, int startX, int startY, int endX, int endY, String fileName) {
        int width = endX - startX;
        int height = endY - startY;

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = pixels[(endY - y - 1) * endX + x]; // flip vertically
                int alpha = (pixel >> 24) & 0xFF;
                int blue = (pixel >> 16) & 0xFF;
                int green = (pixel >> 8) & 0xFF;
                int red = pixel & 0xFF;

                // combine channels into ARGB format
                image.setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
            }
        }

        File output = new File(String.format("%s.png", fileName));
        try {
            ImageIO.write(image, "PNG", output);
            ChatUtils.addAtlasComponent(output, "texture atlas");
        } catch (IOException e) {
            TextureAtlasGenerator.LOGGER.error("Failed to save atlas: {}", e.getMessage());
        }
    }

    private static void exportTextureAtlas() {
        String atlasName = "texture_atlas_vanilla";

        int pixelsPerItem = Math.round((SIZE + PADDING) * scale);
        int width = rowsColumns * pixelsPerItem;
        int height = rowsColumns * pixelsPerItem;

        TextureAtlasGenerator.LOGGER.info("Row/column count: {}, Dimensions per item: {}x{}, Dimensions: {}x{}, Total pixels: {}", rowsColumns, pixelsPerItem, pixelsPerItem, width, height, width * height);

        // generate and bind framebuffer
        int framebuffer = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);

        // generate and bind texture
        int textureColorBuffer = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureColorBuffer);

        // allocate space for texture
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);

        // nearest neighbour filtering to avoid blur (might change later)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        // attach texture to framebuffer
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, textureColorBuffer, 0);

        // create renderbuffer
        int rbo = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, rbo);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, width, height);
        glBindRenderbuffer(GL_RENDERBUFFER, 0); // unbind renderbuffer once memory is allocated

        // attach renderbuffer to depth and stencil attachment of framebuffer
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, rbo);

        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            TextureAtlasGenerator.LOGGER.error("Framebuffer is not complete!");
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        DrawContext drawContext = new DrawContext(client, new GuiRenderState());

        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        glClearColor(0, 0, 0, 0);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        int x = 0, y = 0;
        for (ItemStack stack : vanillaItems) {
            drawContext.drawItem(stack, x * SIZE + PADDING, y * SIZE + PADDING);

            x++;
            if (x == rowsColumns) {
                x = 0;
                y++;
            }
        }

        IntBuffer intBuffer = ByteBuffer.allocateDirect(width * height * 4)
                .order(ByteOrder.nativeOrder())
                .asIntBuffer();
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, intBuffer);

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glDeleteFramebuffers(framebuffer);

        int[] pixels = new int[width * height];
        intBuffer.get(pixels);

        saveTextureAtlas(pixels, 0, 0, width, height, atlasName);
    }

    private static void renderItems(DrawContext context) {
        context.getMatrices().pushMatrix();
        context.getMatrices().scale(scale);

        int x = 0, y = 0;
        for (ItemStack stack : vanillaItems) {
            context.drawItem(stack, x * SIZE + PADDING, y * SIZE + PADDING);

            x++;
            if (x == rowsColumns) {
                x = 0;
                y++;
            }
        }

        context.getMatrices().popMatrix();
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
            exportTextureAtlas();
            shouldExport = false;
        }

        renderItems(context);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}