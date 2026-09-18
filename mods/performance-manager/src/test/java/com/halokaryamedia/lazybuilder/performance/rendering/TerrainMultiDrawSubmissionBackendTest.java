package com.halokaryamedia.lazybuilder.performance.rendering;

import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TerrainMultiDrawSubmissionBackendTest {
    @Test
    void plansOnlyConsecutiveStateCompatibleRuns() {
        var arenaA = new TerrainRegionAllocationRegistry.ArenaKey(0, 0, 0, 0);
        var arenaB = new TerrainRegionAllocationRegistry.ArenaKey(1, 0, 0, 0);
        var state = state();

        var packet = new TerrainMultiDrawCommandStream.LayerPacket(
                0,
                List.of(
                        packed(arenaA, state, 0, 0),
                        packed(arenaA, state, 1, 1),
                        packed(arenaA, state, 2, 3),
                        packed(arenaB, state, 3, 4),
                        packed(arenaB, state, 4, 5)
                ),
                0,
                0L,
                0L,
                0L
        );

        List<TerrainMultiDrawSubmissionBackend.Run> runs = TerrainMultiDrawSubmissionBackend.planRuns(packet);
        assertEquals(2, runs.size());
        assertEquals(2, runs.get(0).commandCount());
        assertEquals(2, runs.get(1).commandCount());
    }

    private static TerrainMultiDrawCommandStream.PackedCommand packed(
            TerrainRegionAllocationRegistry.ArenaKey arena,
            TerrainArenaDrawStateRegistry.DrawState state,
            int transformIndex,
            int orderIndex
    ) {
        long size = state.requiredAllocationBytes();
        var draw = new TerrainArenaDrawPlanner.Command(
                new TerrainRegionAllocationRegistry.Handle(arena, transformIndex * 512L, size, size, transformIndex + 1L),
                state
        );
        return new TerrainMultiDrawCommandStream.PackedCommand(
                null,
                draw,
                state.indexCount(),
                0L,
                transformIndex * 16,
                transformIndex,
                orderIndex,
                0.0F,
                0.0F,
                0.0F
        );
    }

    private static TerrainArenaDrawStateRegistry.DrawState state() {
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
}
