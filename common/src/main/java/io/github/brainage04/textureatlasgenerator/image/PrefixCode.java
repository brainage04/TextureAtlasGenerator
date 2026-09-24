package io.github.brainage04.textureatlasgenerator.image;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** A canonical, length-limited prefix code in the WebP lossless form. */
final class PrefixCode {
    private static final int[] CODE_LENGTH_ORDER = {17, 18, 0, 1, 2, 3, 4, 5, 16, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};

    private final int[] lengths;
    /** Bit-reversed canonical codes, ready to be written least-significant bit first. */
    private final int[] codes;
    /** A single used symbol costs zero bits. */
    private final boolean singleSymbol;

    private PrefixCode(int[] lengths, boolean singleSymbol) {
        this.lengths = lengths;
        this.singleSymbol = singleSymbol;
        this.codes = canonicalCodes(lengths);
    }

    /** Builds a code for {@code histogram} whose codes are at most {@code maxLength} bits. */
    static PrefixCode build(int[] histogram, int maxLength) {
        int used = 0;
        for (int count : histogram) {
            if (count > 0) {
                used++;
            }
        }
        int[] lengths = new int[histogram.length];
        if (used <= 1) {
            for (int symbol = 0; symbol < histogram.length; symbol++) {
                if (histogram[symbol] > 0) {
                    lengths[symbol] = 1;
                }
            }
            if (used == 0) {
                lengths[0] = 1;
            }
            return new PrefixCode(lengths, true);
        }
        packageMerge(histogram, maxLength, lengths);
        return new PrefixCode(lengths, false);
    }

    void writeSymbol(BitWriter out, int symbol) throws IOException {
        if (!singleSymbol) {
            out.write(codes[symbol], lengths[symbol]);
        }
    }

    /** Estimated cost in bits of {@code symbol}; used symbols only. */
    int length(int symbol) {
        return singleSymbol ? 0 : lengths[symbol];
    }

    /** Serialises the code lengths as a simple or normal code length code. */
    void writeLengths(BitWriter out) throws IOException {
        int[] used = usedSymbols();
        if (used.length <= 2 && used[used.length - 1] < 256) {
            out.write(1, 1);
            out.write(used.length - 1, 1);
            if (used[0] < 2) {
                out.write(0, 1);
                out.write(used[0], 1);
            } else {
                out.write(1, 1);
                out.write(used[0], 8);
            }
            if (used.length == 2) {
                out.write(used[1], 8);
            }
            return;
        }

        out.write(0, 1);
        List<int[]> tokens = runLengthTokens(lengths);
        int[] tokenHistogram = new int[19];
        for (int[] token : tokens) {
            tokenHistogram[token[0]]++;
        }
        PrefixCode lengthCode = build(tokenHistogram, 7);
        int count = CODE_LENGTH_ORDER.length;
        while (count > 4 && lengthCode.lengths[CODE_LENGTH_ORDER[count - 1]] == 0) {
            count--;
        }
        out.write(count - 4, 4);
        for (int index = 0; index < count; index++) {
            out.write(lengthCode.lengths[CODE_LENGTH_ORDER[index]], 3);
        }
        out.write(0, 1); // max_symbol is the alphabet size
        for (int[] token : tokens) {
            lengthCode.writeSymbol(out, token[0]);
            switch (token[0]) {
                case 16 -> out.write(token[1] - 3, 2);
                case 17 -> out.write(token[1] - 3, 3);
                case 18 -> out.write(token[1] - 11, 7);
                default -> {
                }
            }
        }
    }

    private int[] usedSymbols() {
        return java.util.stream.IntStream.range(0, lengths.length).filter(symbol -> lengths[symbol] > 0).toArray();
    }

    /** Encodes code lengths with the repeat codes 16 (previous non-zero), 17 and 18 (zeros). */
    private static List<int[]> runLengthTokens(int[] lengths) {
        List<int[]> tokens = new ArrayList<>();
        int previous = 8;
        int index = 0;
        while (index < lengths.length) {
            int value = lengths[index];
            int run = 1;
            while (index + run < lengths.length && lengths[index + run] == value) {
                run++;
            }
            index += run;
            if (value == 0) {
                while (run >= 11) {
                    int take = Math.min(run, 138);
                    tokens.add(new int[] {18, take});
                    run -= take;
                }
                if (run >= 3) {
                    tokens.add(new int[] {17, run});
                    run = 0;
                }
                for (; run > 0; run--) {
                    tokens.add(new int[] {0, 0});
                }
                continue;
            }
            if (value != previous) {
                tokens.add(new int[] {value, 0});
                previous = value;
                run--;
            }
            while (run >= 3) {
                int take = Math.min(run, 6);
                tokens.add(new int[] {16, take});
                run -= take;
            }
            for (; run > 0; run--) {
                tokens.add(new int[] {value, 0});
            }
        }
        return tokens;
    }

