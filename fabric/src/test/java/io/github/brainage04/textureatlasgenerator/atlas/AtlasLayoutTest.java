package io.github.brainage04.textureatlasgenerator.atlas;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AtlasLayoutTest {
    @Test
    void pageBoundaryAndLastPageHaveCorrectCoordinates() {
        AtlasLayout layout = AtlasLayout.of(70, 1024);
        assertEquals(2, layout.pages().size());
        assertEquals(new AtlasLayout.Page(0, 64, 8), layout.pages().getFirst());
        assertEquals(new AtlasLayout.Page(64, 6, 3), layout.pages().getLast());
        assertEquals(new AtlasLayout.Cell(0, 7, 7), layout.cell(63));
        assertEquals(new AtlasLayout.Cell(1, 0, 0), layout.cell(64));
        assertEquals(new AtlasLayout.Cell(1, 2, 1), layout.cell(69));
        assertEquals("atlas_2.webp", layout.imageFileName("atlas", 1, "webp"));
        assertEquals("atlas.png", AtlasLayout.of(1, 1).imageFileName("atlas", 0, "png"));
    }

    @Test
    void everySupportedCellSizeProducesBoundedNonOverlappingPages() {
        for (int size = 1; size <= 1024; size++) {
            AtlasLayout layout = AtlasLayout.of(2545, size);
            assertEquals(2545, layout.entryCount());
            Set<AtlasLayout.Cell> cells = new HashSet<>();
            for (int i = 0; i < 2545; i++) {
                AtlasLayout.Cell cell = layout.cell(i);
                AtlasLayout.Page page = layout.pages().get(cell.page());
                assertTrue(page.side(size) <= 8192);
                assertTrue(cell.column() >= 0 && cell.column() < page.columns());
                assertTrue(cell.row() >= 0 && cell.row() < page.columns());
                assertTrue(cells.add(cell), "Duplicate cell at size " + size);
            }
        }
    }

    @Test
    void downsampleIgnoresTransparentColourAndAveragesAlpha() {
        ByteBuffer rgba = ByteBuffer.wrap(new byte[] {
                0, 0, (byte) 255, 0, 0, 0, (byte) 255, 0,
                (byte) 255, 0, 0, (byte) 255, 0, (byte) 255, 0, (byte) 255
        });
        byte[] target = new byte[12];
        AtlasItemRenderer.downsample(rgba, 2, 2, 2, target, 8, 4);
        assertArrayEquals(new byte[] {0, 0, 0, 0, (byte) 128, (byte) 128, 0, (byte) 128, 0, 0, 0, 0}, target);
    }

    @Test
    void unscaledReadbackFlipsRowsAndRespectsDestinationStride() {
        ByteBuffer rgba = ByteBuffer.wrap(new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
        byte[] target = new byte[16];
        AtlasItemRenderer.downsample(rgba, 1, 2, 1, target, 8, 2);
        assertArrayEquals(new byte[] {0, 0, 5, 6, 7, 8, 0, 0, 0, 0, 1, 2, 3, 4, 0, 0}, target);
    }
}
