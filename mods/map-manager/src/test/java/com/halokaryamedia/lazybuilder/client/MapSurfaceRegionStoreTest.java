package com.halokaryamedia.lazybuilder.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void repeatedRegionalPersistenceLeavesNoTemporaryResidue() throws Exception {
        int regionCount = 128;
        for (int i = 0; i < regionCount; i++) {
            int regionX = i - regionCount / 2;
            int regionZ = (i % 17) - 8;
            long key = ((long) regionX << 32) ^ (regionZ & 0xFFFFFFFFL);
            Path file = MapSurfaceRegionStore.regionFile(tempDir, key);

            for (int revision = 1; revision <= 4; revision++) {
                ClientMapSurfaceCache.RegionSnapshot snapshot = new ClientMapSurfaceCache.RegionSnapshot(
                        new int[] {0, 8192, 16_383},
                        new int[] {
                                0xFF000000 | (i << 8) | revision,
                                0xFF224466,
                                0xFF88AACC
                        },
                        new int[] {revision, 64 + revision, 128 - revision},
                        revision);
                assertTrue(MapSurfaceRegionStore.writeWithRetry(file, snapshot));
            }

            ClientMapSurfaceCache.RegionSnapshot restored = MapSurfaceRegionStore.read(file);
            assertEquals(4, restored.heights()[0]);
            assertEquals(68, restored.heights()[1]);
            assertEquals(124, restored.heights()[2]);
        }

        try (var files = Files.list(tempDir)) {
            assertEquals(regionCount, files.filter(Files::isRegularFile).count());
        }
        try (var files = Files.list(tempDir)) {
            assertEquals(0L, files.filter(path -> path.getFileName().toString().endsWith(".tmp")).count());
        }
    }

    @Test
    void regionFileUsesSignedRegionCoordinates() {
        long key = ((long) -3 << 32) ^ (12 & 0xFFFFFFFFL);
        Path file = MapSurfaceRegionStore.regionFile(tempDir, key);

        assertTrue(file.endsWith("r.-3.12.surface.gz"));
    }
}
