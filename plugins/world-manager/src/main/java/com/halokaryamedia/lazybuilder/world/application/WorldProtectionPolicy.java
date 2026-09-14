package com.halokaryamedia.lazybuilder.world.application;

import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Single authority for worlds that must remain available as the server fallback/default world.
 *
 * <p>A configured fallback folder wins. When none is configured, the server's primary world
 * folder is used. This policy is intentionally runtime-derived and is not persisted as world metadata.</p>
 */
public final class WorldProtectionPolicy implements Predicate<WorldRecord> {
    private final Supplier<String> configuredFallbackFolder;
    private final Supplier<String> primaryWorldFolder;

    public WorldProtectionPolicy(
            Supplier<String> configuredFallbackFolder,
            Supplier<String> primaryWorldFolder
    ) {
        this.configuredFallbackFolder = Objects.requireNonNull(configuredFallbackFolder, "configuredFallbackFolder");
        this.primaryWorldFolder = Objects.requireNonNull(primaryWorldFolder, "primaryWorldFolder");
    }

    @Override
    public boolean test(WorldRecord world) {
        Objects.requireNonNull(world, "world");
        String configured = normalize(configuredFallbackFolder.get());
        if (configured != null) {
            return world.folderName().equals(configured);
        }
        String primary = normalize(primaryWorldFolder.get());
        return primary != null && world.folderName().equals(primary);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.strip();
    }
}
