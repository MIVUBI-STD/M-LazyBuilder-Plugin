package com.halokaryamedia.lazybuilder.performance.rendering;

/** Pure bounds/eligibility rules for the mirrored physical shared-VBO terrain path. */
public final class TerrainPhysicalArenaPolicy {
    private TerrainPhysicalArenaPolicy() {
    }

    public static boolean canUpload(TerrainArenaDrawPlanner.Command command, int vertexPayloadBytes) {
        if (!TerrainArenaBaseVertexPolicy.isReady(command)) return false;
        if (vertexPayloadBytes <= 0 || command.state() == null) return false;
        if (command.state().vertexPayloadBytes() != vertexPayloadBytes) return false;

        long offset = command.vertexByteOffset();
        long end = endOffset(offset, vertexPayloadBytes);
        return offset >= 0L && offset <= Integer.MAX_VALUE && end > offset && end <= Integer.MAX_VALUE;
    }

    public static int plannedCapacity(TerrainArenaDrawPlanner.Command command, int vertexPayloadBytes) {
        if (!canUpload(command, vertexPayloadBytes)) return -1;
        long requiredEnd = endOffset(command.vertexByteOffset(), vertexPayloadBytes);
        long planned = TerrainRegionArenaPolicy.plannedCapacity(requiredEnd);
        if (planned < requiredEnd || planned > Integer.MAX_VALUE) return -1;
        return (int) planned;
    }

    static long endOffset(long offset, long bytes) {
        if (offset < 0L || bytes <= 0L || offset > Long.MAX_VALUE - bytes) return Long.MAX_VALUE;
        return offset + bytes;
    }
}
