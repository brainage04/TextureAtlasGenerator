package io.github.brainage04.textureatlasgenerator.image;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Encodes an ARGB image as a WebP lossless (VP8L) bitstream, following RFC 9649.
 *
 * <p>The encoder applies the subtract-green and predictor transforms, then codes the residuals
 * with LZ77 backward references, a colour cache and a single prefix code group. Decisions do not
 * depend on the prefix codes, so the image is scanned twice — once to gather symbol statistics,
 * once to write — without storing the symbol stream.
 */
final class Vp8lEncoder {
    /** libwebp's WEBP_MAX_DIMENSION; the 14-bit header could express 16384. */
    static final int MAX_DIMENSION = 16383;

    private static final int NUM_LENGTH_CODES = 24;
    private static final int NUM_DISTANCE_CODES = 40;
    private static final int MIN_MATCH = 3;
    private static final int MAX_MATCH = 4096;
    private static final int WINDOW_BITS = 20;
    private static final int WINDOW = 1 << WINDOW_BITS;
    private static final int MAX_DISTANCE = (1 << 20) - 120;
    private static final int HASH_BITS = 18;
    private static final int MAX_CHAIN = 48;
    private static final int CACHE_BITS = 10;
    private static final int PREDICTOR_BITS = 4;

    /** RFC 9649 distance map: (xi, yi) for distance codes 1..120. */
    private static final int[] DISTANCE_MAP = {
        0, 1, 1, 0, 1, 1, -1, 1, 0, 2, 2, 0, 1, 2, -1, 2, 2, 1, -2, 1, 2, 2, -2, 2, 0, 3, 3, 0, 1, 3, -1, 3,
        3, 1, -3, 1, 2, 3, -2, 3, 3, 2, -3, 2, 0, 4, 4, 0, 1, 4, -1, 4, 4, 1, -4, 1, 3, 3, -3, 3, 2, 4, -2, 4,
        4, 2, -4, 2, 0, 5, 3, 4, -3, 4, 4, 3, -4, 3, 5, 0, 1, 5, -1, 5, 5, 1, -5, 1, 2, 5, -2, 5, 5, 2, -5, 2,
        4, 4, -4, 4, 3, 5, -3, 5, 5, 3, -5, 3, 0, 6, 6, 0, 1, 6, -1, 6, 6, 1, -6, 1, 2, 6, -2, 6, 6, 2, -6, 2,
        4, 5, -4, 5, 5, 4, -5, 4, 3, 6, -3, 6, 6, 3, -6, 3, 0, 7, 7, 0, 1, 7, -1, 7, 5, 5, -5, 5, 7, 1, -7, 1,
        4, 6, -4, 6, 6, 4, -6, 4, 2, 7, -2, 7, 7, 2, -7, 2, 3, 7, -3, 7, 7, 3, -7, 3, 5, 6, -5, 6, 6, 5, -6, 5,
        8, 0, 4, 7, -4, 7, 7, 4, -7, 4, 8, 1, 8, 2, 6, 6, -6, 6, 8, 3, 5, 7, -5, 7, 7, 5, -7, 5, 8, 4, 6, 7,
        -6, 7, 7, 6, -7, 6, 8, 5, 7, 7, -7, 7, 8, 6, 8, 7
    };
    /** Plane code (1-based) for offset (xi, yi), indexed by (yi * 17 + xi + 8); 0 when absent. */
    private static final int[] PLANE_CODES = new int[8 * 17];

    static {
        for (int code = 0; code < 120; code++) {
            PLANE_CODES[DISTANCE_MAP[2 * code + 1] * 17 + DISTANCE_MAP[2 * code] + 8] = code + 1;
        }
    }

    private Vp8lEncoder() {}

