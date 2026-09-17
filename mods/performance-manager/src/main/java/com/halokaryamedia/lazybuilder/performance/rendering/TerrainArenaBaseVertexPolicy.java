package com.halokaryamedia.lazybuilder.performance.rendering;

/** Pure eligibility math for the first physical shared-VBO terrain draw path. */
public final class TerrainArenaBaseVertexPolicy {
    private TerrainArenaBaseVertexPolicy() {
    }

    public static boolean isReady(TerrainArenaDrawPlanner.Command command) {
        if (command == null || command.handle() == null || command.state() == null) return false;

        TerrainArenaDrawStateRegistry.DrawState state = command.state();
        if (!state.drawable() || state.indexPayloadBytes() != 0) return false;

        int stride = command.vertexStrideBytes();
        long vertexOffset = command.vertexByteOffset();
        if (stride <= 0 || vertexOffset < 0L || vertexOffset % stride != 0L) return false;

        long baseVertex = vertexOffset / stride;
        return baseVertex <= Integer.MAX_VALUE;
    }

    public static int baseVertex(TerrainArenaDrawPlanner.Command command) {
        if (!isReady(command)) return -1;
        return (int) (command.vertexByteOffset() / command.vertexStrideBytes());
    }
}
