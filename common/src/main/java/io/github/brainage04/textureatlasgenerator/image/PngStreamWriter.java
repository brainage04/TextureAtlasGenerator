package io.github.brainage04.textureatlasgenerator.image;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Writes an 8-bit RGBA PNG one band of scanlines at a time, so images far larger than memory (or
 * than WebP's 16383 px limit) can be encoded.
 */
public final class PngStreamWriter implements AutoCloseable {
    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final int CHUNK_DATA_LIMIT = 1 << 20;

    private final OutputStream out;
    private final int height;
    private final int rowBytes;
    private final Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
    private final CRC32 crc = new CRC32();
    private final byte[] chunkData = new byte[CHUNK_DATA_LIMIT];
    private final byte[] deflateBuffer = new byte[64 * 1024];
    private final byte[][] filtered = new byte[5][];
    private final byte[] previousRow;
    private int chunkLength;
    private int rowsWritten;
    private boolean closed;

    public PngStreamWriter(Path path, int width, int height) throws IOException {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid PNG size " + width + 'x' + height);
        }
        this.out = new BufferedOutputStream(Files.newOutputStream(path), 1 << 16);
        this.height = height;
        this.rowBytes = Math.multiplyExact(width, 4);
        for (int filter = 0; filter < filtered.length; filter++) {
            filtered[filter] = new byte[rowBytes + 1];
            filtered[filter][0] = (byte) filter;
        }
        this.previousRow = new byte[rowBytes];

        out.write(SIGNATURE);
        byte[] header = new byte[13];
        putInt(header, 0, width);
        putInt(header, 4, height);
        header[8] = 8; // bit depth
        header[9] = 6; // RGBA
        writeChunk("IHDR", header, header.length);
    }

    /** Appends {@code rows} RGBA scanlines, top to bottom, starting at {@code offset} in {@code rgba}. */
    public void writeRows(byte[] rgba, int offset, int rows) throws IOException {
        if (rowsWritten + rows > height) {
            throw new IllegalStateException("Too many PNG rows");
        }
        for (int row = 0; row < rows; row++) {
            int start = offset + row * rowBytes;
            deflate(filterRow(rgba, start), false);
            System.arraycopy(rgba, start, previousRow, 0, rowBytes);
        }
        rowsWritten += rows;
        if (rowsWritten == height) {
            deflate(null, true);
            flushChunk();
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        try {
            if (rowsWritten != height) {
                throw new IllegalStateException("PNG closed after " + rowsWritten + " of " + height + " rows");
            }
            writeChunk("IEND", new byte[0], 0);
        } finally {
            deflater.end();
            out.close();
        }
    }

    /** Releases the file handle without completing the image. */
    public void abort() {
        if (closed) {
            return;
        }
        closed = true;
        deflater.end();
        try {
            out.close();
        } catch (IOException ignored) {
            // The partial file is deleted by the caller.
        }
    }

    /** Chooses the PNG filter with the smallest sum of absolute signed residuals, as libpng does. */
    private byte[] filterRow(byte[] rgba, int start) {
        byte[] prior = previousRow;
        long bestScore = Long.MAX_VALUE;
        int bestFilter = 0;
        for (int filter = 0; filter < filtered.length; filter++) {
            byte[] target = filtered[filter];
            long score = 0;
            for (int i = 0; i < rowBytes; i++) {
                int raw = rgba[start + i] & 0xFF;
                int left = i >= 4 ? rgba[start + i - 4] & 0xFF : 0;
                int up = prior[i] & 0xFF;
                int upLeft = i >= 4 ? prior[i - 4] & 0xFF : 0;
                int value = switch (filter) {
                    case 0 -> raw;
                    case 1 -> raw - left;
                    case 2 -> raw - up;
                    case 3 -> raw - ((left + up) >>> 1);
                    default -> raw - paeth(left, up, upLeft);
                };
                byte encoded = (byte) value;
                target[i + 1] = encoded;
                score += Math.abs(encoded);
            }
            if (score < bestScore) {
                bestScore = score;
                bestFilter = filter;
            }
        }
        return filtered[bestFilter];
    }

    private static int paeth(int left, int up, int upLeft) {
        int estimate = left + up - upLeft;
        int distanceLeft = Math.abs(estimate - left);
        int distanceUp = Math.abs(estimate - up);
        int distanceUpLeft = Math.abs(estimate - upLeft);
        if (distanceLeft <= distanceUp && distanceLeft <= distanceUpLeft) {
            return left;
        }
        return distanceUp <= distanceUpLeft ? up : upLeft;
    }

    private void deflate(byte[] input, boolean finish) throws IOException {
        if (input != null) {
            deflater.setInput(input);
        }
        if (finish) {
            deflater.finish();
        }
        while (finish ? !deflater.finished() : !deflater.needsInput()) {
            int produced = deflater.deflate(deflateBuffer);
            int offset = 0;
            while (offset < produced) {
                int copied = Math.min(produced - offset, CHUNK_DATA_LIMIT - chunkLength);
                System.arraycopy(deflateBuffer, offset, chunkData, chunkLength, copied);
                chunkLength += copied;
                offset += copied;
                if (chunkLength == CHUNK_DATA_LIMIT) {
                    flushChunk();
                }
            }
        }
    }

    private void flushChunk() throws IOException {
        if (chunkLength > 0) {
            writeChunk("IDAT", chunkData, chunkLength);
            chunkLength = 0;
        }
    }

    private void writeChunk(String type, byte[] data, int length) throws IOException {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        byte[] word = new byte[4];
        putInt(word, 0, length);
        out.write(word);
        out.write(typeBytes);
        out.write(data, 0, length);
        crc.reset();
        crc.update(typeBytes);
        crc.update(data, 0, length);
        putInt(word, 0, (int) crc.getValue());
        out.write(word);
    }

    private static void putInt(byte[] target, int offset, int value) {
        target[offset] = (byte) (value >>> 24);
        target[offset + 1] = (byte) (value >>> 16);
        target[offset + 2] = (byte) (value >>> 8);
        target[offset + 3] = (byte) value;
    }
}