    /**
     * Writes the VP8L header and image data for {@code argb}, which is overwritten with residuals.
     */
    static void encode(BitWriter out, int[] argb, int width, int height) throws IOException {
        if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
            throw new IllegalArgumentException("WebP images are limited to " + MAX_DIMENSION + " px per side");
        }
        boolean alpha = false;
        for (int pixel : argb) {
            if (pixel >>> 24 != 0xFF) {
                alpha = true;
                break;
            }
        }
        out.write(0x2F, 8);
        out.write(width - 1, 14);
        out.write(height - 1, 14);
        out.write(alpha ? 1 : 0, 1);
        out.write(0, 3);

        subtractGreen(argb);
        out.write(1, 1);
        out.write(2, 2);
        if (repeatRatio(argb, width) > 0.5) {
            // Pixel art and upscaled cells repeat exactly; LZ77 codes raw pixels far better than residuals.
            out.write(0, 1);
            writeImage(out, argb, width, height, true);
            return;
        }

        int[] modes = chooseModes(argb, width, height);
        int tilesWide = Math.ceilDiv(width, 1 << PREDICTOR_BITS);
        int tilesHigh = Math.ceilDiv(height, 1 << PREDICTOR_BITS);
        out.write(1, 1);
        out.write(0, 2);
        out.write(PREDICTOR_BITS - 2, 3);
        int[] modeImage = new int[modes.length];
        for (int index = 0; index < modes.length; index++) {
            modeImage[index] = 0xFF000000 | modes[index] << 8;
        }
        writeImage(out, modeImage, tilesWide, tilesHigh, false);
        toResiduals(argb, width, height, modes, tilesWide);
        out.write(0, 1);

