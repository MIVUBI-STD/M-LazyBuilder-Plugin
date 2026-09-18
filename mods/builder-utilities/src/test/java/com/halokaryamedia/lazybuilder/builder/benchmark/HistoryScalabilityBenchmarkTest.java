package com.halokaryamedia.lazybuilder.builder.benchmark;

import com.halokaryamedia.lazybuilder.builder.history.ChangeSetStorage;
import com.halokaryamedia.lazybuilder.builder.history.ChangeSetWriter;
import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
import com.halokaryamedia.lazybuilder.builder.history.CompressedMemoryChangeSetStorage;
import com.halokaryamedia.lazybuilder.builder.history.DiskChangeSetStorage;
import com.halokaryamedia.lazybuilder.builder.history.LocalBlockPosition;
import com.halokaryamedia.lazybuilder.builder.history.MemoryChangeSetStorage;
import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;
import com.halokaryamedia.lazybuilder.builder.history.StoredChunkCursor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in scalability probe. It asserts correctness only and emits timing metrics
 * for apples-to-apples comparison with FAWE on the same hardware.
 */
@Tag("benchmark")
class HistoryScalabilityBenchmarkTest {
    private static final int CHANGES_PER_CHUNK = 4096;

    @TempDir
    Path tempDir;

    @Test
    void encodeAndCursorScanLargeSyntheticHistory() throws Exception {
        long requestedChanges = Long.getLong("lazybuilder.benchmark.changes", 1_000_000L);
        if (requestedChanges <= 0 || requestedChanges > 50_000_000L) {
            throw new IllegalArgumentException(
                    "lazybuilder.benchmark.changes must be in 1..50,000,000");
        }

        String storageName = System.getProperty(
                "lazybuilder.benchmark.storage", "compressed").toLowerCase(Locale.ROOT);
        ChangeSetStorage storage = switch (storageName) {
            case "memory" -> new MemoryChangeSetStorage();
            case "compressed" -> new CompressedMemoryChangeSetStorage();
            case "disk" -> new DiskChangeSetStorage(tempDir.resolve("history"));
            default -> throw new IllegalArgumentException(
                    "Unknown benchmark storage: " + storageName);
        };

        long writeStarted = System.nanoTime();
        StoredChangeSet stored;
        long written = 0L;
        int chunkOrdinal = 0;
        try (ChangeSetWriter writer = storage.begin("benchmark-" + storageName)) {
            while (written < requestedChanges) {
                int count = (int) Math.min(CHANGES_PER_CHUNK, requestedChanges - written);
                writer.append(chunk(chunkOrdinal++, count));
                written += count;
            }
            stored = writer.commit();
        }
        long writeNanos = System.nanoTime() - writeStarted;
        assertEquals(requestedChanges, stored.changeCount());

        long scanStarted = System.nanoTime();
        long scanned = 0L;
        long chunks = 0L;
        try (StoredChangeSet ignored = stored;
             StoredChunkCursor cursor = StoredChunkCursor.open(stored)) {
            ChunkChangeSet chunk;
            while ((chunk = cursor.nextChunk()) != null) {
                scanned += chunk.size();
                chunks++;
            }
            assertTrue(cursor.exhausted());
            assertNull(cursor.nextChunk());
        }
        long scanNanos = System.nanoTime() - scanStarted;

        assertEquals(requestedChanges, scanned);
        long expectedChunks =
                Math.addExact(requestedChanges, CHANGES_PER_CHUNK - 1L) / CHANGES_PER_CHUNK;
        assertEquals(expectedChunks, chunks);

        double writeSeconds = writeNanos / 1_000_000_000.0;
        double scanSeconds = scanNanos / 1_000_000_000.0;

        System.out.printf(Locale.ROOT,
                "LAZYBUILDER_BENCHMARK storage=%s changes=%d chunks=%d "
                        + "write_ms=%.3f scan_ms=%.3f write_mchanges_s=%.3f scan_mchanges_s=%.3f%n",
                storageName,
                requestedChanges,
                chunks,
                writeNanos / 1_000_000.0,
                scanNanos / 1_000_000.0,
                requestedChanges / Math.max(writeSeconds, 1e-9) / 1_000_000.0,
                requestedChanges / Math.max(scanSeconds, 1e-9) / 1_000_000.0
        );
    }

    private static ChunkChangeSet chunk(int ordinal, int count) {
        long[] positions = new long[count];
        int[] before = new int[count];
        int[] after = new int[count];

        for (int i = 0; i < count; i++) {
            int localIndex = i & 4095;
            int localX = localIndex & 15;
            int localZ = (localIndex >>> 4) & 15;
            int y = (localIndex >>> 8) + ((ordinal & 15) * 16);
            positions[i] = LocalBlockPosition.pack(localX, y, localZ);
            after[i] = 1;
        }

        return new ChunkChangeSet(
                ordinal,
                0,
                List.of("minecraft:stone", "minecraft:air"),
                positions,
                before,
                after
        );
    }
}
