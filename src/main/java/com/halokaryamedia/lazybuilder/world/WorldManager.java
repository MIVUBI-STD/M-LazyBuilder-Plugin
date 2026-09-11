package com.halokaryamedia.lazybuilder.world;

import com.halokaryamedia.lazybuilder.LazyBuilderPlugin;
import com.halokaryamedia.lazybuilder.world.application.BuildReadyPolicy;
import com.halokaryamedia.lazybuilder.world.application.WorldCreationService;
import com.halokaryamedia.lazybuilder.world.conversion.ConversionRuntimePolicy;
import com.halokaryamedia.lazybuilder.world.paper.PaperWorldRuntimeGateway;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistryPersistence;
import com.halokaryamedia.lazybuilder.world.registry.YamlWorldRegistryPersistence;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Canonical server-side owner for World Manager runtime coordination.
 *
 * <p>Startup performs one bounded registry load only. No converter process,
 * polling loop, file watcher, preview generator, or repeated world scan is
 * started while the feature is idle.</p>
 */
public final class WorldManager {
    private final LazyBuilderPlugin plugin;
    private final ConversionRuntimePolicy conversionRuntimePolicy;
    private final BuildReadyPolicy buildReadyPolicy;
    private final WorldRegistry worldRegistry;
    private final WorldRegistryPersistence registryPersistence;
    private final WorldCreationService worldCreationService;

    public WorldManager(LazyBuilderPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.conversionRuntimePolicy = ConversionRuntimePolicy.defaults();
        this.buildReadyPolicy = BuildReadyPolicy.defaults();
        this.worldRegistry = new WorldRegistry();

        Path registryPath = plugin.getDataFolder().toPath()
                .resolve("world")
                .resolve("registry.yml");
        this.registryPersistence = new YamlWorldRegistryPersistence(registryPath);
        this.worldCreationService = new WorldCreationService(
                worldRegistry,
                registryPersistence,
                new PaperWorldRuntimeGateway(plugin.getServer()),
                buildReadyPolicy
        );
    }

    public void start() {
        try {
            List<WorldRecord> persisted = registryPersistence.load();
            for (WorldRecord world : persisted) {
                worldRegistry.register(world);
            }
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Failed to load LazyBuilder world registry", exception);
        }

        plugin.getLogger().fine("World Manager ready with " + worldRegistry.size() + " managed worlds.");
    }

    public void stop() {
        // Current services are request-bound and own no persistent background workers.
    }

    public ConversionRuntimePolicy conversionRuntimePolicy() {
        return conversionRuntimePolicy;
    }

    public BuildReadyPolicy buildReadyPolicy() {
        return buildReadyPolicy;
    }

    public WorldRegistry worldRegistry() {
        return worldRegistry;
    }

    public WorldCreationService worldCreationService() {
        return worldCreationService;
    }
}
