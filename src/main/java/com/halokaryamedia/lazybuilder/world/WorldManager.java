package com.halokaryamedia.lazybuilder.world;

import com.halokaryamedia.lazybuilder.LazyBuilderPlugin;
import com.halokaryamedia.lazybuilder.world.application.BuildReadyPolicy;
import com.halokaryamedia.lazybuilder.world.application.WorldCloneService;
import com.halokaryamedia.lazybuilder.world.application.WorldCreationService;
import com.halokaryamedia.lazybuilder.world.application.WorldDeleteService;
import com.halokaryamedia.lazybuilder.world.application.WorldLifecycleService;
import com.halokaryamedia.lazybuilder.world.application.WorldOperationCoordinator;
import com.halokaryamedia.lazybuilder.world.application.WorldRuntimeGateway;
import com.halokaryamedia.lazybuilder.world.application.WorldRuntimeService;
import com.halokaryamedia.lazybuilder.world.application.WorldRuntimeState;
import com.halokaryamedia.lazybuilder.world.application.WorldRuntimeStateRegistry;
import com.halokaryamedia.lazybuilder.world.application.WorldSettingsService;
import com.halokaryamedia.lazybuilder.world.application.WorldTeleportService;
import com.halokaryamedia.lazybuilder.world.conversion.ChunkerCliAdapter;
import com.halokaryamedia.lazybuilder.world.conversion.ConversionJobCoordinator;
import com.halokaryamedia.lazybuilder.world.conversion.ConversionReleaseSource;
import com.halokaryamedia.lazybuilder.world.conversion.ConversionRuntimePolicy;
import com.halokaryamedia.lazybuilder.world.conversion.ConversionRuntimeStore;
import com.halokaryamedia.lazybuilder.world.conversion.ConversionUpdateService;
import com.halokaryamedia.lazybuilder.world.conversion.ConverterAdapter;
import com.halokaryamedia.lazybuilder.world.conversion.GitHubChunkerReleaseSource;
import com.halokaryamedia.lazybuilder.world.conversion.LocalConversionRuntimeStore;
import com.halokaryamedia.lazybuilder.world.conversion.OnDemandProcessRunner;
import com.halokaryamedia.lazybuilder.world.files.LocalWorldFileRepository;
import com.halokaryamedia.lazybuilder.world.files.WorldFileRepository;
import com.halokaryamedia.lazybuilder.world.paper.PaperWorldRuntimeGateway;
import com.halokaryamedia.lazybuilder.world.registry.WorldLifecycle;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistryPersistence;
import com.halokaryamedia.lazybuilder.world.registry.YamlWorldRegistryPersistence;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

/** Canonical server-side owner for World Manager runtime coordination. */
public final class WorldManager {
    private final LazyBuilderPlugin plugin;
    private final ConversionRuntimePolicy conversionRuntimePolicy;
    private final ConversionRuntimeStore conversionRuntimeStore;
    private final ConversionJobCoordinator conversionJobCoordinator;
    private final ConverterAdapter converterAdapter;
    private final ConversionUpdateService conversionUpdateService;
    private final BuildReadyPolicy buildReadyPolicy;
    private final WorldRegistry worldRegistry;
    private final WorldRegistryPersistence registryPersistence;
    private final WorldRuntimeStateRegistry runtimeStates;
    private final WorldRuntimeGateway runtimeGateway;
    private final WorldRuntimeService worldRuntimeService;
    private final WorldCreationService worldCreationService;
    private final WorldTeleportService worldTeleportService;
    private final WorldSettingsService worldSettingsService;
    private final WorldOperationCoordinator worldOperationCoordinator;
    private final WorldFileRepository worldFileRepository;
    private final WorldLifecycleService worldLifecycleService;
    private final WorldCloneService worldCloneService;
    private final WorldDeleteService worldDeleteService;

