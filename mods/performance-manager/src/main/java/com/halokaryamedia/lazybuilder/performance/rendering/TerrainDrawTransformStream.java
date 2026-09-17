package com.halokaryamedia.lazybuilder.performance.rendering;

import net.minecraft.client.gl.VertexBuffer;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable per-layer stream of the section translations that vanilla currently uploads through the
 * modelOffset uniform before each terrain draw.
 *
 * The stream preserves exact vanilla draw order, including reverse translucent traversal. It does
 * not alter shader state by itself; it materializes the per-command transform payload required by
 * the guarded multi-draw backend.
 */
public final class TerrainDrawTransformStream {
    private static final int LAYER_COUNT = 5;
    private static final int TRANSFORM_BYTES = Float.BYTES * 3;
    private static volatile LayerSnapshot[] current = emptyLayers();

    private TerrainDrawTransformStream() {
    }

    public static LayerSnapshot build(
            int layerSlot,
            List<Input> inputs,
            double cameraX,
            double cameraY,
            double cameraZ,
            boolean reverseOrder
    ) {
        if (layerSlot < 0 || layerSlot >= LAYER_COUNT || inputs == null || inputs.isEmpty()) {
            return LayerSnapshot.empty(layerSlot, reverseOrder);
        }

        List<Command> commands = new ArrayList<>(inputs.size());
        int physicalReady = 0;
        int candidateRuns = 0;
        long potentialDrawReduction = 0L;
        int currentRunSize = 0;
        Command previousReady = null;

        for (int n = 0; n < inputs.size(); n++) {
            int index = reverseOrder ? inputs.size() - 1 - n : n;
            Input input = inputs.get(index);
            if (input == null) continue;

            Command command = new Command(
                    input.source(),
                    input.arenaCommand(),
                    (float) ((double) input.originX() - cameraX),
                    (float) ((double) input.originY() - cameraY),
                    (float) ((double) input.originZ() - cameraZ)
            );
            commands.add(command);

            if (!command.physicalReady()) {
                potentialDrawReduction += completedRunReduction(currentRunSize);
                currentRunSize = 0;
                previousReady = null;
                continue;
            }

            physicalReady++;
            if (previousReady == null || !sameFutureMultiDrawState(previousReady, command)) {
                potentialDrawReduction += completedRunReduction(currentRunSize);
                candidateRuns++;
                currentRunSize = 1;
            } else {
                currentRunSize++;
            }
            previousReady = command;
        }

        potentialDrawReduction += completedRunReduction(currentRunSize);
        List<Command> immutable = List.copyOf(commands);
        return new LayerSnapshot(
                layerSlot,
                reverseOrder,
                immutable,
                physicalReady,
                physicalReady,
                candidateRuns,
                potentialDrawReduction,
                (long) immutable.size() * TRANSFORM_BYTES
        );
    }

    public static synchronized void publish(LayerSnapshot snapshot) {
        if (snapshot == null || snapshot.layerSlot() < 0 || snapshot.layerSlot() >= LAYER_COUNT) return;
        LayerSnapshot[] next = current.clone();
        next[snapshot.layerSlot()] = snapshot;
        current = next;
        TerrainMultiDrawCommandStream.publish(snapshot);
    }

    public static LayerSnapshot layer(int layerSlot) {
        LayerSnapshot[] snapshot = current;
        if (layerSlot < 0 || layerSlot >= snapshot.length) return LayerSnapshot.empty(layerSlot, false);
        return snapshot[layerSlot];
    }

    public static Snapshot snapshot() {
        int commands = 0;
        int physicalReady = 0;
        int transformBlocked = 0;
        int candidateRuns = 0;
        long potentialDrawReduction = 0L;
        long packedBytes = 0L;

        for (LayerSnapshot layer : current) {
            commands += layer.commands().size();
            physicalReady += layer.physicalReadyCommands();
            transformBlocked += layer.transformBlockedCommands();
            candidateRuns += layer.multiDrawCandidateRuns();
            potentialDrawReduction += layer.potentialDrawCallReduction();
            packedBytes += layer.packedTransformBytes();
        }

        return new Snapshot(
                commands,
                physicalReady,
                transformBlocked,
                candidateRuns,
                potentialDrawReduction,
                packedBytes
        );
    }

    public static synchronized void clear() {
        current = emptyLayers();
        TerrainMultiDrawCommandStream.clear();
    }

    private static boolean sameFutureMultiDrawState(Command left, Command right) {
        TerrainArenaDrawPlanner.Command leftCommand = left.arenaCommand();
        TerrainArenaDrawPlanner.Command rightCommand = right.arenaCommand();
        if (leftCommand == null || rightCommand == null
                || leftCommand.handle() == null || rightCommand.handle() == null
                || leftCommand.state() == null || rightCommand.state() == null) {
            return false;
        }

        TerrainArenaDrawStateRegistry.DrawState leftState = leftCommand.state();
        TerrainArenaDrawStateRegistry.DrawState rightState = rightCommand.state();
        return leftCommand.handle().arenaKey().equals(rightCommand.handle().arenaKey())
                && leftState.format() == rightState.format()
                && leftState.mode() == rightState.mode()
                && leftState.indexType() == rightState.indexType()
                && (leftState.indexPayloadBytes() > 0) == (rightState.indexPayloadBytes() > 0);
    }

    private static long completedRunReduction(int runSize) {
        return runSize > 1 ? runSize - 1L : 0L;
    }

    private static LayerSnapshot[] emptyLayers() {
        LayerSnapshot[] layers = new LayerSnapshot[LAYER_COUNT];
        for (int i = 0; i < layers.length; i++) layers[i] = LayerSnapshot.empty(i, i == 3);
        return layers;
    }

    public record Input(
            VertexBuffer source,
            TerrainArenaDrawPlanner.Command arenaCommand,
            int originX,
            int originY,
            int originZ
    ) {
        public Input(TerrainArenaDrawPlanner.Command arenaCommand, int originX, int originY, int originZ) {
            this(null, arenaCommand, originX, originY, originZ);
        }
    }

    public record Command(
            VertexBuffer source,
            TerrainArenaDrawPlanner.Command arenaCommand,
            float modelOffsetX,
            float modelOffsetY,
            float modelOffsetZ
    ) {
        public boolean physicalReady() {
            return TerrainPhysicalArenaPolicy.isDrawReady(arenaCommand);
        }
    }

    public record LayerSnapshot(
            int layerSlot,
            boolean reverseOrder,
            List<Command> commands,
            int physicalReadyCommands,
            int transformBlockedCommands,
            int multiDrawCandidateRuns,
            long potentialDrawCallReduction,
            long packedTransformBytes
    ) {
        private static LayerSnapshot empty(int layerSlot, boolean reverseOrder) {
            return new LayerSnapshot(layerSlot, reverseOrder, List.of(), 0, 0, 0, 0L, 0L);
        }
    }

    public record Snapshot(
            int commands,
            int physicalReadyCommands,
            int transformBlockedCommands,
            int multiDrawCandidateRuns,
            long potentialDrawCallReduction,
            long packedTransformBytes
    ) {
    }
}
