package io.github.brainage04.textureatlasgenerator.atlas;

import io.github.brainage04.textureatlasgenerator.image.PngStreamWriter;
import io.github.brainage04.textureatlasgenerator.image.WebPWriter;

import java.io.IOException;
import java.nio.file.Path;

/** Receives an atlas image band by band and writes it in the chosen format. */
abstract sealed class AtlasImage permits AtlasImage.Png, AtlasImage.WebP {
    /** Output file format of an atlas image. */
    enum Format {
        WEBP("webp"),
        PNG("png");

        private final String extension;

        Format(String extension) {
            this.extension = extension;
        }

        String extension() {
            return extension;
        }
    }

    /**
     * Lossless WebP when the heap can hold the image (WebP needs the whole image; PNG is streamed);
     * otherwise PNG. Pages never exceed WebP's dimension limit.
     *
     * @return the format and, when PNG was forced, why
     */
    static Choice choose(int side) {
        if (side > WebPWriter.MAX_DIMENSION) {
            throw new IllegalArgumentException(side + " px exceeds WebP's " + WebPWriter.MAX_DIMENSION + " px limit");
        }
        Runtime runtime = Runtime.getRuntime();
        long available = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory());
        long needed = (long) side * side * 4 * 5 / 4;
        if (needed > available) {
            return new Choice(Format.PNG, "a " + side + " px WebP needs about " + (needed >> 20)
                    + " MiB of free memory; " + (available >> 20) + " MiB is available");
        }
        return new Choice(Format.WEBP, null);
    }

    record Choice(Format format, String fallbackReason) {}

    static AtlasImage open(Format format, Path path, int width, int height) throws IOException {
        return format == Format.WEBP ? new WebP(path, width, height) : new Png(path, width, height);
    }

    /** Appends {@code rows} RGBA scanlines, top to bottom. */
    abstract void writeRows(byte[] rgba, int offset, int rows) throws IOException;

    /** Completes and closes the file. */
    abstract void finish() throws IOException;

    /** Closes the file without completing it. */
    abstract void abort();

    static final class Png extends AtlasImage {
        private final PngStreamWriter writer;

        Png(Path path, int width, int height) throws IOException {
            writer = new PngStreamWriter(path, width, height);
        }

        @Override
        void writeRows(byte[] rgba, int offset, int rows) throws IOException {
            writer.writeRows(rgba, offset, rows);
        }

        @Override
        void finish() throws IOException {
            writer.close();
        }

        @Override
        void abort() {
            writer.abort();
        }
    }

    static final class WebP extends AtlasImage {
        private final Path path;
        private final int width;
        private final int height;
        private int[] argb;
        private int rowsWritten;

        WebP(Path path, int width, int height) {
            this.path = path;
            this.width = width;
            this.height = height;
            this.argb = new int[Math.multiplyExact(width, height)];
        }

        @Override
        void writeRows(byte[] rgba, int offset, int rows) {
            int target = rowsWritten * width;
            int source = offset;
            for (int pixel = 0; pixel < rows * width; pixel++) {
                argb[target + pixel] = (rgba[source + 3] & 0xFF) << 24 | (rgba[source] & 0xFF) << 16
                        | (rgba[source + 1] & 0xFF) << 8 | (rgba[source + 2] & 0xFF);
                source += 4;
            }
            rowsWritten += rows;
        }

        @Override
        void finish() throws IOException {
            if (rowsWritten != height) {
                throw new IllegalStateException("WebP finished after " + rowsWritten + " of " + height + " rows");
            }
            int[] pixels = argb;
            argb = null;
            WebPWriter.writeStill(path, pixels, width, height);
        }

        @Override
        void abort() {
            argb = null;
        }
    }
}
