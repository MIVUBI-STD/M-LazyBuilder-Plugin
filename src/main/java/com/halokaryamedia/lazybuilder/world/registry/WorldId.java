package com.halokaryamedia.lazybuilder.world.registry;

import java.util.Objects;
import java.util.UUID;

/** Stable internal identity for one LazyBuilder-managed world. */
public record WorldId(UUID value) {
    public WorldId {
        Objects.requireNonNull(value, "value");
    }

    public static WorldId create() {
        return new WorldId(UUID.randomUUID());
    }

    public static WorldId parse(String value) {
        return new WorldId(UUID.fromString(Objects.requireNonNull(value, "value")));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
