package io.github.brainage04.textureatlasgenerator.image;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Lossless WebP files: a single image, or an animation built frame by frame. */
public final class WebPWriter {
    /** Largest width or height a WebP image may have. */
    public static final int MAX_DIMENSION = Vp8lEncoder.MAX_DIMENSION;

    private WebPWriter() {}

    /** Writes {@code argb} (row-major, 0xAARRGGBB; overwritten) as a lossless WebP. */
    public static void writeStill(Path path, int[] argb, int width, int height) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            OutputStream out = new BufferedOutputStream(Channels.newOutputStream(channel), 1 << 16);
            out.write(ascii("RIFF"));
            out.write(new byte[4]);
            out.write(ascii("WEBP"));
            out.write(ascii("VP8L"));
            out.write(new byte[4]);
            BitWriter bits = new BitWriter(out);
            Vp8lEncoder.encode(bits, argb, width, height);
            bits.flush();
            long payload = bits.bytesWritten();
            if (payload % 2 == 1) {
                out.write(0);
            }
            out.flush();
            long riffSize = 4 + 8 + payload + (payload % 2);
            if (riffSize > 0xFFFFFFFEL) {
                throw new IOException("WebP file would exceed 4 GiB");
            }
            channel.write(littleEndian((int) riffSize), 4);
            channel.write(littleEndian((int) payload), 16);
        }
    }

    /** An animated lossless WebP whose frames all cover the whole canvas and replace it. */
    public static final class Animation implements AutoCloseable {
        private final FileChannel channel;
        private final OutputStream out;
        private final int width;
        private final int height;
        private long riffPayload = 4;
        private boolean closed;

        /**
         * @param loopCount number of plays, or 0 to loop forever
         */
        public Animation(Path path, int width, int height, int loopCount) throws IOException {
            if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
                throw new IllegalArgumentException("WebP images are limited to " + MAX_DIMENSION + " px per side");
            }
            this.width = width;
            this.height = height;
            channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            out = new BufferedOutputStream(Channels.newOutputStream(channel), 1 << 16);
            out.write(ascii("RIFF"));
            out.write(new byte[4]);
            out.write(ascii("WEBP"));

            ByteBuffer vp8x = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN);
            vp8x.put((byte) 0x12); // alpha and animation
            vp8x.put(new byte[3]);
            putUint24(vp8x, width - 1);
            putUint24(vp8x, height - 1);
            chunk("VP8X", vp8x.array());

            ByteBuffer anim = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN);
            anim.putInt(0); // transparent background
            anim.putShort((short) loopCount);
            chunk("ANIM", anim.array());
        }

        /** Appends a frame; {@code argb} is overwritten. */
        public void addFrame(int[] argb, int durationMillis) throws IOException {
            ByteArrayOutputStream frame = new ByteArrayOutputStream(argb.length);
            BitWriter bits = new BitWriter(frame);
            Vp8lEncoder.encode(bits, argb, width, height);
            bits.flush();
            byte[] vp8l = frame.toByteArray();

            ByteArrayOutputStream anmf = new ByteArrayOutputStream(vp8l.length + 32);
            ByteBuffer header = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
            putUint24(header, 0);
            putUint24(header, 0);
            putUint24(header, width - 1);
            putUint24(header, height - 1);
            putUint24(header, durationMillis);
            header.put((byte) 0x02); // do not blend, do not dispose
            anmf.write(header.array());
            anmf.write(ascii("VP8L"));
            anmf.write(littleEndian(vp8l.length).array());
            anmf.write(vp8l);
            if (vp8l.length % 2 == 1) {
                anmf.write(0);
            }
            chunk("ANMF", anmf.toByteArray());
        }

        private void chunk(String type, byte[] payload) throws IOException {
            out.write(ascii(type));
            out.write(littleEndian(payload.length).array());
            out.write(payload);
            if (payload.length % 2 == 1) {
                out.write(0);
            }
            riffPayload += 8 + payload.length + (payload.length % 2);
            if (riffPayload > 0xFFFFFFFEL) {
                throw new IOException("WebP file would exceed 4 GiB");
            }
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            try {
                out.flush();
                channel.write(littleEndian((int) riffPayload), 4);
            } finally {
                channel.close();
            }
        }
    }

    private static void putUint24(ByteBuffer buffer, int value) {
        buffer.put((byte) value);
        buffer.put((byte) (value >>> 8));
        buffer.put((byte) (value >>> 16));
    }

    private static ByteBuffer littleEndian(int value) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0, value);
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }
}