        writeImage(out, argb, width, height, true);
    }

    /** Fraction of pixels equal to their left or upper neighbour. */
    static double repeatRatio(int[] argb, int width) {
        long repeats = 0;
        for (int index = 1; index < argb.length; index++) {
            int pixel = argb[index];
            if (pixel == argb[index - 1] || (index >= width && pixel == argb[index - width])) {
                repeats++;
            }
        }
        return (double) repeats / argb.length;
    }

    private static void subtractGreen(int[] argb) {
        for (int index = 0; index < argb.length; index++) {
            int pixel = argb[index];
            int green = (pixel >>> 8) & 0xFF;
            int red = (((pixel >>> 16) & 0xFF) - green) & 0xFF;
            int blue = ((pixel & 0xFF) - green) & 0xFF;
            argb[index] = (pixel & 0xFF00FF00) | red << 16 | blue;
        }
    }

    /** Picks the predictor per tile with the smallest summed absolute residual. */
    private static int[] chooseModes(int[] argb, int width, int height) {
        int tileSize = 1 << PREDICTOR_BITS;
        int tilesWide = Math.ceilDiv(width, tileSize);
        int tilesHigh = Math.ceilDiv(height, tileSize);
        int[] modes = new int[tilesWide * tilesHigh];
        long[] cost = new long[14];
        for (int tileY = 0; tileY < tilesHigh; tileY++) {
            for (int tileX = 0; tileX < tilesWide; tileX++) {
                Arrays.fill(cost, 0);
                int yEnd = Math.min(height, (tileY + 1) * tileSize);
                int xEnd = Math.min(width, (tileX + 1) * tileSize);
                for (int y = Math.max(1, tileY * tileSize); y < yEnd; y++) {
                    for (int x = Math.max(1, tileX * tileSize); x < xEnd; x++) {
                        int pixel = argb[y * width + x];
                        for (int mode = 0; mode < 14; mode++) {
                            cost[mode] += residualCost(pixel, predict(argb, width, x, y, mode));
                        }
                    }
                }
                int best = 0;
                for (int mode = 1; mode < 14; mode++) {
                    if (cost[mode] < cost[best]) {
                        best = mode;
                    }
                }
                modes[tileY * tilesWide + tileX] = best;
            }
        }
        return modes;
    }

    private static int residualCost(int pixel, int prediction) {
        int cost = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int delta = ((pixel >>> shift) - (prediction >>> shift)) & 0xFF;
            cost += Math.min(delta, 256 - delta);
        }
        return cost;
    }

    /** Replaces pixels with residuals, last to first so every prediction still sees original pixels. */
    private static void toResiduals(int[] argb, int width, int height, int[] modes, int tilesWide) {
        for (int y = height - 1; y >= 0; y--) {
            for (int x = width - 1; x >= 0; x--) {
                int prediction;
                if (y == 0) {
                    prediction = x == 0 ? 0xFF000000 : argb[x - 1];
                } else if (x == 0) {
                    prediction = argb[(y - 1) * width];
                } else {
                    prediction = predict(argb, width, x, y,
                            modes[(y >> PREDICTOR_BITS) * tilesWide + (x >> PREDICTOR_BITS)]);
                }
                argb[y * width + x] = subtract(argb[y * width + x], prediction);
            }
        }
    }

    private static int subtract(int pixel, int prediction) {
        int alpha = ((pixel >>> 24) - (prediction >>> 24)) & 0xFF;
        int red = ((pixel >>> 16) - (prediction >>> 16)) & 0xFF;
        int green = ((pixel >>> 8) - (prediction >>> 8)) & 0xFF;
        int blue = (pixel - prediction) & 0xFF;
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    /** RFC 9649 predictor modes for a pixel not in the first row or column. */
    private static int predict(int[] argb, int width, int x, int y, int mode) {
        int index = y * width + x;
        int left = argb[index - 1];
        int top = argb[index - width];
        int topLeft = argb[index - width - 1];
        int topRight = x == width - 1 ? argb[y * width] : argb[index - width + 1];
        return switch (mode) {
            case 0 -> 0xFF000000;
            case 1 -> left;
            case 2 -> top;
            case 3 -> topRight;
            case 4 -> topLeft;
            case 5 -> average(average(left, topRight), top);
            case 6 -> average(left, topLeft);
            case 7 -> average(left, top);
            case 8 -> average(topLeft, top);
            case 9 -> average(top, topRight);
            case 10 -> average(average(left, topLeft), average(top, topRight));
            case 11 -> select(left, top, topLeft);
            case 12 -> clampAddSubtractFull(left, top, topLeft);
            default -> clampAddSubtractHalf(average(left, top), topLeft);
        };
    }

    private static int average(int a, int b) {
        return (((a ^ b) & 0xFEFEFEFE) >>> 1) + (a & b);
    }

    private static int select(int left, int top, int topLeft) {
        int distanceLeft = 0;
        int distanceTop = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int l = (left >>> shift) & 0xFF;
            int t = (top >>> shift) & 0xFF;
            int predicted = l + t - ((topLeft >>> shift) & 0xFF);
            distanceLeft += Math.abs(predicted - l);
            distanceTop += Math.abs(predicted - t);
        }
        return distanceLeft < distanceTop ? left : top;
    }

    private static int clampAddSubtractFull(int a, int b, int c) {
        int result = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int value = ((a >>> shift) & 0xFF) + ((b >>> shift) & 0xFF) - ((c >>> shift) & 0xFF);
            result |= Math.clamp(value, 0, 255) << shift;
        }
        return result;
    }

    private static int clampAddSubtractHalf(int a, int b) {
        int result = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int av = (a >>> shift) & 0xFF;
            int value = av + (av - ((b >>> shift) & 0xFF)) / 2;
            result |= Math.clamp(value, 0, 255) << shift;
        }
        return result;
    }

    /**
     * Writes an entropy-coded image. The main ARGB image uses LZ77, the colour cache and several
     * prefix code groups chosen per tile; transform sub-images are small and coded as literals
     * with one group.
     */
    private static void writeImage(BitWriter out, int[] pixels, int width, int height, boolean main) throws IOException {
        int cacheBits = main ? CACHE_BITS : 0;
        if (cacheBits > 0) {
            out.write(1, 1);
            out.write(cacheBits, 4);
        } else {
            out.write(0, 1);
        }
        Layout layout = new Layout(256 + NUM_LENGTH_CODES + (cacheBits > 0 ? 1 << cacheBits : 0));

        int tileBits = main ? tileBits(width, height) : 0;
        int tilesWide = Math.ceilDiv(width, 1 << tileBits);
        int tileCount = main ? tilesWide * Math.ceilDiv(height, 1 << tileBits) : 1;
        int[][] tileHistograms = new int[tileCount][];
        TokenSink counter = new TokenSink() {
            private int[] histogram(int position) {
                int tile = main ? tileOf(position, width, tileBits, tilesWide) : 0;
                int[] histogram = tileHistograms[tile];
                if (histogram == null) {
                    histogram = new int[layout.size];
                    tileHistograms[tile] = histogram;
                }
                return histogram;
            }

            @Override
            public void literal(int position, int argb) {
                int[] histogram = histogram(position);
                histogram[(argb >>> 8) & 0xFF]++;
                histogram[layout.red + ((argb >>> 16) & 0xFF)]++;
                histogram[layout.blue + (argb & 0xFF)]++;
                histogram[layout.alpha + (argb >>> 24)]++;
            }

            @Override
            public void cache(int position, int index) {
                histogram(position)[256 + NUM_LENGTH_CODES + index]++;
            }

            @Override
            public void copy(int position, int length, int distanceCode) {
                int[] histogram = histogram(position);
                histogram[256 + prefix(length)]++;
                histogram[layout.distance + prefix(distanceCode)]++;
            }
        };
        tokenize(pixels, width, main, cacheBits, counter);

        int[] tileGroup = new int[tileCount];
        List<int[]> groups = main ? cluster(tileHistograms, layout, tileGroup) : singleGroup(tileHistograms, layout);
        if (main) {
            if (groups.size() > 1) {
                out.write(1, 1);
                out.write(tileBits - 2, 3);
                int[] entropyImage = new int[tileCount];
                for (int tile = 0; tile < tileCount; tile++) {
                    entropyImage[tile] = 0xFF000000 | tileGroup[tile] << 8;
                }
                writeImage(out, entropyImage, tilesWide, tileCount / tilesWide, false);
            } else {
                out.write(0, 1);
            }
        }

        PrefixCode[][] codes = new PrefixCode[groups.size()][5];
        for (int group = 0; group < groups.size(); group++) {
            int[] histogram = groups.get(group);
            for (int part = 0; part < 5; part++) {
                codes[group][part] = PrefixCode.build(layout.part(histogram, part), 15);
                codes[group][part].writeLengths(out);
            }
        }
        IOException[] failure = new IOException[1];
        TokenSink writer = new TokenSink() {
            private PrefixCode[] codes(int position) {
                return codes[main && groups.size() > 1 ? tileGroup[tileOf(position, width, tileBits, tilesWide)] : 0];
            }

            @Override
            public void literal(int position, int argb) {
                PrefixCode[] group = codes(position);
                try {
                    group[0].writeSymbol(out, (argb >>> 8) & 0xFF);
                    group[1].writeSymbol(out, (argb >>> 16) & 0xFF);
                    group[2].writeSymbol(out, argb & 0xFF);
                    group[3].writeSymbol(out, argb >>> 24);
                } catch (IOException error) {
                    failure[0] = error;
                }
            }

            @Override
            public void cache(int position, int index) {
                try {
                    codes(position)[0].writeSymbol(out, 256 + NUM_LENGTH_CODES + index);
                } catch (IOException error) {
                    failure[0] = error;
                }
            }

            @Override
            public void copy(int position, int length, int distanceCode) {
                PrefixCode[] group = codes(position);
                try {
                    group[0].writeSymbol(out, 256 + prefix(length));
                    writeExtraBits(out, length);
                    group[4].writeSymbol(out, prefix(distanceCode));
                    writeExtraBits(out, distanceCode);
                } catch (IOException error) {
                    failure[0] = error;
                }
            }
        };
        tokenize(pixels, width, main, cacheBits, writer);
        if (failure[0] != null) {
            throw failure[0];
        }
    }

    private static int tileOf(int position, int width, int tileBits, int tilesWide) {
        int y = position / width;
        int x = position - y * width;
        return (y >> tileBits) * tilesWide + (x >> tileBits);
    }

    /** Tile size for prefix code groups: about 4,000 tiles, between 8 and 512 px. */
    private static int tileBits(int width, int height) {
        int bits = 3;
        while (bits < 9 && (long) Math.ceilDiv(width, 1 << bits) * Math.ceilDiv(height, 1 << bits) > 4096) {
            bits++;
        }
        return bits;
    }

    /** The five alphabets of a prefix code group, concatenated into one histogram array. */
    private record Layout(int green, int red, int blue, int alpha, int distance, int size) {
        Layout(int greenSize) {
            this(0, greenSize, greenSize + 256, greenSize + 512, greenSize + 768, greenSize + 768 + NUM_DISTANCE_CODES);
        }

        int start(int part) {
            return switch (part) {
                case 0 -> green;
                case 1 -> red;
                case 2 -> blue;
                case 3 -> alpha;
                default -> distance;
            };
        }

        int end(int part) {
            return part == 4 ? size : start(part + 1);
        }

        int[] part(int[] histogram, int part) {
            return Arrays.copyOfRange(histogram, start(part), end(part));
        }
    }

    private static List<int[]> singleGroup(int[][] tileHistograms, Layout layout) {
        return List.of(tileHistograms[0] != null ? tileHistograms[0] : new int[layout.size]);
    }

    /** Estimated cost of describing one used symbol in a prefix code's length table. */
    private static final double SYMBOL_HEADER_BITS = 4.0;
    /** Estimated fixed cost of a prefix code group's five length tables. */
    private static final double GROUP_HEADER_BITS = 5 * 40.0;
    private static final int MAX_GROUPS = 256;

    /**
     * Assigns every tile to a prefix code group, greedily joining the group whose estimated size
     * (entropy plus code description) grows least, or opening a new group when that is cheaper.
     */
    private static List<int[]> cluster(int[][] tileHistograms, Layout layout, int[] tileGroup) {
        List<int[]> groups = new ArrayList<>();
        List<double[]> stats = new ArrayList<>(); // per part: total, sum c log2 c, used symbols
        int[] nonZero = new int[layout.size];
        for (int tile = 0; tile < tileHistograms.length; tile++) {
            int[] histogram = tileHistograms[tile];
            if (histogram == null) {
                tileGroup[tile] = 0;
                continue;
            }
            int used = 0;
            for (int symbol = 0; symbol < layout.size; symbol++) {
                if (histogram[symbol] != 0) {
                    nonZero[used++] = symbol;
                }
            }
            double alone = GROUP_HEADER_BITS + costOf(histogram, nonZero, used, layout);
            int best = -1;
            double bestDelta = alone;
            for (int group = 0; group < groups.size(); group++) {
                double delta = mergeDelta(groups.get(group), stats.get(group), histogram, nonZero, used, layout);
                if (delta < bestDelta || (groups.size() >= MAX_GROUPS && best < 0)) {
                    bestDelta = delta;
                    best = group;
                }
            }
            if (best < 0) {
                groups.add(new int[layout.size]);
                stats.add(new double[15]);
                best = groups.size() - 1;
            }
            add(groups.get(best), stats.get(best), histogram, nonZero, used, layout);
            tileGroup[tile] = best;
        }
        if (groups.isEmpty()) {
            groups.add(new int[layout.size]);
        }
        return groups;
    }

    private static double costOf(int[] histogram, int[] nonZero, int used, Layout layout) {
        double[] partTotal = new double[5];
        double[] partSum = new double[5];
        int[] partUsed = new int[5];
        for (int index = 0; index < used; index++) {
            int symbol = nonZero[index];
            int part = partOf(symbol, layout);
            partTotal[part] += histogram[symbol];
            partSum[part] += xlog2x(histogram[symbol]);
            partUsed[part]++;
        }
        double cost = 0;
        for (int part = 0; part < 5; part++) {
            cost += xlog2x(partTotal[part]) - partSum[part] + partUsed[part] * SYMBOL_HEADER_BITS;
        }
        return cost;
    }

    private static double mergeDelta(int[] group, double[] stats, int[] histogram, int[] nonZero, int used, Layout layout) {
        double[] total = new double[5];
        double[] sum = new double[5];
        double[] newUsed = new double[5];
        for (int part = 0; part < 5; part++) {
            total[part] = stats[part * 3];
            sum[part] = stats[part * 3 + 1];
        }
        double before = 0;
        for (int part = 0; part < 5; part++) {
            before += xlog2x(total[part]) - sum[part];
        }
        for (int index = 0; index < used; index++) {
            int symbol = nonZero[index];
            int part = partOf(symbol, layout);
            int existing = group[symbol];
            int added = histogram[symbol];
            total[part] += added;
            sum[part] += xlog2x(existing + added) - xlog2x(existing);
            if (existing == 0) {
                newUsed[part]++;
            }
        }
        double after = 0;
        for (int part = 0; part < 5; part++) {
            after += xlog2x(total[part]) - sum[part] + newUsed[part] * SYMBOL_HEADER_BITS;
        }
        return after - before;
    }

    private static void add(int[] group, double[] stats, int[] histogram, int[] nonZero, int used, Layout layout) {
        for (int index = 0; index < used; index++) {
            int symbol = nonZero[index];
            int part = partOf(symbol, layout);
            int existing = group[symbol];
            int added = histogram[symbol];
            stats[part * 3] += added;
            stats[part * 3 + 1] += xlog2x(existing + added) - xlog2x(existing);
            if (existing == 0) {
                stats[part * 3 + 2]++;
            }
            group[symbol] = existing + added;
        }
    }

    private static int partOf(int symbol, Layout layout) {
        if (symbol < layout.red) {
            return 0;
        } else if (symbol < layout.blue) {
            return 1;
        } else if (symbol < layout.alpha) {
            return 2;
        } else if (symbol < layout.distance) {
            return 3;
        }
        return 4;
    }

    private static final double[] XLOG2X = new double[4096];

    static {
        for (int value = 1; value < XLOG2X.length; value++) {
            XLOG2X[value] = value * (Math.log(value) / Math.log(2));
        }
    }

    private static double xlog2x(double value) {
        if (value < XLOG2X.length && value == (int) value) {
            return XLOG2X[(int) value];
        }
        return value <= 0 ? 0 : value * (Math.log(value) / Math.log(2));
    }

    private interface TokenSink {
        void literal(int position, int argb);

        void cache(int position, int index);

        void copy(int position, int length, int distanceCode);
    }

    /** Deterministic greedy LZ77 parse with a hash chain and a colour cache. */
    private static void tokenize(int[] pixels, int width, boolean lz77, int cacheBits, TokenSink sink) {
        int size = pixels.length;
        int[] cache = cacheBits > 0 ? new int[1 << cacheBits] : null;
        int cacheShift = 32 - cacheBits;
        int[] head = lz77 ? new int[1 << HASH_BITS] : null;
        int[] chain = lz77 ? new int[Math.min(WINDOW, Integer.highestOneBit(Math.max(1, size - 1)) << 1)] : null;
        int chainMask = lz77 ? chain.length - 1 : 0;
        if (lz77) {
            Arrays.fill(head, -1);
        }

        int position = 0;
        while (position < size) {
            int bestLength = 0;
            int bestDistance = 0;
            if (lz77 && position + MIN_MATCH <= size) {
                int maxLength = Math.min(MAX_MATCH, size - position);
                // Cheap, very common candidates first: the previous pixel and the one above.
                if (position >= 1) {
                    bestLength = matchLength(pixels, position, position - 1, maxLength);
                    bestDistance = 1;
                }
                if (width > 1 && position >= width) {
                    int length = matchLength(pixels, position, position - width, maxLength);
                    if (length > bestLength) {
                        bestLength = length;
                        bestDistance = width;
                    }
                }
                int hash = hash(pixels, position);
                int candidate = head[hash];
                for (int steps = 0; candidate >= 0 && steps < MAX_CHAIN && bestLength < maxLength; steps++) {
                    int distance = position - candidate;
                    if (distance > MAX_DISTANCE || distance > chain.length - 1) {
                        break;
                    }
                    int length = matchLength(pixels, position, candidate, maxLength);
                    if (length > bestLength) {
                        bestLength = length;
                        bestDistance = distance;
                    }
                    int next = chain[candidate & chainMask];
                    if (next >= candidate) {
                        break;
                    }
                    candidate = next;
                }
            }

            int advance;
            if (bestLength >= MIN_MATCH) {
                sink.copy(position, bestLength, distanceCode(bestDistance, width));
                advance = bestLength;
            } else {
                int pixel = pixels[position];
                if (cache != null && cache[(0x1E35A7BD * pixel) >>> cacheShift] == pixel) {
                    sink.cache(position, (0x1E35A7BD * pixel) >>> cacheShift);
                } else {
                    sink.literal(position, pixel);
                }
                advance = 1;
            }
            for (int index = position; index < position + advance; index++) {
                if (cache != null) {
                    int pixel = pixels[index];
                    cache[(0x1E35A7BD * pixel) >>> cacheShift] = pixel;
                }
                if (lz77 && index + MIN_MATCH <= size) {
                    int hash = hash(pixels, index);
                    chain[index & chainMask] = head[hash];
                    head[hash] = index;
                }
            }
            position += advance;
        }
    }

    private static int hash(int[] pixels, int index) {
        long key = (pixels[index] * 0x9E3779B1L) ^ (pixels[index + 1] * 0x85EBCA77L) ^ (pixels[index + 2] * 0xC2B2AE3DL);
        return (int) ((key ^ (key >>> 29)) * 0x165667B1L >>> (64 - HASH_BITS)) & ((1 << HASH_BITS) - 1);
    }

    private static int matchLength(int[] pixels, int position, int candidate, int maxLength) {
        int length = 0;
        while (length < maxLength && pixels[position + length] == pixels[candidate + length]) {
            length++;
        }
        return length;
    }

    /** Maps a scan-line distance to its distance code, preferring the short 2D neighbourhood codes. */
    private static int distanceCode(int distance, int width) {
        int yOffset = distance / width;
        int xOffset = distance - yOffset * width;
        if (xOffset <= 8 && yOffset < 8) {
            int code = PLANE_CODES[yOffset * 17 + xOffset + 8];
            if (code != 0) {
                return code;
            }
        }
        int rightX = xOffset - width;
        if (rightX >= -8 && yOffset + 1 < 8) {
            int code = PLANE_CODES[(yOffset + 1) * 17 + rightX + 8];
            if (code != 0) {
                return code;
            }
        }
        return distance + 120;
    }

    /** RFC 9649 LZ77 prefix code of a value in [1, 2^20]. */
    static int prefix(int value) {
        int x = value - 1;
        if (x < 4) {
            return x;
        }
        int highest = 31 - Integer.numberOfLeadingZeros(x);
        int second = (x >>> (highest - 1)) & 1;
        return 2 * highest + second;
    }

    private static void writeExtraBits(BitWriter out, int value) throws IOException {
        int x = value - 1;
        if (x < 4) {
            return;
        }
        int highest = 31 - Integer.numberOfLeadingZeros(x);
        int extraBits = highest - 1;
        out.write(x & ((1 << extraBits) - 1), extraBits);
    }
}
