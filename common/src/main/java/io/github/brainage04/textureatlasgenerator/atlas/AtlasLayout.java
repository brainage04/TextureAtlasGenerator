package io.github.brainage04.textureatlasgenerator.atlas;

import java.util.ArrayList;
import java.util.List;

/**
 * Where each atlas cell goes: one square image when it fits in {@link #MAX_PAGE_SIDE} pixels,
 * otherwise several square pages of at most that size, filled in order.
 *
 * @param pixelSize cell width and height in pixels
 * @param pages the pages, in order; their entries are consecutive
 */
public record AtlasLayout(int pixelSize, List<Page> pages) {
    /**
     * Largest page side. A 43008 px atlas decodes to 7.4 GB, which image viewers refuse (Gwenview
     * stops near 2 GB) and WebP cannot hold (16383 px). 8192 px pages decode to 256 MiB, Qt's
     * default image limit, so they open in Qt-based viewers and stay practical in browsers.
     */
    public static final int MAX_PAGE_SIDE = 8192;

    /**
     * @param firstEntry index of the page's first entry
     * @param entryCount entries on the page
     * @param columns cells per row and rows per page
     */
    public record Page(int firstEntry, int entryCount, int columns) {
        public int side(int pixelSize) {
            return columns * pixelSize;
        }
    }

    /** A cell's page and position on it. */
    public record Cell(int page, int column, int row) {}

    public static AtlasLayout of(int entryCount, int pixelSize) {
        int maxColumns = Math.max(1, MAX_PAGE_SIDE / pixelSize);
        int perPage = maxColumns * maxColumns;
        List<Page> pages = new ArrayList<>();
        for (int first = 0; first < entryCount || pages.isEmpty(); first += perPage) {
            int count = Math.min(perPage, entryCount - first);
            pages.add(new Page(first, count, columnsFor(count)));
        }
        return new AtlasLayout(pixelSize, List.copyOf(pages));
    }

    /** Cells per row of a square page holding {@code items} cells. */
    public static int columnsFor(int items) {
        int columns = (int) Math.ceil(Math.sqrt(items));
        while ((long) columns * columns < items) {
            columns++;
        }
        return Math.max(1, columns);
    }

    public int entryCount() {
        Page last = pages.getLast();
        return last.firstEntry() + last.entryCount();
    }

    public Cell cell(int index) {
        int perPage = pages.getFirst().entryCount();
        int page = perPage == 0 ? 0 : index / perPage;
        Page sheet = pages.get(page);
        int local = index - sheet.firstEntry();
        return new Cell(page, local % sheet.columns(), local / sheet.columns());
    }

    /**
     * {@code <base>.<extension>} for a single page, {@code <base>_<n>.<extension>} (from 1)
     * otherwise.
     */
    public String imageFileName(String base, int page, String extension) {
        return pages.size() == 1 ? base + '.' + extension : base + '_' + (page + 1) + '.' + extension;
    }
}
