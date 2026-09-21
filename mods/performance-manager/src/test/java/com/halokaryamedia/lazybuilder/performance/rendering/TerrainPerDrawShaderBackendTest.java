package com.halokaryamedia.lazybuilder.performance.rendering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TerrainPerDrawShaderBackendTest {
    @Test
    void transformBufferCapacityUsesBoundedFourKiBQuanta() {
        assertEquals(0, TerrainPerDrawShaderBackend.plannedCapacity(0));
        assertEquals(4096, TerrainPerDrawShaderBackend.plannedCapacity(1));
        assertEquals(4096, TerrainPerDrawShaderBackend.plannedCapacity(4096));
        assertEquals(8192, TerrainPerDrawShaderBackend.plannedCapacity(4097));
        assertEquals(-1, TerrainPerDrawShaderBackend.plannedCapacity(Integer.MAX_VALUE));
    }
    @Test
    void packsOnlyRequestedTransformRange() {
        var packet = new TerrainMultiDrawCommandStream.LayerPacket(
                0,
                java.util.List.of(
                        packed(1.0F, 2.0F, 3.0F, 0),
                        packed(4.0F, 5.0F, 6.0F, 1),
                        packed(7.0F, 8.0F, 9.0F, 2)
                ),
                0,
                0L,
                0L,
                48L
        );

        java.nio.ByteBuffer range =
                TerrainMultiDrawCommandStream.packTransforms(packet, 1, 3);

        assertEquals(32, range.remaining());
        assertEquals(4.0F, range.getFloat(), 0.0F);
        assertEquals(5.0F, range.getFloat(), 0.0F);
        assertEquals(6.0F, range.getFloat(), 0.0F);
        assertEquals(0.0F, range.getFloat(), 0.0F);
        assertEquals(7.0F, range.getFloat(), 0.0F);
        assertEquals(8.0F, range.getFloat(), 0.0F);
        assertEquals(9.0F, range.getFloat(), 0.0F);
        assertEquals(0.0F, range.getFloat(), 0.0F);
    }

    private static TerrainMultiDrawCommandStream.PackedCommand packed(
            float x,
            float y,
            float z,
            int index
    ) {
        return new TerrainMultiDrawCommandStream.PackedCommand(
                null,
                null,
                6,
                0L,
                0,
                index,
                index,
                x,
                y,
                z
        );
    }
}
