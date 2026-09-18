package com.halokaryamedia.lazybuilder.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;
import java.util.zip.GZIPInputStream;

/** One-way migration from the pre-region map-surface cache into regional files. */
final class MapSurfaceLegacyMigrator {
    private static final Logger LOGGER = LoggerFactory.getLogger(MapSurfaceLegacyMigrator.class);
    private static final int LEGACY_FORMAT_VERSION = 1;
    private static final int REGION_SIZE = 128;
    private static final int MAX_LEGACY_ENTRIES = 262_144;

    private MapSurfaceLegacyMigrator() {}

    static void migrateAsync(
            Path legacy,
            Path targetDirectory,
            long generation,
            LongSupplier currentGeneration,
            Executor executor
    ) {
        if (legacy == null || !Files.isRegularFile(legacy)) return;
        Path migratedMarker = targetDirectory.resolve(".legacy-v1-migrated");
        if (Files.exists(migratedMarker)) return;

        CompletableFuture.runAsync(() -> {
            Map<Long, ClientMapSurfaceCache.RegionData> partitioned = readLegacySnapshot(legacy);
            if (partitioned.isEmpty() || generation != currentGeneration.getAsLong()) return;

            boolean complete = true;
            for (Map.Entry<Long, ClientMapSurfaceCache.RegionData> entry : partitioned.entrySet()) {
                Path regionFile = MapSurfaceRegionStore.regionFile(targetDirectory, entry.getKey());
                complete &= MapSurfaceRegionStore.writeWithRetry(regionFile, entry.getValue().snapshot());
            }
            if (!complete || generation != currentGeneration.getAsLong()) return;

            try {
                Files.createDirectories(targetDirectory);
                Files.writeString(migratedMarker, "v1\n");
            } catch (IOException error) {
                LOGGER.warn("Could not mark LazyBuilder legacy map migration complete: {}", migratedMarker, error);
            }
        }, executor);
    }

    static Map<Long, ClientMapSurfaceCache.RegionData> readLegacySnapshot(Path source) {
        Map<Long, ClientMapSurfaceCache.RegionData> result = new HashMap<>();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(Files.newInputStream(source))))) {
            if (in.readInt() != LEGACY_FORMAT_VERSION) return result;
            int count = Math.max(0, Math.min(MAX_LEGACY_ENTRIES, in.readInt()));
            for (int i = 0; i < count; i++) {
                long packed = in.readLong();
                int x = (int) (packed >> 32);
                int z = (int) packed;
                int color = in.readInt();
                int height = in.readInt();
                long regionKey = pack(
                        Math.floorDiv(x, REGION_SIZE),
                        Math.floorDiv(z, REGION_SIZE));
                result.computeIfAbsent(regionKey, ignored -> new ClientMapSurfaceCache.RegionData())
                        .put(localIndex(x, z), color, height);
            }
        } catch (IOException error) {
            LOGGER.warn("Could not read LazyBuilder legacy map cache: {}", source, error);
            result.clear();
        }
        return result;
    }

    private static int localIndex(int x, int z) {
        int localX = Math.floorMod(x, REGION_SIZE);
        int localZ = Math.floorMod(z, REGION_SIZE);
        return localZ * REGION_SIZE + localX;
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }
}
