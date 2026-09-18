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
        TerrainArenaDrawPlanner.Command command = command(0L, 0, 0);
        int bytes = command.state().vertexPayloadBytes();

        assertTrue(TerrainPhysicalArenaPolicy.canUploadVertex(command, bytes));
        assertEquals(1 << 20, TerrainPhysicalArenaPolicy.plannedVertexCapacity(command, bytes));
        assertTrue(TerrainPhysicalArenaPolicy.isDrawReady(command));
    }

    @Test
    void rejectsMismatchedPayloadAndMisalignedBaseVertex() {
        TerrainArenaDrawPlanner.Command aligned = command(0L, 0, 0);
        TerrainArenaDrawPlanner.Command misaligned = command(1L, 0, 0);

        assertFalse(TerrainPhysicalArenaPolicy.canUploadVertex(
                aligned,
                aligned.state().vertexPayloadBytes() - 1
        ));
        assertFalse(TerrainPhysicalArenaPolicy.canUploadVertex(
                misaligned,
                misaligned.state().vertexPayloadBytes()
        ));
        assertEquals(-1, TerrainPhysicalArenaPolicy.plannedVertexCapacity(
                misaligned,
                misaligned.state().vertexPayloadBytes()
        ));
    }

    @Test
    void acceptsAlignedCustomIndexRangeAndRejectsWrongPayload() {
        TerrainArenaDrawPlanner.Command command = command(0L, 48, 3);

        assertTrue(TerrainPhysicalArenaIndexPolicy.isCustomIndexReady(command));
        assertEquals(1 << 20, TerrainPhysicalArenaPolicy.plannedIndexCapacity(command, 48));
        assertTrue(TerrainPhysicalArenaPolicy.isDrawReady(command));
        assertEquals(0, TerrainPhysicalArenaPolicy.baseVertex(command));
        assertEquals(-1, TerrainPhysicalArenaPolicy.plannedIndexCapacity(command, 46));
    }

    @Test
    void rejectsCustomIndexPayloadThatDoesNotMatchIndexCount() {
        TerrainArenaDrawPlanner.Command command = command(0L, 46, 3);
        assertFalse(TerrainPhysicalArenaIndexPolicy.isCustomIndexReady(command));
        assertFalse(TerrainPhysicalArenaPolicy.isDrawReady(command));
    }

    private static TerrainArenaDrawPlanner.Command command(long offset, int indexBytes, int layerSlot) {
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
                indexBytes
        );
        long required = state.requiredAllocationBytes();
        var arena = new TerrainRegionAllocationRegistry.ArenaKey(0, 0, 0, layerSlot);
        var handle = new TerrainRegionAllocationRegistry.Handle(arena, offset, required, required, 1L);
        return new TerrainArenaDrawPlanner.Command(handle, state);
    }
}
