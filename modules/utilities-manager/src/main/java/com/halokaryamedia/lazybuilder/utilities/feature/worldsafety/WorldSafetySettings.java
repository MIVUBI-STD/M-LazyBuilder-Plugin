package com.halokaryamedia.lazybuilder.utilities.feature.worldsafety;

/** Immutable behavior switches owned by the World Safety feature family. */
public record WorldSafetySettings(
        boolean explosions,
        boolean leavesDecay,
        boolean farmlandTrample,
        boolean dragonEggTeleport
) {
}