    public WorldManager(LazyBuilderPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.conversionRuntimePolicy = ConversionRuntimePolicy.defaults();
        this.buildReadyPolicy = BuildReadyPolicy.defaults();
        this.worldRegistry = new WorldRegistry();
        this.runtimeStates = new WorldRuntimeStateRegistry();
        this.worldOperationCoordinator = new WorldOperationCoordinator();
        this.conversionJobCoordinator = new ConversionJobCoordinator();

        Path worldDataRoot = plugin.getDataFolder().toPath().resolve("world");
        Path registryPath = worldDataRoot.resolve("registry.yml");
        Path conversionRoot = worldDataRoot.resolve("runtime").resolve("converter");
        this.registryPersistence = new YamlWorldRegistryPersistence(registryPath);
        this.worldFileRepository = new LocalWorldFileRepository(
                plugin.getServer().getWorldContainer().toPath(),
                worldDataRoot.resolve("work")
        );
        this.conversionRuntimeStore = new LocalConversionRuntimeStore(conversionRoot);

        int conversionHeapMb = plugin.getConfig().getInt("world-manager.conversion.max-heap-mb", 3072);
        long conversionTimeoutMinutes = plugin.getConfig().getLong("world-manager.conversion.timeout-minutes", 60L);
        this.converterAdapter = new ChunkerCliAdapter(
                ChunkerCliAdapter.currentJavaExecutable(),
                conversionHeapMb,
                Duration.ofSeconds(30),
                Duration.ofMinutes(Math.max(1L, conversionTimeoutMinutes)),
                new OnDemandProcessRunner()
        );
        GitHubChunkerReleaseSource releaseSource = new GitHubChunkerReleaseSource();
        this.conversionUpdateService = new ConversionUpdateService(
                conversionRuntimePolicy,
                conversionRuntimeStore,
                releaseSource,
                releaseSource,
                converterAdapter,
                conversionRoot.resolve("downloads"),
                Clock.systemUTC()
        );

        this.runtimeGateway = new PaperWorldRuntimeGateway(
                plugin.getServer(),
                () -> plugin.getConfig().getString("world-manager.fallback-world", "")
        );
        this.worldRuntimeService = new WorldRuntimeService(worldRegistry, runtimeStates, runtimeGateway);
        this.worldCreationService = new WorldCreationService(
                worldRegistry, registryPersistence, runtimeGateway, runtimeStates, buildReadyPolicy
        );
        this.worldTeleportService = new WorldTeleportService(worldRegistry, worldRuntimeService, runtimeGateway);
        this.worldSettingsService = new WorldSettingsService(
                worldRegistry, registryPersistence, worldRuntimeService, runtimeGateway, buildReadyPolicy
        );
        this.worldLifecycleService = new WorldLifecycleService(
                worldRegistry, registryPersistence, worldRuntimeService, runtimeStates, worldOperationCoordinator
        );
        this.worldCloneService = new WorldCloneService(
                worldRegistry, registryPersistence, worldRuntimeService, runtimeStates,
                worldOperationCoordinator, worldFileRepository
        );
        this.worldDeleteService = new WorldDeleteService(
                worldRegistry, registryPersistence, worldRuntimeService, runtimeStates,
                worldOperationCoordinator, worldFileRepository
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
        // Conversion/file workers are request-bound; there is no idle process to stop.
    }

    public ConversionRuntimePolicy conversionRuntimePolicy() { return conversionRuntimePolicy; }
    public ConversionRuntimeStore conversionRuntimeStore() { return conversionRuntimeStore; }
    public ConversionJobCoordinator conversionJobCoordinator() { return conversionJobCoordinator; }
    public ConverterAdapter converterAdapter() { return converterAdapter; }
    public ConversionUpdateService conversionUpdateService() { return conversionUpdateService; }
    public BuildReadyPolicy buildReadyPolicy() { return buildReadyPolicy; }
    public WorldRegistry worldRegistry() { return worldRegistry; }
    public WorldRuntimeService worldRuntimeService() { return worldRuntimeService; }
    public WorldCreationService worldCreationService() { return worldCreationService; }
    public WorldTeleportService worldTeleportService() { return worldTeleportService; }
    public WorldSettingsService worldSettingsService() { return worldSettingsService; }
    public WorldOperationCoordinator worldOperationCoordinator() { return worldOperationCoordinator; }
    public WorldFileRepository worldFileRepository() { return worldFileRepository; }
    public WorldLifecycleService worldLifecycleService() { return worldLifecycleService; }
    public WorldCloneService worldCloneService() { return worldCloneService; }
    public WorldDeleteService worldDeleteService() { return worldDeleteService; }
}
