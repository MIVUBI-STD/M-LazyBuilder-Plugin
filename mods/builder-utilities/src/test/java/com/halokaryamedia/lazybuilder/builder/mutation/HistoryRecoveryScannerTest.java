package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.ChangeSetWriter;
import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
import com.halokaryamedia.lazybuilder.builder.history.DiskChangeSetStorage;
import com.halokaryamedia.lazybuilder.builder.history.LocalBlockPosition;
import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HistoryRecoveryScannerTest {
    @TempDir
    Path tempDir;

    @Test
    void classifiesRecoveredDiskPlanAgainstActualWorld() throws Exception {
        DiskChangeSetStorage storage = new DiskChangeSetStorage(tempDir);
        StoredChangeSet leaked;
        try (ChangeSetWriter writer = storage.begin("orphan")) {
            writer.append(new ChunkChangeSet(
                    0, 0,
                    List.of("minecraft:stone", "minecraft:air"),
                    new long[]{LocalBlockPosition.pack(0, 64, 0)},
                    new int[]{0},
                    new int[]{1}
            ));
            leaked = writer.commit();
        }
        assertTrue(leaked.preserveForRecovery());

        var candidates = HistoryRecoveryScanner.scanBlocksOnly(
                storage,
                (x, y, z) -> "minecraft:air"
        );

        assertEquals(1, candidates.size());
        assertEquals(ReconciliationState.FULLY_APPLIED, candidates.get(0).state());
        assertFalse(candidates.get(0).requiresUserDecision());

        candidates.get(0).close();
        leaked.close();
    }

    @Test
    void combinedMixedSubsystemStatesArePartial() {
        assertEquals(
                ReconciliationState.PARTIALLY_APPLIED,
                HistoryRecoveryScanner.combine(
                        ReconciliationState.FULLY_APPLIED,
                        ReconciliationState.NOT_APPLIED
                )
        );
        assertEquals(
                ReconciliationState.CONFLICT,
                HistoryRecoveryScanner.combine(
                        ReconciliationState.NOT_APPLIED,
                        ReconciliationState.CONFLICT
                )
        );
    }
}
