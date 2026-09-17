package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
import com.halokaryamedia.lazybuilder.builder.history.LocalBlockPosition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChunkMutationReconcilerTest {
    private static ChunkChangeSet changes() {
        return new ChunkChangeSet(
                -1,
                2,
                List.of("minecraft:stone", "minecraft:air"),
                new long[]{
                        LocalBlockPosition.pack(15, 70, 0),
                        LocalBlockPosition.pack(0, 71, 15)
                },
                new int[]{0, 0},
                new int[]{1, 1}
        );
    }

    @Test
    void distinguishesNotAppliedFullyAppliedAndPartialWorlds() {
        ChunkChangeSet changes = changes();
        assertEquals(ReconciliationState.NOT_APPLIED,
                ChunkMutationReconciler.reconcile(changes, (x, y, z) -> "minecraft:stone").state());
        assertEquals(ReconciliationState.FULLY_APPLIED,
                ChunkMutationReconciler.reconcile(changes, (x, y, z) -> "minecraft:air").state());

        Map<String, String> world = Map.of(
                "-1,70,32", "minecraft:air",
                "-16,71,47", "minecraft:stone"
        );
        ChunkReconciliationReport partial = ChunkMutationReconciler.reconcile(
                changes,
                (x, y, z) -> world.get(x + "," + y + "," + z)
        );
        assertEquals(ReconciliationState.PARTIALLY_APPLIED, partial.state());
        assertEquals(1, partial.beforeMatches());
        assertEquals(1, partial.afterMatches());
        assertEquals(0, partial.conflicts());
    }

    @Test
    void reportsExternalOrUnexpectedMutationAsConflict() {
        ChunkReconciliationReport report = ChunkMutationReconciler.reconcile(
                changes(),
                (x, y, z) -> y == 70 ? "minecraft:dirt" : "minecraft:stone"
        );
        assertEquals(ReconciliationState.CONFLICT, report.state());
        assertEquals(1, report.conflicts());
        assertTrue(report.hasConflict());
    }

    @Test
    void worldCoordinatesRespectNegativeChunkFlooring() {
        StringBuilder seen = new StringBuilder();
        ChunkMutationReconciler.reconcile(changes(), (x, y, z) -> {
            seen.append(x).append(',').append(y).append(',').append(z).append(';');
            return "minecraft:stone";
        });
        assertEquals("-1,70,32;-16,71,47;", seen.toString());
    }
}
