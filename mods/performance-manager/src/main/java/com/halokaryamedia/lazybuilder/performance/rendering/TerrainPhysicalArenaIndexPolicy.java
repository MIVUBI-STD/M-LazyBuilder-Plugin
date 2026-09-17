package com.halokaryamedia.lazybuilder.performance.rendering;

/** Pure custom-index bounds rules for shared physical terrain EBOs. */
public final class TerrainPhysicalArenaIndexPolicy {
    private TerrainPhysicalArenaIndexPolicy() {
    }

    public static boolean isCustomIndexReady(TerrainArenaDrawPlanner.Command command) {
        if (command == null || command.handle() == null || command.state() == null) return false;
        TerrainArenaDrawStateRegistry.DrawState state = command.state();
        if (!state.drawable() || state.indexPayloadBytes() <= 0 || state.indexType() == null) return false;

        long expectedBytes = (long) state.indexCount() * (long) state.indexType().size;
        if (expectedBytes <= 0L || expectedBytes != state.indexPayloadBytes()) return false;

        long offset = command.indexByteOffset();
        if (offset < 0L || offset > Integer.MAX_VALUE || offset % state.indexType().size != 0L) return false;
        long end = TerrainPhysicalArenaPolicy.endOffset(offset, state.indexPayloadBytes());
        return end > offset && end <= Integer.MAX_VALUE;
    }
}
