package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.ChangeSetWriter;
import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
import com.halokaryamedia.lazybuilder.builder.history.DiskChangeSetStorage;
import com.halokaryamedia.lazybuilder.builder.history.LocalBlockPosition;
import com.halokaryamedia.lazybuilder.builder.history.ScopedOperationIds;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HistoryRecoveryScopeFilterTest {
    @TempDir Path tempDir;

    @Test
    void blockRecoveryClaimsOnlyMatchingWorldScope() throws Exception {
        DiskChangeSetStorage writerStorage = new DiskChangeSetStorage(tempDir);
        write(writerStorage, ScopedOperationIds.scope("worldA", "a"));
        write(writerStorage, ScopedOperationIds.scope("worldB", "b"));

        DiskChangeSetStorage restarted = new DiskChangeSetStorage(tempDir);
        var candidates = HistoryRecoveryScanner.scanBlocksOnly(
                restarted,
                (x, y, z) -> "minecraft:air",
                operationId -> ScopedOperationIds.belongsTo(operationId, "worldA")
        );

        assertEquals(1, candidates.size());
        assertEquals("worldA",
                ScopedOperationIds.scopeOf(candidates.get(0).changeSet().operationId()).orElseThrow());
        candidates.get(0).close();

        var other = HistoryRecoveryScanner.scanBlocksOnly(
                restarted,
                (x, y, z) -> "minecraft:air",
                operationId -> ScopedOperationIds.belongsTo(operationId, "worldB")
        );
        assertEquals(1, other.size());
        other.get(0).close();
    }

    private static void write(DiskChangeSetStorage storage, String id) throws Exception {
        try (ChangeSetWriter writer = storage.begin(id)) {
            writer.append(new ChunkChangeSet(
                    0, 0,
                    List.of("minecraft:stone", "minecraft:air"),
                    new long[]{LocalBlockPosition.pack(0, 64, 0)},
                    new int[]{0}, new int[]{1}));
            // Simulate process loss: committed owner intentionally not closed.
            writer.commit();
        }
    }
}
