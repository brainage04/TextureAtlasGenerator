package io.github.brainage04.textureatlasgenerator.atlas;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.WindowRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.List;
import java.util.function.BiConsumer;

final class AtlasItemRenderer {
    private final Minecraft client;

    AtlasItemRenderer(Minecraft client) {
        this.client = client;
    }

    void renderPage(
            List<AtlasCatalog.Entry> entries,
            int fromIndex,
            int itemCount,
            int pixelSize,
            int columns,
            int targetWidth,
            int targetHeight,
            BiConsumer<NativeImage, Throwable> completion
    ) {
        client.execute(() -> renderPageOnClient(
                entries,
                fromIndex,
                itemCount,
                pixelSize,
                columns,
                targetWidth,
                targetHeight,
                completion
        ));
    }

    private void renderPageOnClient(
            List<AtlasCatalog.Entry> entries,
            int fromIndex,
            int itemCount,
            int pixelSize,
            int columns,
            int targetWidth,
            int targetHeight,
            BiConsumer<NativeImage, Throwable> completion
    ) {
        MainTarget target = null;
        try {
            target = new MainTarget(targetWidth, targetHeight);
            clear(target);
            renderItems(target, entries, fromIndex, itemCount, pixelSize, columns);
            capture(target, completion);
        } catch (Throwable error) {
            if (target != null) {
                target.destroyBuffers();
            }
            completion.accept(null, error);
        }
    }

    private void renderItems(
            RenderTarget target,
            List<AtlasCatalog.Entry> entries,
            int fromIndex,
            int itemCount,
            int pixelSize,
            int columns
    ) {
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
        for (int offset = 0; offset < itemCount; offset++) {
            int row = offset / columns;
            int column = offset % columns;
            graphics.fakeItem(entries.get(fromIndex + offset).stack(), column * 16, row * 16);
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

    private void capture(RenderTarget target, BiConsumer<NativeImage, Throwable> completion) {
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
                    () -> finishCapture(target, buffer, completion),
                    0
            );
        } catch (Throwable error) {
            buffer.close();
            target.destroyBuffers();
            completion.accept(null, error);
        }
    }

    private void finishCapture(
            RenderTarget target,
            GpuBuffer buffer,
            BiConsumer<NativeImage, Throwable> completion
    ) {
        NativeImage image = null;
        Throwable failure = null;
        try (GpuBufferSlice.MappedView mapped = buffer.map(true, false)) {
            image = new NativeImage(target.width, target.height, false);
            ByteBuffer bytes = mapped.data().order(ByteOrder.nativeOrder());
            IntBuffer pixels = bytes.asIntBuffer();
            for (int y = 0; y < target.height; y++) {
                for (int x = 0; x < target.width; x++) {
                    image.setPixelABGR(x, target.height - 1 - y, pixels.get(x + y * target.width));
                }
            }
        } catch (Throwable error) {
            failure = error;
            if (image != null) {
                image.close();
                image = null;
            }
        } finally {
            buffer.close();
            target.destroyBuffers();
        }

        completion.accept(image, failure);
    }
}
