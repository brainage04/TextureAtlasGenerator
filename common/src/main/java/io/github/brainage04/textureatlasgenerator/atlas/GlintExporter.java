package io.github.brainage04.textureatlasgenerator.atlas;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.brainage04.textureatlasgenerator.image.WebPWriter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Exports one atlas cell of the item enchantment glint as a looping animated WebP on a
 * transparent background, for drawing over website icons.
 *
 * <p>The glint is computed exactly as 26.2 draws it on an item in the inventory, rather than
 * captured from the screen: the {@code enchanted_glint_item} texture (bilinear, repeating) is
 * sampled through {@code TextureTransform.GLINT_TEXTURING} at the enchanted book sprite's
 * item-atlas UVs, scaled by the default glint strength and blended with {@code BlendFunction.GLINT}
 * ({@code SRC_COLOR, ONE}), which adds {@code (strength × texel)²} to the icon. The scroll repeats
 * every 330,000 {@code TextureTransform} units, which at the default glint speed is 82.5 s.
 *
 * <p>Each pixel stores that addition {@code C} as colour {@code C / max(C)} with alpha
 * {@code max(C)}, so {@code mix-blend-mode: plus-lighter} reproduces the in-game result exactly
 * and ordinary alpha blending gives a close approximation.
 */
public final class GlintExporter {
    public static final int DEFAULT_PIXEL_SIZE = 64;
    public static final int FRAME_MILLIS = 50;
    /** One full loop of both glint layers at the default glint speed: 82.5 s. */
    public static final int FRAMES = 1650;
    public static final String FILE_STEM = "enchantment_glint";

    private static final double GLINT_SPEED = 0.5;
    private static final double GLINT_STRENGTH = 0.75;
    private static final double ROTATION = Math.PI / 18;
    private static final double SCALE = 8.0;
    private static final Identifier REFERENCE_SPRITE = Identifier.withDefaultNamespace("item/enchanted_book");

    private final int pixelSize;
    private final float[] glint;
    private final int glintWidth;
    private final int glintHeight;
    private final float u0;
    private final float v0;
    private final float u1;
    private final float v1;

    private GlintExporter(int pixelSize, float[] glint, int glintWidth, int glintHeight, TextureAtlasSprite sprite) {
        this.pixelSize = pixelSize;
        this.glint = glint;
        this.glintWidth = glintWidth;
        this.glintHeight = glintHeight;
        this.u0 = sprite.getU0();
        this.v0 = sprite.getV0();
        this.u1 = sprite.getU1();
        this.v1 = sprite.getV1();
    }

