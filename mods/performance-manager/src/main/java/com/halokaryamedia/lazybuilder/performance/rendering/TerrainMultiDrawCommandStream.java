package com.halokaryamedia.lazybuilder.performance.rendering;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Packs physical-ready terrain draws into the exact command/transform payload shape needed by a
 * future shader-aware multi-draw backend. This does not submit GL multi-draw yet.
 */
public final class TerrainMultiDrawCommandStream {
    private static final int LAYER_COUNT = 5;
    private static final int COMMAND_BYTES = 24;
    private static final int TRANSFORM_BYTES = 16;
    private static volatile LayerPacket[] current = emptyLayers();

    private TerrainMultiDrawCommandStream() {
    }

    public static LayerPacket build(TerrainDrawTransformStream.LayerSnapshot layer) {
        if (layer == null || layer.layerSlot() < 0 || layer.layerSlot() >= LAYER_COUNT) {
            return LayerPacket.empty(layer == null ? -1 : layer.layerSlot());
        }

        List<PackedCommand> commands = new ArrayList<>();
        for (int i = 0; i < layer.commands().size(); i++) {
            TerrainDrawTransformStream.Command transform = layer.commands().get(i);
            if (transform == null || !transform.physicalReady()) continue;

            TerrainArenaDrawPlanner.Command arena = transform.arenaCommand();
            TerrainArenaDrawStateRegistry.DrawState state = arena == null ? null : arena.state();
            if (arena == null || state == null) continue;

            int baseVertex = TerrainPhysicalArenaPolicy.baseVertex(arena);
            if (baseVertex < 0) continue;

            long indexOffset = state.indexPayloadBytes() > 0 ? arena.indexByteOffset() : 0L;
            if (indexOffset < 0L) continue;

            commands.add(new PackedCommand(
                    arena,
                    state.indexCount(),
                    indexOffset,
                    baseVertex,
                    i,
                    transform.modelOffsetX(),
                    transform.modelOffsetY(),
                    transform.modelOffsetZ()
            ));
        }

        return new LayerPacket(
                layer.layerSlot(),
                List.copyOf(commands),
                layer.multiDrawCandidateRuns(),
                layer.potentialDrawCallReduction(),
                (long) commands.size() * COMMAND_BYTES,
                (long) commands.size() * TRANSFORM_BYTES
        );
    }

    public static synchronized void publish(TerrainDrawTransformStream.LayerSnapshot layer) {
        LayerPacket packet = build(layer);
        if (packet.layerSlot() < 0 || packet.layerSlot() >= LAYER_COUNT) return;
        LayerPacket[] next = current.clone();
        next[packet.layerSlot()] = packet;
        current = next;
    }

    public static LayerPacket layer(int layerSlot) {
        LayerPacket[] snapshot = current;
        if (layerSlot < 0 || layerSlot >= snapshot.length) return LayerPacket.empty(layerSlot);
        return snapshot[layerSlot];
    }

    public static Snapshot snapshot() {
        int commands = 0;
        int runs = 0;
        long reductions = 0L;
        long commandBytes = 0L;
        long transformBytes = 0L;
        for (LayerPacket layer : current) {
            commands += layer.commands().size();
            runs += layer.candidateRuns();
            reductions += layer.potentialDrawCallReduction();
            commandBytes += layer.packedCommandBytes();
            transformBytes += layer.packedTransformBytes();
        }
        return new Snapshot(commands, runs, reductions, commandBytes, transformBytes);
    }

    public static ByteBuffer packCommands(LayerPacket packet) {
        if (packet == null || packet.commands().isEmpty()) return ByteBuffer.allocate(0).asReadOnlyBuffer();
        ByteBuffer buffer = ByteBuffer.allocate(packet.commands().size() * COMMAND_BYTES).order(ByteOrder.nativeOrder());
        for (PackedCommand command : packet.commands()) {
            buffer.putInt(command.indexCount());
            buffer.putInt(command.baseVertex());
            buffer.putLong(command.indexByteOffset());
            buffer.putInt(command.transformIndex());
            buffer.putInt(0);
        }
        buffer.flip();
        return buffer.asReadOnlyBuffer().order(ByteOrder.nativeOrder());
    }

    public static ByteBuffer packTransforms(LayerPacket packet) {
        if (packet == null || packet.commands().isEmpty()) return ByteBuffer.allocate(0).asReadOnlyBuffer();
        ByteBuffer buffer = ByteBuffer.allocate(packet.commands().size() * TRANSFORM_BYTES).order(ByteOrder.nativeOrder());
        for (PackedCommand command : packet.commands()) {
            buffer.putFloat(command.modelOffsetX());
            buffer.putFloat(command.modelOffsetY());
            buffer.putFloat(command.modelOffsetZ());
            buffer.putFloat(0.0F);
        }
        buffer.flip();
        return buffer.asReadOnlyBuffer().order(ByteOrder.nativeOrder());
    }

    public static synchronized void clear() {
        current = emptyLayers();
    }

    private static LayerPacket[] emptyLayers() {
        LayerPacket[] layers = new LayerPacket[LAYER_COUNT];
        for (int i = 0; i < layers.length; i++) layers[i] = LayerPacket.empty(i);
        return layers;
    }

    public record PackedCommand(
            TerrainArenaDrawPlanner.Command arenaCommand,
            int indexCount,
            long indexByteOffset,
            int baseVertex,
            int transformIndex,
            float modelOffsetX,
            float modelOffsetY,
            float modelOffsetZ
    ) {
    }

    public record LayerPacket(
            int layerSlot,
            List<PackedCommand> commands,
            int candidateRuns,
            long potentialDrawCallReduction,
            long packedCommandBytes,
            long packedTransformBytes
    ) {
        static LayerPacket empty(int layerSlot) {
            return new LayerPacket(layerSlot, List.of(), 0, 0L, 0L, 0L);
        }
    }

    public record Snapshot(
            int commands,
            int candidateRuns,
            long potentialDrawCallReduction,
            long packedCommandBytes,
            long packedTransformBytes
    ) {
    }
}
