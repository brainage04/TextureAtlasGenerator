package io.github.brainage04.textureatlasgenerator.image;

import java.io.IOException;
import java.io.OutputStream;

/** Writes bits least-significant first, as the WebP lossless bitstream requires. */
final class BitWriter {
    private final OutputStream out;
    private long buffer;
    private int used;
    private long bytesWritten;

    BitWriter(OutputStream out) {
        this.out = out;
    }

    /** Appends the low {@code count} bits of {@code value} (count at most 32). */
    void write(int value, int count) throws IOException {
        if (count == 0) {
            return;
        }
        buffer |= (value & ((1L << count) - 1)) << used;
        used += count;
        while (used >= 8) {
            out.write((int) buffer & 0xFF);
            buffer >>>= 8;
            used -= 8;
            bytesWritten++;
        }
    }

    /** Pads the final byte with zero bits. */
    void flush() throws IOException {
        if (used > 0) {
            out.write((int) buffer & 0xFF);
            bytesWritten++;
            buffer = 0;
            used = 0;
        }
    }

    long bytesWritten() {
        return bytesWritten;
    }
}
