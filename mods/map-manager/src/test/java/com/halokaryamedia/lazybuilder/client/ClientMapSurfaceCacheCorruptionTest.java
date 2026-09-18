package com.halokaryamedia.lazybuilder.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientMapSurfaceCacheCorruptionTest {
    @TempDir Path tempDir;

    @Test
    void rejectsRegionEntryCountBeyondCapacity() throws Exception {
        Path region = tempDir.resolve("oversized.surface.gz");
        try (DataOutputStream out = output(region)) {
            out.writeInt(5);
            out.writeInt(16_385);
        }

        assertThrows(IllegalStateException.class, () -> MapSurfaceRegionStore.read(region));
    }

    @Test
    void rejectsRegionIndexBeyondCapacity() throws Exception {
        Path region = tempDir.resolve("bad-index.surface.gz");
        try (DataOutputStream out = output(region)) {
            out.writeInt(5);
            out.writeInt(1);
            out.writeShort(16_384);
            out.writeInt(0xFF223344);
            out.writeInt(72);
        }

        assertThrows(IllegalStateException.class, () -> MapSurfaceRegionStore.read(region));
    }

    private static DataOutputStream output(Path destination) throws Exception {
        return new DataOutputStream(new BufferedOutputStream(
                new GZIPOutputStream(Files.newOutputStream(destination))));
    }
}
