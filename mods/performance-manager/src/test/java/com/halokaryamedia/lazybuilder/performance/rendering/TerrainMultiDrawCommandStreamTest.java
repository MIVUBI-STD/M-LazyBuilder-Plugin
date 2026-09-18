package com.halokaryamedia.lazybuilder.performance.rendering;

import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TerrainMultiDrawCommandStreamTest {
    @Test
    void packsPhysicalReadyDrawAndTransformPayloadsDeterministically() {
        VertexFormat format = VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL;
        int vertexCount = 16;
        int vertexBytes = format.getVertexSizeByte() * vertexCount;
        var state = new TerrainArenaDrawStateRegistry.DrawState(
                format,
                vertexCount,
                24,
                VertexFormat.DrawMode.QUADS,
                VertexFormat.IndexType.SHORT,
                vertexBytes,
                0
        );
        long required = state.requiredAllocationBytes();
        var arena = new TerrainRegionAllocationRegistry.ArenaKey(0, 0, 0, 0);
        var handle = new TerrainRegionAllocationRegistry.Handle(arena, 0L, required, required, 1L);
        var draw = new TerrainArenaDrawPlanner.Command(handle, state);
        var layer = TerrainDrawTransformStream.build(
                0,
                List.of(new TerrainDrawTransformStream.Input(draw, 32, 48, 64)),
                16.0D,
                16.0D,
                16.0D,
                false
        );

        TerrainMultiDrawCommandStream.LayerPacket packet = TerrainMultiDrawCommandStream.build(layer);
        assertEquals(1, packet.commands().size());
        assertEquals(24L, packet.packedCommandBytes());
        assertEquals(16L, packet.packedTransformBytes());

        ByteBuffer commands = TerrainMultiDrawCommandStream.packCommands(packet).order(ByteOrder.nativeOrder());
        assertEquals(24, commands.remaining());
        assertEquals(24, commands.getInt());
        assertEquals(0, commands.getInt());
        assertEquals(0L, commands.getLong());
        assertEquals(0, commands.getInt());
        assertEquals(0, commands.getInt());

        ByteBuffer transforms = TerrainMultiDrawCommandStream.packTransforms(packet).order(ByteOrder.nativeOrder());
        assertEquals(16, transforms.remaining());
        assertEquals(16.0F, transforms.getFloat());
        assertEquals(32.0F, transforms.getFloat());
        assertEquals(48.0F, transforms.getFloat());
        assertEquals(0.0F, transforms.getFloat());
    }

    @Test
    void reusablePackingBuffersResetLimitAndContentsBetweenPackets() {
        VertexFormat format = VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL;
        int vertexCount = 16;
        int vertexBytes = format.getVertexSizeByte() * vertexCount;
        var state = new TerrainArenaDrawStateRegistry.DrawState(
                format,
                vertexCount,
                24,
                VertexFormat.DrawMode.QUADS,
                VertexFormat.IndexType.SHORT,
                vertexBytes,
                0
        );
        long required = state.requiredAllocationBytes();
        var arena = new TerrainRegionAllocationRegistry.ArenaKey(0, 0, 0, 0);

        var largeLayer = TerrainDrawTransformStream.build(
                0,
                List.of(
                        new TerrainDrawTransformStream.Input(
                                new TerrainArenaDrawPlanner.Command(
                                        new TerrainRegionAllocationRegistry.Handle(arena, 0L, required, required, 1L),
                                        state
                                ),
                                16, 0, 0
                        ),
                        new TerrainDrawTransformStream.Input(
                                new TerrainArenaDrawPlanner.Command(
                                        new TerrainRegionAllocationRegistry.Handle(arena, 256L, required, required, 2L),
                                        state
                                ),
                                32, 0, 0
                        )
                ),
                0.0D, 0.0D, 0.0D, false
        );
        TerrainMultiDrawCommandStream.packTransforms(TerrainMultiDrawCommandStream.build(largeLayer));

        var smallLayer = TerrainDrawTransformStream.build(
                0,
                List.of(new TerrainDrawTransformStream.Input(
                        new TerrainArenaDrawPlanner.Command(
                                new TerrainRegionAllocationRegistry.Handle(arena, 0L, required, required, 3L),
                                state
                        ),
                        48, 16, 8
                )),
                0.0D, 0.0D, 0.0D, false
        );

        ByteBuffer transforms = TerrainMultiDrawCommandStream
                .packTransforms(TerrainMultiDrawCommandStream.build(smallLayer))
                .order(ByteOrder.nativeOrder());

        assertEquals(16, transforms.remaining());
        assertEquals(48.0F, transforms.getFloat());
        assertEquals(16.0F, transforms.getFloat());
        assertEquals(8.0F, transforms.getFloat());
        assertEquals(0.0F, transforms.getFloat());
    }

    @Test
    void publishingAndClearingTracksLayerPackets() {
        TerrainMultiDrawCommandStream.clear();
        TerrainDrawTransformStream.LayerSnapshot empty = TerrainDrawTransformStream.build(
                2, List.of(), 0.0D, 0.0D, 0.0D, false
        );
        TerrainMultiDrawCommandStream.publish(empty);
        assertEquals(0, TerrainMultiDrawCommandStream.snapshot().commands());
        TerrainMultiDrawCommandStream.clear();
        assertEquals(0L, TerrainMultiDrawCommandStream.snapshot().packedCommandBytes());
    }
}
