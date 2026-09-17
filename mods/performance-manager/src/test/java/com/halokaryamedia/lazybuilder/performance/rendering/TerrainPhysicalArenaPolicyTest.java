package com.halokaryamedia.lazybuilder.performance.rendering;

import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TerrainPhysicalArenaPolicyTest {
    @Test
    void acceptsSequentialVertexPayloadAndPlansQuantizedCapacity() {
        TerrainArenaDrawPlanner.Command command = command(0L, 0);
        int bytes = command.state().vertexPayloadBytes();

        assertTrue(TerrainPhysicalArenaPolicy.canUpload(command, bytes));
        assertEquals(1 << 20, TerrainPhysicalArenaPolicy.plannedCapacity(command, bytes));
    }

    @Test
    void rejectsMismatchedPayloadMisalignmentAndTranslucentLayer() {
        TerrainArenaDrawPlanner.Command aligned = command(0L, 0);
        TerrainArenaDrawPlanner.Command misaligned = command(1L, 0);
        TerrainArenaDrawPlanner.Command translucent = command(0L, 3);

        assertFalse(TerrainPhysicalArenaPolicy.canUpload(aligned, aligned.state().vertexPayloadBytes() - 1));
        assertFalse(TerrainPhysicalArenaPolicy.canUpload(misaligned, misaligned.state().vertexPayloadBytes()));
        assertFalse(TerrainPhysicalArenaPolicy.canUpload(translucent, translucent.state().vertexPayloadBytes()));
        assertEquals(-1, TerrainPhysicalArenaPolicy.plannedCapacity(misaligned, misaligned.state().vertexPayloadBytes()));
    }

    private static TerrainArenaDrawPlanner.Command command(long offset, int layerSlot) {
        VertexFormat format = VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL;
        int vertexCount = 16;
        int vertexBytes = format.getVertexSizeByte() * vertexCount;
        TerrainArenaDrawStateRegistry.DrawState state = new TerrainArenaDrawStateRegistry.DrawState(
                format,
                vertexCount,
                24,
                VertexFormat.DrawMode.QUADS,
                VertexFormat.IndexType.SHORT,
                vertexBytes,
                0
        );
        long required = state.requiredAllocationBytes();
        var arena = new TerrainRegionAllocationRegistry.ArenaKey(0, 0, 0, layerSlot);
        var handle = new TerrainRegionAllocationRegistry.Handle(arena, offset, required, required, 1L);
        return new TerrainArenaDrawPlanner.Command(handle, state);
    }
}
