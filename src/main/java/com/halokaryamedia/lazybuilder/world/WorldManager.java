package com.halokaryamedia.lazybuilder.world;

import com.halokaryamedia.lazybuilder.LazyBuilderPlugin;
import com.halokaryamedia.lazybuilder.world.conversion.ConversionRuntimePolicy;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;

import java.util.Objects;

/**
 * Canonical server-side owner for World Manager runtime coordination.
 *
 * <p>V1 intentionally starts with no periodic tasks. Feature services are added
 * behind this owner as their contracts become executable.</p>
 */
public final class WorldManager {
    private final LazyBuilderPlugin plugin;
    private final ConversionRuntimePolicy conversionRuntimePolicy;
    private final WorldRegistry worldRegistry;

    public WorldManager(LazyBuilderPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.conversionRuntimePolicy = ConversionRuntimePolicy.defaults();
        this.worldRegistry = new WorldRegistry();
    }

    public void start() {
        // Deliberately idle: no converter process, polling loop, file watcher,
        // preview generation, or world scan is started here.
        plugin.getLogger().fine("World Manager ready in idle mode.");
    }

    public void stop() {
        // Future active operations must own explicit cancellation/cleanup.
    }

    public ConversionRuntimePolicy conversionRuntimePolicy() {
        return conversionRuntimePolicy;
    }

    public WorldRegistry worldRegistry() {
        return worldRegistry;
    }
}
