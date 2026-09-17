package com.halokaryamedia.lazybuilder.performance.rendering;

/** Pure bounds/eligibility rules for mirrored physical shared terrain buffers. */
public final class TerrainPhysicalArenaPolicy {
    private TerrainPhysicalArenaPolicy() {
    }

    public static boolean canUploadVertex(TerrainArenaDrawPlanner.Command command, int vertexPayloadBytes) {
        if (command == null || command.handle() == null || command.state() == null) return false;
        TerrainArenaDrawStateRegistry.DrawState state = command.state();
        if (!state.drawable() || vertexPayloadBytes <= 0 || state.vertexPayloadBytes() != vertexPayloadBytes) return false;

        int stride = command.vertexStrideBytes();
        long offset = command.vertexByteOffset();
        if (stride <= 0 || offset < 0L || offset > Integer.MAX_VALUE || offset % stride != 0L) return false;

        long expectedBytes = (long) state.vertexCount() * (long) stride;
        if (expectedBytes <= 0L || expectedBytes != vertexPayloadBytes) return false;

        long baseVertex = offset / stride;
        if (baseVertex > Integer.MAX_VALUE || state.vertexCount() > (long) Integer.MAX_VALUE - baseVertex) return false;

        long end = endOffset(offset, vertexPayloadBytes);
        return end > offset && end <= Integer.MAX_VALUE;
    }

    public static boolean isDrawReady(TerrainArenaDrawPlanner.Command command) {
        if (command == null || command.state() == null) return false;
        TerrainArenaDrawStateRegistry.DrawState state = command.state();
        if (!canUploadVertex(command, state.vertexPayloadBytes())) return false;
        return state.indexPayloadBytes() == 0 || TerrainPhysicalArenaIndexPolicy.isCustomIndexReady(command);
    }

    public static int baseVertex(TerrainArenaDrawPlanner.Command command) {
        if (command == null || command.state() == null) return -1;
        if (!canUploadVertex(command, command.state().vertexPayloadBytes())) return -1;
        return (int) (command.vertexByteOffset() / command.vertexStrideBytes());
    }

    public static int plannedVertexCapacity(TerrainArenaDrawPlanner.Command command, int vertexPayloadBytes) {
        if (!canUploadVertex(command, vertexPayloadBytes)) return -1;
        return plannedCapacityForEnd(endOffset(command.vertexByteOffset(), vertexPayloadBytes));
    }

    public static int plannedIndexCapacity(TerrainArenaDrawPlanner.Command command, int indexPayloadBytes) {
        if (command == null || command.state() == null) return -1;
        if (command.state().indexPayloadBytes() != indexPayloadBytes
                || !TerrainPhysicalArenaIndexPolicy.isCustomIndexReady(command)) {
            return -1;
        }
        return plannedCapacityForEnd(endOffset(command.indexByteOffset(), indexPayloadBytes));
    }

    private static int plannedCapacityForEnd(long requiredEnd) {
        if (requiredEnd <= 0L || requiredEnd > Integer.MAX_VALUE) return -1;
        long planned = TerrainRegionArenaPolicy.plannedCapacity(requiredEnd);
        if (planned < requiredEnd || planned > Integer.MAX_VALUE) return -1;
        return (int) planned;
    }

    static long endOffset(long offset, long bytes) {
        if (offset < 0L || bytes <= 0L || offset > Long.MAX_VALUE - bytes) return Long.MAX_VALUE;
        return offset + bytes;
    }
}
