package io.github.brainage04.textureatlasgenerator.atlas;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.WindowRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.world.item.ItemStack;

import java.nio.ByteBuffer;
import java.util.List;

/** Renders a grid of item stacks off-screen and reads the pixels back. */
final class AtlasItemRenderer {
    private final Minecraft client;

    AtlasItemRenderer(Minecraft client) {
        this.client = client;
    }

    /**
     * The cell size to draw on the GPU for {@code pixelSize}, a multiple of it. Below 16 px the GUI
     * renderer would point-sample a single texel per output pixel, so thin items vanish; they are
     * drawn at a multiple of 16 instead and area-averaged down with {@link #downsample}.
     */
    static int renderSize(int pixelSize) {
        if (pixelSize >= 16) {
            return pixelSize;
        }
        int a = 16;
        int b = pixelSize;
        while (b != 0) {
            int t = a % b;
            a = b;
            b = t;
        }
        return 16 / a * pixelSize;
    }

    /**
     * Copies a bottom-up RGBA page into a top-down buffer, averaging each {@code factor × factor}
     * block (weighted by alpha) when the page was drawn larger than its cells.
     *
     * @param destination receives {@code width / factor × height / factor} pixels
     * @param destinationRowBytes bytes per row of {@code destination}
     * @param destinationOffset byte offset of the page's top-left pixel in {@code destination}
     */
    static void downsample(
            ByteBuffer pixels, int width, int height, int factor, byte[] destination, int destinationRowBytes, int destinationOffset) {
        int pageRowBytes = width * 4;
        if (factor == 1) {
            for (int y = 0; y < height; y++) {
                pixels.get((height - 1 - y) * pageRowBytes, destination, destinationOffset + y * destinationRowBytes, pageRowBytes);
            }
            return;
        }
        int samples = factor * factor;
        for (int outY = 0; outY < height / factor; outY++) {
            for (int outX = 0; outX < width / factor; outX++) {
                long red = 0;
                long green = 0;
                long blue = 0;
                long alpha = 0;
                for (int dy = 0; dy < factor; dy++) {
                    int rowStart = (height - 1 - (outY * factor + dy)) * pageRowBytes + outX * factor * 4;
                    for (int dx = 0; dx < factor; dx++) {
                        int at = rowStart + dx * 4;
                        int a = pixels.get(at + 3) & 0xFF;
                        red += (long) (pixels.get(at) & 0xFF) * a;
                        green += (long) (pixels.get(at + 1) & 0xFF) * a;
                        blue += (long) (pixels.get(at + 2) & 0xFF) * a;
                        alpha += a;
                    }
                }
                int target = destinationOffset + outY * destinationRowBytes + outX * 4;
                if (alpha == 0) {
                    destination[target] = 0;
                    destination[target + 1] = 0;
                    destination[target + 2] = 0;
                    destination[target + 3] = 0;
                } else {
                    destination[target] = (byte) ((red + alpha / 2) / alpha);
                    destination[target + 1] = (byte) ((green + alpha / 2) / alpha);
                    destination[target + 2] = (byte) ((blue + alpha / 2) / alpha);
                    destination[target + 3] = (byte) ((alpha + samples / 2) / samples);
                }
            }
        }
    }

    /** Receives a rendered page; the buffer is only valid during the call. */
    interface PageConsumer {
        /**
         * @param rgba tightly packed RGBA8 pixels, bottom row first
         * @param error the failure, in which case {@code rgba} is {@code null}
         */
        void accept(ByteBuffer rgba, int width, int height, Throwable error);
    }

    /**
     * Renders {@code stacks} (row-major, {@code null} for an empty cell) into a page of
     * {@code columns} x {@code rows} cells of {@code pixelSize} pixels. Must run on the render thread.
     */
    void renderPage(
            List<ItemStack> stacks,
            int columns,
            int rows,
            int pixelSize,
            PageConsumer consumer) {
        int width = columns * pixelSize;
        int height = rows * pixelSize;
        MainTarget target = null;
        try {
            target = new MainTarget(width, height);
            clear(target);
            renderItems(target, stacks, columns, pixelSize);
            capture(target, consumer);
        } catch (Throwable error) {
            if (target != null) {
                target.destroyBuffers();
            }
            consumer.accept(null, width, height, error);
        }
    }

    private void renderItems(
            RenderTarget target,
            List<ItemStack> stacks,
            int columns,
            int pixelSize) {
        GuiRenderState renderState = new GuiRenderState();
        GuiRenderer renderer = new GuiRenderer(renderState, client.gameRenderer.featureRenderDispatcher(), List.of());
        int renderScale = Math.max(1, Math.ceilDiv(pixelSize, 16));
        // The extractor's initial scissor comes from these methods, not WindowRenderState.
        GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(client, renderState, 0, 0) {
            @Override
            public int guiWidth() {
                return Math.ceilDiv(target.width, renderScale);
            }

            @Override
            public int guiHeight() {
                return Math.ceilDiv(target.height, renderScale);
            }
        };
        float itemScale = pixelSize / (16.0F * renderScale);
        graphics.pose().pushMatrix();
        graphics.pose().scale(itemScale, itemScale);
        for (int index = 0; index < stacks.size(); index++) {
            ItemStack stack = stacks.get(index);
            if (stack != null) {
                graphics.fakeItem(stack, index % columns * 16, index / columns * 16);
            }
        }
        graphics.pose().popMatrix();

        WindowRenderState window = client.gameRenderer.gameRenderState().windowRenderState;
        RenderTarget previousTarget = client.gameRenderer.mainRenderTarget;
        int previousWidth = window.width;
        int previousHeight = window.height;
        int previousGuiScale = window.guiScale;
        try (AtlasRenderContext context = new AtlasRenderContext(client)) {
            client.gameRenderer.mainRenderTarget = target;
            window.width = target.width;
            window.height = target.height;
            window.guiScale = renderScale;
            context.apply(target.width, target.height);
            renderer.render();
        } finally {
            client.gameRenderer.mainRenderTarget = previousTarget;
            window.width = previousWidth;
            window.height = previousHeight;
            window.guiScale = previousGuiScale;
            renderer.close();
        }
    }

    private static void clear(RenderTarget target) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.clearColorAndDepthTextures(
                target.getColorTexture(),
                GuiRenderer.CLEAR_COLOR,
                target.getDepthTexture(),
                1.0
        );
    }

    private void capture(RenderTarget target, PageConsumer consumer) {
        GpuBuffer buffer = RenderSystem.getDevice().createBuffer(
                () -> "Texture Atlas Generator readback",
                GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ,
                (long) target.width * target.height * 4
        );

        try {
            RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(
                    target.getColorTexture(),
                    buffer,
                    0,
                    () -> finishCapture(target, buffer, consumer),
                    0
            );
        } catch (Throwable error) {
            buffer.close();
            target.destroyBuffers();
            consumer.accept(null, target.width, target.height, error);
        }
    }

    private static void finishCapture(RenderTarget target, GpuBuffer buffer, PageConsumer consumer) {
        int width = target.width;
        int height = target.height;
        boolean delivered = false;
        Throwable failure = null;
        try (GpuBufferSlice.MappedView mapped = buffer.map(true, false)) {
            delivered = true;
            consumer.accept(mapped.data(), width, height, null);
        } catch (Throwable error) {
            failure = error;
        } finally {
            buffer.close();
            target.destroyBuffers();
        }
        if (failure != null && !delivered) {
            consumer.accept(null, width, height, failure);
        }
    }
}
