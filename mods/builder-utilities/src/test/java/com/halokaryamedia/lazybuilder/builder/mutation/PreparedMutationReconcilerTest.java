package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.ChangeSetWriter;
import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
import com.halokaryamedia.lazybuilder.builder.history.LocalBlockPosition;
import com.halokaryamedia.lazybuilder.builder.history.MemoryChangeSetStorage;
import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PreparedMutationReconcilerTest {
    @Test
    void aggregatesAppliedPartialAndConflictStates() throws Exception {
        try (StoredChangeSet prepared = prepared()) {
            Map<String, String> world = new HashMap<>();
            world.put("0,64,0", "minecraft:stone");
            world.put("1,64,0", "minecraft:dirt");
            PreparedReconciliationReport partial = PreparedMutationReconciler.reconcile(
                    prepared, (x, y, z) -> world.get(x + "," + y + "," + z));
            assertEquals(ReconciliationState.PARTIALLY_APPLIED, partial.state());
            assertEquals(1, partial.beforeMatches());
            assertEquals(1, partial.afterMatches());

            world.put("0,64,0", "minecraft:diamond_block");
            PreparedReconciliationReport conflict = PreparedMutationReconciler.reconcile(
                    prepared, (x, y, z) -> world.get(x + "," + y + "," + z));
            assertEquals(ReconciliationState.CONFLICT, conflict.state());
            assertEquals(1, conflict.conflicts());
        }
    }

    @Test
    void noopGuardMatchesBothBeforeAndAfter() throws Exception {
        try (ChangeSetWriter writer = new MemoryChangeSetStorage().begin("guard")) {
            writer.append(new ChunkChangeSet(
                    0, 0,
                    List.of("minecraft:chest"),
                    new long[]{LocalBlockPosition.pack(0, 64, 0)},
                    new int[]{0},
                    new int[]{0}
            ));
            try (StoredChangeSet stored = writer.commit()) {
                PreparedReconciliationReport report =
                        PreparedMutationReconciler.reconcile(
                                stored,
                                (x, y, z) -> "minecraft:chest");
                assertEquals(1, report.beforeMatches());
                assertEquals(1, report.afterMatches());
                assertEquals(ReconciliationState.FULLY_APPLIED, report.state());
            }
        }
    }

    private static StoredChangeSet prepared() throws Exception {
        ChangeSetWriter writer = new MemoryChangeSetStorage().begin("op");
        writer.append(new ChunkChangeSet(
                0, 0,
                List.of("minecraft:stone", "minecraft:dirt"),
                new long[]{LocalBlockPosition.pack(0, 64, 0), LocalBlockPosition.pack(1, 64, 0)},
                new int[]{0, 0},
                new int[]{1, 1}
        ));
        return writer.commit();
    }
}
