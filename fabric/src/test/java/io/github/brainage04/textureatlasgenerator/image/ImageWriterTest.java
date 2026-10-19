package io.github.brainage04.textureatlasgenerator.image;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ImageWriterTest {
    @TempDir Path directory;

    @Test
    void streamedPngPreservesColourAlphaAndRowOrderAcrossBands() throws Exception {
        int width = 13;
        int height = 9;
        int[] pixels = pixels(width, height, 47);
        byte[] rgba = new byte[width * height * 4 + 7];
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int at = 7 + i * 4;
            rgba[at] = (byte) (pixel >>> 16);
            rgba[at + 1] = (byte) (pixel >>> 8);
            rgba[at + 2] = (byte) pixel;
            rgba[at + 3] = (byte) (pixel >>> 24);
        }
        Path path = directory.resolve("bands.png");
        try (PngStreamWriter writer = new PngStreamWriter(path, width, height)) {
            writer.writeRows(rgba, 7, 2);
            writer.writeRows(rgba, 7 + width * 8, height - 2);
        }
        assertPixels(path, pixels, width, height);
    }

    @Test
    void webpPreservesVariedImagesThroughIndependentDecoder() throws Exception {
        int[][] sizes = {{1, 1}, {3, 7}, {16, 16}, {33, 19}, {127, 65}};
        for (int[] size : sizes) {
            int width = size[0];
            int height = size[1];
            for (int kind = 0; kind < 4; kind++) {
                int[] expected = pixels(width, height, kind);
                if (kind == 0) java.util.Arrays.fill(expected, 0xFF42A080);
                if (kind == 1) {
                    for (int i = 0; i < expected.length; i++) expected[i] = (i % 3 == 0 ? 0x7F123456 : 0xFF00FF00);
                }
                if (kind == 2) {
                    for (int i = 0; i < expected.length; i++) expected[i] = 0xFF000000 | ((i % width * 7 & 255) << 16) | (i / width * 5 & 255);
                }
                Path path = directory.resolve("image-" + width + '-' + height + '-' + kind + ".webp");
                WebPWriter.writeStill(path, expected.clone(), width, height);
                assertPixels(path, expected, width, height);
            }
        }
    }

    @Test
    void incompletePngIsRejectedAndCanBeAbortedTwice() throws Exception {
        PngStreamWriter writer = new PngStreamWriter(directory.resolve("incomplete.png"), 2, 2);
        writer.writeRows(new byte[8], 0, 1);
        assertThrows(IllegalStateException.class, writer::close);
        assertDoesNotThrow(writer::abort);
        assertDoesNotThrow(writer::abort);
    }

    private static int[] pixels(int width, int height, long seed) {
        Random random = new Random(seed);
        int[] result = new int[width * height];
        for (int i = 0; i < result.length; i++) {
            // Keep alpha nonzero: RGB in a fully transparent WebP pixel is not significant.
            result[i] = random.nextInt() | 0x01000000;
        }
        return result;
    }

    private static void assertPixels(Path path, int[] expected, int width, int height) throws Exception {
        BufferedImage decoded = ImageIO.read(path.toFile());
        assertNotNull(decoded, "No decoder for " + path);
        assertEquals(width, decoded.getWidth());
        assertEquals(height, decoded.getHeight());
        assertArrayEquals(expected, decoded.getRGB(0, 0, width, height, null, 0, width), path.toString());
    }
}
