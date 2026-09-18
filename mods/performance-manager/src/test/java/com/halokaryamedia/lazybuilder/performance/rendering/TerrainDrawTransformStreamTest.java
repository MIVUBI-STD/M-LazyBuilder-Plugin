package com.halokaryamedia.lazybuilder.performance.rendering;

import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TerrainDrawTransformStreamTest {
    @Test
    void preservesForwardOffsetsAndComputesFutureMultiDrawReduction() {
        var arena = new TerrainRegionAllocationRegistry.ArenaKey(0, 0, 0, 0);
        var state = sequentialState();
        long size = state.requiredAllocationBytes();

        TerrainDrawTransformStream.LayerSnapshot snapshot = TerrainDrawTransformStream.build(
                0,
                List.of(
                        input(command(arena, 0L, size, state, 1L), 0, 0, 0),
                        input(command(arena, 512L, size, state, 2L), 16, 0, 0)
                ),
                4.0D,
                5.0D,
                6.0D,
                false
        );

        assertEquals(2, snapshot.commands().size());
        assertEquals(-4.0F, snapshot.commands().get(0).modelOffsetX());
        assertEquals(-5.0F, snapshot.commands().get(0).modelOffsetY());
        assertEquals(-6.0F, snapshot.commands().get(0).modelOffsetZ());
        assertEquals(12.0F, snapshot.commands().get(1).modelOffsetX());
        assertEquals(2, snapshot.physicalReadyCommands());
        assertEquals(2, snapshot.transformBlockedCommands());
        assertEquals(1, snapshot.multiDrawCandidateRuns());
        assertEquals(1L, snapshot.potentialDrawCallReduction());
        assertEquals(24L, snapshot.packedTransformBytes());
    }

    @Test
    void translucentSnapshotPreservesReverseVanillaTraversal() {
        var arena = new TerrainRegionAllocationRegistry.ArenaKey(0, 0, 0, 3);
        var state = customIndexState();
        long size = state.requiredAllocationBytes();

        TerrainDrawTransformStream.LayerSnapshot snapshot = TerrainDrawTransformStream.build(
                3,
                List.of(
                        input(command(arena, 0L, size, state, 1L), 0, 0, 0),
                        input(command(arena, 1024L, size, state, 2L), 16, 0, 0)
                ),
                0.0D,
                0.0D,
                0.0D,
                true
        );

        assertEquals(16.0F, snapshot.commands().get(0).modelOffsetX());
        assertEquals(0.0F, snapshot.commands().get(1).modelOffsetX());
        assertEquals(2, snapshot.physicalReadyCommands());
        assertEquals(1, snapshot.multiDrawCandidateRuns());
        assertEquals(1L, snapshot.potentialDrawCallReduction());
    }

    @Test
    void fallbackBreaksFutureMultiDrawRun() {
        var arena = new TerrainRegionAllocationRegistry.ArenaKey(0, 0, 0, 1);
        var state = sequentialState();
        long size = state.requiredAllocationBytes();

        TerrainDrawTransformStream.LayerSnapshot snapshot = TerrainDrawTransformStream.build(
                1,
                Arrays.asList(
                        input(command(arena, 0L, size, state, 1L), 0, 0, 0),
                        input(command(arena, 512L, size, state, 2L), 16, 0, 0),
                        input(null, 32, 0, 0),
                        input(command(arena, 1024L, size, state, 3L), 48, 0, 0)
                ),
                0.0D,
                0.0D,
                0.0D,
                false
        );

        assertEquals(3, snapshot.physicalReadyCommands());
        assertEquals(2, snapshot.multiDrawCandidateRuns());
        assertEquals(1L, snapshot.potentialDrawCallReduction());
    }

    private static TerrainDrawTransformStream.Input input(
            TerrainArenaDrawPlanner.Command command,
            int x,
            int y,
            int z
    ) {
        return new TerrainDrawTransformStream.Input(command, x, y, z);
    }

    private static TerrainArenaDrawPlanner.Command command(
            TerrainRegionAllocationRegistry.ArenaKey arena,
            long offset,
            long size,
            TerrainArenaDrawStateRegistry.DrawState state,
            long generation
    ) {
        return new TerrainArenaDrawPlanner.Command(
                new TerrainRegionAllocationRegistry.Handle(arena, offset, size, size, generation),
                state
        );
    }

    private static TerrainArenaDrawStateRegistry.DrawState sequentialState() {
        VertexFormat format = VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL;
        int vertexCount = 16;
        return new TerrainArenaDrawStateRegistry.DrawState(
                format,
                vertexCount,
                24,
                VertexFormat.DrawMode.QUADS,
                VertexFormat.IndexType.SHORT,
                format.getVertexSizeByte() * vertexCount,
                0
        );
    }

    private static TerrainArenaDrawStateRegistry.DrawState customIndexState() {
        VertexFormat format = VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL;
        int vertexCount = 16;
        return new TerrainArenaDrawStateRegistry.DrawState(
                format,
                vertexCount,
                24,
                VertexFormat.DrawMode.QUADS,
                VertexFormat.IndexType.SHORT,
                format.getVertexSizeByte() * vertexCount,
                48
        );
    }
}