    /**
     * Starts the export on the render thread. Returns {@code false} if another export is running.
     *
     * @throws IllegalArgumentException if {@code pixelSize} is out of range
     */
    public static boolean start(Minecraft client, int pixelSize, AtlasExporter.Listener listener) {
        if (pixelSize < AtlasExporter.MIN_PIXEL_SIZE || pixelSize > AtlasExporter.MAX_PIXEL_SIZE) {
            throw new IllegalArgumentException("Pixel size must be between "
                    + AtlasExporter.MIN_PIXEL_SIZE + " and " + AtlasExporter.MAX_PIXEL_SIZE);
        }
        if (!AtlasExporter.claim()) {
            return false;
        }
        GlintExporter exporter;
        try {
            TextureAtlasSprite sprite = client.getAtlasManager().getAtlasOrThrow(AtlasIds.ITEMS).getSprite(REFERENCE_SPRITE);
            Optional<Resource> resource = client.getResourceManager().getResource(ItemFeatureRenderer.ENCHANTED_GLINT_ITEM);
            if (resource.isEmpty()) {
                throw new IllegalStateException("Missing " + ItemFeatureRenderer.ENCHANTED_GLINT_ITEM);
            }
            try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
                int width = image.getWidth();
                int height = image.getHeight();
                float[] texels = new float[width * height * 3];
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int argb = image.getPixel(x, y);
                        int at = (y * width + x) * 3;
                        texels[at] = ((argb >>> 16) & 0xFF) / 255.0F;
                        texels[at + 1] = ((argb >>> 8) & 0xFF) / 255.0F;
                        texels[at + 2] = (argb & 0xFF) / 255.0F;
                    }
                }
                exporter = new GlintExporter(pixelSize, texels, width, height, sprite);
            }
        } catch (Throwable error) {
            AtlasExporter.release();
            listener.onFailure(error);
            return true;
        }

        Path directory = client.gameDirectory.toPath().resolve("texture-atlases");
        String fileBase = FILE_STEM + '_' + pixelSize + 'x' + pixelSize;
        listener.onProgress(new AtlasExporter.Progress(AtlasExporter.Stage.RENDERING, 0, FRAMES, "Rendering glint frames…"));
        Util.backgroundExecutor().execute(() -> {
            Path temporary = directory.resolve(fileBase + ".tmp.webp");
            try {
                AtlasExporter.Result result = exporter.write(directory, fileBase, temporary, completed ->
                        client.schedule(() -> listener.onProgress(new AtlasExporter.Progress(
                                AtlasExporter.Stage.RENDERING, completed, FRAMES, "Rendering glint frames…"))));
                client.schedule(() -> {
                    AtlasExporter.release();
                    listener.onSuccess(result);
                });
            } catch (Throwable error) {
                AtlasExporter.deleteQuietly(temporary);
                client.schedule(() -> {
                    AtlasExporter.release();
                    listener.onFailure(error);
                });
            }
        });
        return true;
    }

    private interface FrameProgress {
        void completed(int frames);
    }

    private AtlasExporter.Result write(Path directory, String fileBase, Path temporary, FrameProgress progress) throws IOException {
        Files.createDirectories(directory);
        int[] frame = new int[pixelSize * pixelSize];
        try (WebPWriter.Animation animation = new WebPWriter.Animation(temporary, pixelSize, pixelSize, 0)) {
            for (int index = 0; index < FRAMES; index++) {
                renderFrame((long) index * FRAME_MILLIS, frame);
                animation.addFrame(frame, FRAME_MILLIS);
                if ((index + 1) % 50 == 0 || index + 1 == FRAMES) {
                    progress.completed(index + 1);
                }
            }
        }

        String imageName = fileBase + ".webp";
        Path image = directory.resolve(imageName);
        Path json = directory.resolve(fileBase + ".json");
        Path css = directory.resolve(fileBase + ".css");
        Path temporaryJson = AtlasExporter.writeTemporary(json, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping()
                .create().toJson(description(imageName)) + '\n');
        Path temporaryCss = AtlasExporter.writeTemporary(css, css(imageName));
        try {
            AtlasExporter.replace(temporary, image);
            AtlasExporter.replace(temporaryJson, json);
            AtlasExporter.replace(temporaryCss, css);
        } catch (IOException error) {
            AtlasExporter.deleteQuietly(temporaryJson);
            AtlasExporter.deleteQuietly(temporaryCss);
            throw error;
        }
        return new AtlasExporter.Result(
                "the " + (FRAMES * FRAME_MILLIS / 1000.0) + " s enchantment glint loop (" + FRAMES + " frames)",
                List.of(image),
                List.of(json, css),
                directory);
    }

    /** Fills {@code argb} with the glint added to an item {@code millis} into the loop. */
    private void renderFrame(long millis, int[] argb) {
        long units = (long) (millis * GLINT_SPEED * 8.0);
        float offsetU = (float) (units % 110000L) / 110000.0F;
        float offsetV = (float) (units % 30000L) / 30000.0F;
        double cos = Math.cos(ROTATION);
        double sin = Math.sin(ROTATION);
        // Below 16 px average several samples per pixel, as the atlas exporter supersamples items.
        int samples = Math.max(1, Math.ceilDiv(32, pixelSize));
        double[] sum = new double[3];
        float[] texel = new float[3];
        for (int y = 0; y < pixelSize; y++) {
            for (int x = 0; x < pixelSize; x++) {
                sum[0] = 0;
                sum[1] = 0;
                sum[2] = 0;
                for (int sy = 0; sy < samples; sy++) {
                    double v = v0 + (y + (sy + 0.5) / samples) / pixelSize * (v1 - v0);
                    for (int sx = 0; sx < samples; sx++) {
                        double u = u0 + (x + (sx + 0.5) / samples) / pixelSize * (u1 - u0);
                        double scaledU = u * SCALE;
                        double scaledV = v * SCALE;
                        double s = cos * scaledU - sin * scaledV - offsetU;
                        double t = sin * scaledU + cos * scaledV + offsetV;
                        sample(s, t, texel);
                        for (int channel = 0; channel < 3; channel++) {
                            double source = texel[channel] * GLINT_STRENGTH;
                            sum[channel] += source * source;
                        }
                    }
                }
                int red = toByte(sum[0] / (samples * samples));
                int green = toByte(sum[1] / (samples * samples));
                int blue = toByte(sum[2] / (samples * samples));
                int alpha = Math.max(red, Math.max(green, blue));
                argb[y * pixelSize + x] = alpha == 0 ? 0
                        : alpha << 24 | unpremultiply(red, alpha) << 16 | unpremultiply(green, alpha) << 8 | unpremultiply(blue, alpha);
            }
        }
    }

    /** Bilinear, repeating texture lookup at normalised coordinates, as the GPU sampler does. */
    private void sample(double s, double t, float[] out) {
        double x = s * glintWidth - 0.5;
        double y = t * glintHeight - 0.5;
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        float fx = (float) (x - x0);
        float fy = (float) (y - y0);
        int left = Math.floorMod(x0, glintWidth);
        int right = Math.floorMod(x0 + 1, glintWidth);
        int top = Math.floorMod(y0, glintHeight);
        int bottom = Math.floorMod(y0 + 1, glintHeight);
        for (int channel = 0; channel < 3; channel++) {
            float a = glint[(top * glintWidth + left) * 3 + channel];
            float b = glint[(top * glintWidth + right) * 3 + channel];
            float c = glint[(bottom * glintWidth + left) * 3 + channel];
            float d = glint[(bottom * glintWidth + right) * 3 + channel];
            out[channel] = (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy;
        }
    }

    private static int toByte(double value) {
        return (int) Math.clamp(Math.round(value * 255.0), 0, 255);
    }

    private static int unpremultiply(int value, int alpha) {
        return Math.min(255, (value * 255 + alpha / 2) / alpha);
    }

    private JsonObject description(String imageName) {
        JsonObject json = new JsonObject();
        json.addProperty("image", imageName);
        json.addProperty("width", pixelSize);
        json.addProperty("height", pixelSize);
        json.addProperty("frames", FRAMES);
        json.addProperty("frameMilliseconds", FRAME_MILLIS);
        json.addProperty("loopSeconds", FRAMES * FRAME_MILLIS / 1000.0);
        json.addProperty("blend", "plus-lighter");
        json.addProperty("usage", "Draw over an icon with mix-blend-mode: plus-lighter, masked by the icon's alpha "
                + "(see the .css file). Each pixel's colour times its alpha is what Minecraft adds to the icon.");
        JsonObject source = new JsonObject();
        source.addProperty("minecraft", "26.2");
        source.addProperty("texture", ItemFeatureRenderer.ENCHANTED_GLINT_ITEM.toString());
        source.addProperty("sprite", REFERENCE_SPRITE.toString());
        JsonArray uv = new JsonArray();
        uv.add(u0);
        uv.add(v0);
        uv.add(u1);
        uv.add(v1);
        source.add("spriteUv", uv);
        source.addProperty("glintSpeed", GLINT_SPEED);
        source.addProperty("glintStrength", GLINT_STRENGTH);
        json.add("source", source);
        return json;
    }

    private String css(String imageName) {
        return "/* " + imageName + ": the " + (FRAMES * FRAME_MILLIS / 1000.0) + " s Minecraft enchantment glint loop, "
                + pixelSize + "x" + pixelSize + " px. Generated by Texture Atlas Generator.\n"
                + "   Add the tag-glint class to an atlas icon, e.g.\n"
                + "   <span class=\"vanilla_items vanilla_items-minecraft-diamond_sword tag-glint\"></span>.\n"
                + "   The glint is confined to the icon through the atlas stylesheet's --tag-mask. */\n"
                + ".tag-glint {\n"
                + "  position: relative;\n"
                + "  isolation: isolate;\n"
                + "}\n"
                + ".tag-glint::after {\n"
                + "  content: \"\";\n"
                + "  position: absolute;\n"
                + "  inset: 0;\n"
                + "  pointer-events: none;\n"
                + "  background: url(\"" + imageName + "\") 0 0 / 100% 100% no-repeat;\n"
                + "  mix-blend-mode: plus-lighter;\n"
                + "  -webkit-mask: var(--tag-mask);\n"
                + "  mask: var(--tag-mask);\n"
                + "}\n";
    }
}
