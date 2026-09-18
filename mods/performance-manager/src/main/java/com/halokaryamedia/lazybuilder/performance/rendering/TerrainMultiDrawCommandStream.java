package com.halokaryamedia.lazybuilder.performance.rendering;

import net.minecraft.client.gl.VertexBuffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/** Packs physical-ready terrain draws into GPU-ready command and transform payloads. */
public final class TerrainMultiDrawCommandStream {
    private static final int LAYER_COUNT = 5;
    private static final int COMMAND_BYTES = 24;
    private static final int TRANSFORM_BYTES = 16;
    private static final ByteBuffer EMPTY = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder()).asReadOnlyBuffer();
    private static ByteBuffer commandScratch = ByteBuffer.allocateDirect(COMMAND_BYTES).order(ByteOrder.nativeOrder());
    private static ByteBuffer transformScratch = ByteBuffer.allocateDirect(TRANSFORM_BYTES).order(ByteOrder.nativeOrder());
    private static volatile LayerPacket[] current = emptyLayers();

    private TerrainMultiDrawCommandStream() {
    }

    public static LayerPacket build(TerrainDrawTransformStream.LayerSnapshot layer) {
        if (layer == null || layer.layerSlot() < 0 || layer.layerSlot() >= LAYER_COUNT) {
            return LayerPacket.empty(layer == null ? -1 : layer.layerSlot());
        }

        List<PackedCommand> commands = new ArrayList<>();
        for (int orderIndex = 0; orderIndex < layer.commands().size(); orderIndex++) {
            TerrainDrawTransformStream.Command transform = layer.commands().get(orderIndex);
            if (transform == null || !transform.physicalReady()) continue;

            TerrainArenaDrawPlanner.Command arena = transform.arenaCommand();
            TerrainArenaDrawStateRegistry.DrawState state = arena == null ? null : arena.state();
            if (arena == null || state == null) continue;

            int baseVertex = TerrainPhysicalArenaPolicy.baseVertex(arena);
            if (baseVertex < 0) continue;

            long indexOffset = state.indexPayloadBytes() > 0 ? arena.indexByteOffset() : 0L;
            if (indexOffset < 0L) continue;

            int packedTransformIndex = commands.size();
            commands.add(new PackedCommand(
                    transform.source(),
                    arena,
                    state.indexCount(),
                    indexOffset,
                    baseVertex,
                    packedTransformIndex,
                    orderIndex,
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

    public static synchronized ByteBuffer packCommands(LayerPacket packet) {
        if (packet == null || packet.commands().isEmpty()) return emptyBuffer();
        int required = Math.multiplyExact(packet.commands().size(), COMMAND_BYTES);
        commandScratch = ensureCapacity(commandScratch, required);
        commandScratch.clear();
        for (PackedCommand command : packet.commands()) {
            commandScratch.putInt(command.indexCount());
            commandScratch.putInt(command.baseVertex());
            commandScratch.putLong(command.indexByteOffset());
            commandScratch.putInt(command.transformIndex());
            commandScratch.putInt(0);
        }
        commandScratch.flip();
        return commandScratch.asReadOnlyBuffer().order(ByteOrder.nativeOrder());
    }

    public static synchronized ByteBuffer packTransforms(LayerPacket packet) {
        if (packet == null || packet.commands().isEmpty()) return emptyBuffer();
        int required = Math.multiplyExact(packet.commands().size(), TRANSFORM_BYTES);
        transformScratch = ensureCapacity(transformScratch, required);
        transformScratch.clear();
        for (PackedCommand command : packet.commands()) {
            transformScratch.putFloat(command.modelOffsetX());
            transformScratch.putFloat(command.modelOffsetY());
            transformScratch.putFloat(command.modelOffsetZ());
            transformScratch.putFloat(0.0F);
        }
        transformScratch.flip();
        return transformScratch.asReadOnlyBuffer().order(ByteOrder.nativeOrder());
    }

    public static synchronized void clear() {
        current = emptyLayers();
    }

    private static ByteBuffer emptyBuffer() {
        return EMPTY.duplicate().order(ByteOrder.nativeOrder());
    }

    private static ByteBuffer ensureCapacity(ByteBuffer current, int required) {
        if (required <= current.capacity()) return current;
        int capacity = Math.max(current.capacity(), 1);
        while (capacity < required) {
            int next = capacity << 1;
            if (next <= 0 || next < capacity) {
                capacity = required;
                break;
            }
            capacity = next;
        }
        return ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder());
    }

    private static LayerPacket[] emptyLayers() {
        LayerPacket[] layers = new LayerPacket[LAYER_COUNT];
        for (int i = 0; i < layers.length; i++) layers[i] = LayerPacket.empty(i);
        return layers;
    }

    public record PackedCommand(
            VertexBuffer source,
            TerrainArenaDrawPlanner.Command arenaCommand,
            int indexCount,
            long indexByteOffset,
            int baseVertex,
            int transformIndex,
            int orderIndex,
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
