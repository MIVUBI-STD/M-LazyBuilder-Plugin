package com.halokaryamedia.lazybuilder.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MapSurfaceLegacyMigratorTest {
    @TempDir Path tempDir;

    @Test
    void partitionsLegacySamplesAcrossSignedRegions() throws Exception {
        Path legacy = tempDir.resolve("legacy.surface.gz");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                new GZIPOutputStream(Files.newOutputStream(legacy))))) {
            out.writeInt(1);
            out.writeInt(2);
            writeLegacySample(out, -1, -1, 0xFF112233, 63);
            writeLegacySample(out, 128, 0, 0xFF445566, 70);
        }

        Map<Long, ClientMapSurfaceCache.RegionData> regions =
                MapSurfaceLegacyMigrator.readLegacySnapshot(legacy);

        assertEquals(2, regions.size());
        long negativeRegion = pack(-1, -1);
        long positiveRegion = pack(1, 0);
        assertTrue(regions.containsKey(negativeRegion));
        assertTrue(regions.containsKey(positiveRegion));
        assertEquals(1, regions.get(negativeRegion).size());
        assertEquals(1, regions.get(positiveRegion).size());
    }

    private static void writeLegacySample(
            DataOutputStream out,
            int x,
            int z,
            int color,
            int height
    ) throws Exception {
        out.writeLong(pack(x, z));
        out.writeInt(color);
        out.writeInt(height);
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }
}
