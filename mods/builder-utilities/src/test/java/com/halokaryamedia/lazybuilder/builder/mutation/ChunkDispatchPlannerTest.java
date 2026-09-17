package com.halokaryamedia.lazybuilder.builder.mutation;

import com.halokaryamedia.lazybuilder.builder.history.ChunkChangeSet;
import com.halokaryamedia.lazybuilder.builder.history.LocalBlockPosition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChunkDispatchPlannerTest {
    private static ChunkChangeSet chunk() {
        return new ChunkChangeSet(
                -1, 2,
                List.of("minecraft:stone", "minecraft:dirt"),
                new long[]{
                        LocalBlockPosition.pack(15, 70, 0),
                        LocalBlockPosition.pack(0, 71, 15)
                },
                new int[]{0, 0},
                new int[]{1, 1}
        );
    }

    @Test
    void dispatchesOnlyBlocksStillAtBeforeState() {
        Map<String, String> states = Map.of(
                "-1,70,32", "minecraft:stone",
                "-16,71,47", "minecraft:dirt"
        );
        ChunkDispatchPlan plan = ChunkDispatchPlanner.plan(
                chunk(),
                (x, y, z) -> states.get(x + "," + y + "," + z)
        );
        assertEquals(ChunkDispatchState.READY, plan.state());
        assertEquals(1, plan.entries().size());
        assertEquals(-1, plan.entries().get(0).worldX());
        assertEquals(32, plan.entries().get(0).worldZ());
    }

    @Test
    void allAfterStateIsIdempotentlyAlreadyApplied() {
        ChunkDispatchPlan plan = ChunkDispatchPlanner.plan(
                chunk(),
                (x, y, z) -> "minecraft:dirt"
        );
        assertEquals(ChunkDispatchState.ALREADY_APPLIED, plan.state());
        assertTrue(plan.entries().isEmpty());
    }

    @Test
    void unexpectedWorldStateBecomesConflict() {
        ChunkDispatchPlan plan = ChunkDispatchPlanner.plan(
                chunk(),
                (x, y, z) -> x == -1 ? "minecraft:granite" : "minecraft:stone"
        );
        assertEquals(ChunkDispatchState.CONFLICT, plan.state());
        assertEquals(-1, plan.conflictX());
        assertEquals(70, plan.conflictY());
        assertEquals(32, plan.conflictZ());
    }
}
