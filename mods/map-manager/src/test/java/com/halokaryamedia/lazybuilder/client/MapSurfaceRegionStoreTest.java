package com.halokaryamedia.lazybuilder.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MapSurfaceRegionStoreTest {
    @TempDir Path tempDir;

    @Test
    void roundTripsSparseRegionAtomically() {
        Path file = tempDir.resolve("r.0.-1.surface.gz");
        ClientMapSurfaceCache.RegionSnapshot snapshot = new ClientMapSurfaceCache.RegionSnapshot(
                new int[] {0, 7, 16_383},
                new int[] {0xFF112233, 0xFF445566, 0xFF778899},
                new int[] {64, 70, -12},
                4L);

        assertTrue(MapSurfaceRegionStore.writeWithRetry(file, snapshot));

        ClientMapSurfaceCache.RegionSnapshot restored = MapSurfaceRegionStore.read(file);
        assertArrayEquals(snapshot.indices(), restored.indices());
        assertArrayEquals(snapshot.colors(), restored.colors());
        assertArrayEquals(snapshot.heights(), restored.heights());
    }

    @Test
    void regionFileUsesSignedRegionCoordinates() {
        long key = ((long) -3 << 32) ^ (12 & 0xFFFFFFFFL);
        Path file = MapSurfaceRegionStore.regionFile(tempDir, key);

        assertTrue(file.endsWith("r.-3.12.surface.gz"));
    }
}
