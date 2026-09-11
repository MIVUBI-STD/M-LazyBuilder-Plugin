package com.halokaryamedia.lazybuilder.world;

import com.halokaryamedia.lazybuilder.LazyBuilderPlugin;
import com.halokaryamedia.lazybuilder.world.application.BuildReadyPolicy;
import com.halokaryamedia.lazybuilder.world.application.WorldCreationService;
import com.halokaryamedia.lazybuilder.world.application.WorldRuntimeGateway;
import com.halokaryamedia.lazybuilder.world.application.WorldRuntimeService;
import com.halokaryamedia.lazybuilder.world.application.WorldRuntimeState;
import com.halokaryamedia.lazybuilder.world.application.WorldRuntimeStateRegistry;
import com.halokaryamedia.lazybuilder.world.application.WorldSettingsService;
import com.halokaryamedia.lazybuilder.world.application.WorldTeleportService;
import com.halokaryamedia.lazybuilder.world.conversion.ConversionRuntimePolicy;
import com.halokaryamedia.lazybuilder.world.paper.PaperWorldRuntimeGateway;
import com.halokaryamedia.lazybuilder.world.registry.WorldLifecycle;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistryPersistence;
import com.halokaryamedia.lazybuilder.world.registry.YamlWorldRegistryPersistence;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

/**
 * Canonical server-side owner for World Manager runtime coordination.
 *
 * <p>Startup performs one bounded registry read plus requested auto-loads only.
 * No converter process, polling loop, file watcher, preview generator, or
 * repeated world scan is started while the feature is idle.</p>
 */
public final class WorldManager {
    private final LazyBuilderPlugin plugin;
    private final ConversionRuntimePolicy conversionRuntimePolicy;
    private final BuildReadyPolicy buildReadyPolicy;
    private final WorldRegistry worldRegistry;
    private final WorldRegistryPersistence registryPersistence;
    private final WorldRuntimeStateRegistry runtimeStates;
    private final WorldRuntimeGateway runtimeGateway;
    private final WorldRuntimeService worldRuntimeService;
    private final WorldCreationService worldCreationService;
    private final WorldTeleportService worldTeleportService;
    private final WorldSettingsService worldSettingsService;

    public WorldManager(LazyBuilderPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.conversionRuntimePolicy = ConversionRuntimePolicy.defaults();
        this.buildReadyPolicy = BuildReadyPolicy.defaults();
        this.worldRegistry = new WorldRegistry();
        this.runtimeStates = new WorldRuntimeStateRegistry();

        Path registryPath = plugin.getDataFolder().toPath()
                .resolve("world")
                .resolve("registry.yml");
        this.registryPersistence = new YamlWorldRegistryPersistence(registryPath);
        this.runtimeGateway = new PaperWorldRuntimeGateway(
                plugin.getServer(),
                () -> plugin.getConfig().getString("world-manager.fallback-world", "")
        );
        this.worldRuntimeService = new WorldRuntimeService(worldRegistry, runtimeStates, runtimeGateway);
        this.worldCreationService = new WorldCreationService(
                worldRegistry,
                registryPersistence,
                runtimeGateway,
                runtimeStates,
                buildReadyPolicy
        );
        this.worldTeleportService = new WorldTeleportService(worldRegistry, worldRuntimeService, runtimeGateway);
        this.worldSettingsService = new WorldSettingsService(
                worldRegistry,
                registryPersistence,
                worldRuntimeService,
                runtimeGateway,
                buildReadyPolicy
        );
    }

    public void start() {
        try {
            List<WorldRecord> persisted = registryPersistence.load();
            for (WorldRecord world : persisted) {
                worldRegistry.register(world);
                runtimeStates.initialize(
                        world.id(),
                        runtimeGateway.isLoaded(world) ? WorldRuntimeState.LOADED : WorldRuntimeState.UNLOADED
                );
            }
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Failed to load LazyBuilder world registry", exception);
        }

        for (WorldRecord world : worldRegistry.all()) {
            if (world.lifecycle() == WorldLifecycle.ACTIVE && world.autoLoad()
                    && runtimeStates.get(world.id()) == WorldRuntimeState.UNLOADED) {
                try {
                    worldRuntimeService.load(world.id());
                } catch (RuntimeException exception) {
                    plugin.getLogger().log(Level.SEVERE,
                            "Failed to auto-load managed world " + world.folderName() + "; leaving it unloaded.",
                            exception);
                }
            }
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

    public WorldRuntimeService worldRuntimeService() {
        return worldRuntimeService;
    }

    public WorldCreationService worldCreationService() {
        return worldCreationService;
    }

    public WorldTeleportService worldTeleportService() {
        return worldTeleportService;
    }

    public WorldSettingsService worldSettingsService() {
        return worldSettingsService;
    }
}