    private static int[] canonicalCodes(int[] lengths) {
        int maxLength = 0;
        for (int length : lengths) {
            maxLength = Math.max(maxLength, length);
        }
        int[] countPerLength = new int[maxLength + 1];
        for (int length : lengths) {
            if (length > 0) {
                countPerLength[length]++;
            }
        }
        int[] nextCode = new int[maxLength + 2];
        int code = 0;
        for (int length = 1; length <= maxLength; length++) {
            code = (code + countPerLength[length - 1]) << 1;
            nextCode[length] = code;
        }
        int[] codes = new int[lengths.length];
        for (int symbol = 0; symbol < lengths.length; symbol++) {
            int length = lengths[symbol];
            if (length > 0) {
                codes[symbol] = Integer.reverse(nextCode[length]++) >>> (32 - length);
            }
        }
        return codes;
    }

    /** Length-limited Huffman code lengths by the package-merge (coin collector) algorithm. */
    private static void packageMerge(int[] histogram, int maxLength, int[] lengths) {
        int used = 0;
        for (int count : histogram) {
            if (count > 0) {
                used++;
            }
        }
        long[] leafWeights = new long[used];
        int[] leafSymbols = new int[used];
        Integer[] order = new Integer[histogram.length];
        for (int symbol = 0; symbol < histogram.length; symbol++) {
            order[symbol] = symbol;
        }
        Arrays.sort(order, (a, b) -> histogram[a] != histogram[b] ? Integer.compare(histogram[a], histogram[b]) : Integer.compare(a, b));
        int leaf = 0;
        for (int symbol : order) {
            if (histogram[symbol] > 0) {
                leafWeights[leaf] = histogram[symbol];
                leafSymbols[leaf] = symbol;
                leaf++;
            }
        }

        // Each node is a leaf (child == -1) or a package of two nodes of the previous level.
        List<long[]> levelWeights = new ArrayList<>();
        List<int[][]> levelChildren = new ArrayList<>();
        long[] weights = leafWeights.clone();
        int[][] children = new int[weights.length][];
        levelWeights.add(weights);
        levelChildren.add(children);
        for (int level = 1; level < maxLength; level++) {
            long[] previous = levelWeights.get(level - 1);
            int packages = previous.length / 2;
            long[] merged = new long[used + packages];
            int[][] mergedChildren = new int[used + packages][];
            int leafIndex = 0;
            int packageIndex = 0;
            for (int out = 0; out < merged.length; out++) {
                long packageWeight = packageIndex < packages
                        ? previous[2 * packageIndex] + previous[2 * packageIndex + 1] : Long.MAX_VALUE;
                if (leafIndex < used && leafWeights[leafIndex] <= packageWeight) {
                    merged[out] = leafWeights[leafIndex];
                    mergedChildren[out] = new int[] {-1, leafIndex};
                    leafIndex++;
                } else {
                    merged[out] = packageWeight;
                    mergedChildren[out] = new int[] {2 * packageIndex, 2 * packageIndex + 1};
                    packageIndex++;
                }
            }
            levelWeights.add(merged);
            levelChildren.add(mergedChildren);
        }
        for (int index = 0; index < weights.length; index++) {
            children[index] = new int[] {-1, index};
        }

        int top = maxLength - 1;
        int selected = 2 * used - 2;
        int[] leafDepth = new int[used];
        for (int index = 0; index < selected; index++) {
            count(levelChildren, top, index, leafDepth);
        }
        for (int index = 0; index < used; index++) {
            lengths[leafSymbols[index]] = leafDepth[index];
        }
    }

    private static void count(List<int[][]> levelChildren, int level, int node, int[] leafDepth) {
        int[] child = levelChildren.get(level)[node];
        if (child[0] == -1) {
            leafDepth[child[1]]++;
            return;
        }
        count(levelChildren, level - 1, child[0], leafDepth);
        count(levelChildren, level - 1, child[1], leafDepth);
    }
}
