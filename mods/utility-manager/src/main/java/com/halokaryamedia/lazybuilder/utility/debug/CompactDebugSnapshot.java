package com.halokaryamedia.lazybuilder.utility.debug;

/** Immutable presentation snapshot for one Compact Debug render pass. */
public record CompactDebugSnapshot(
        int fps,
        String clientCpu,
        String clientGpu,
        String clientRam,
        int x,
        int y,
        int z,
        String facing,
        String biome,
        String time,
        String serverWorld,
        String serverCpu,
        String serverRam,
        boolean serverMetricsAvailable
) {
}
