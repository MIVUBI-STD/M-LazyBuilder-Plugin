package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;

import java.util.Objects;

/** Combined LazyBuilder + Paper settings snapshot for UI/protocol consumers. */
public record WorldSettingsSnapshot(
        WorldRecord world,
        WorldGameMode defaultGameMode,
        WorldRuntimeSettings runtime
) {
    public WorldSettingsSnapshot {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(defaultGameMode, "defaultGameMode");
        Objects.requireNonNull(runtime, "runtime");
    }
}
