package com.halokaryamedia.lazybuilder.world;

import com.halokaryamedia.lazybuilder.world.application.BuildReadyPolicy;
import com.halokaryamedia.lazybuilder.world.application.WorldBackupService;
import com.halokaryamedia.lazybuilder.world.application.WorldCloneService;
import com.halokaryamedia.lazybuilder.world.application.WorldCreationService;
import com.halokaryamedia.lazybuilder.world.application.WorldDeleteService;
import com.halokaryamedia.lazybuilder.world.application.WorldExportService;
import com.halokaryamedia.lazybuilder.world.application.WorldImportService;
import com.halokaryamedia.lazybuilder.world.application.WorldLifecycleService;
import com.halokaryamedia.lazybuilder.world.application.WorldLocationGateway;
import com.halokaryamedia.lazybuilder.world.application.WorldLocationTeleportService;
import com.halokaryamedia.lazybuilder.world.application.WorldOperationCoordinator;
import com.halokaryamedia.lazybuilder.world.application.WorldRuntimeGateway;
import com.halokaryamedia.lazybuilder.world.application.WorldRuntimeService;
import com.halokaryamedia.lazybuilder.world.application.WorldRuntimeState;
import com.halokaryamedia.lazybuilder.world.application.WorldRuntimeStateRegistry;
import com.halokaryamedia.lazybuilder.world.application.WorldSettingsService;
import com.halokaryamedia.lazybuilder.world.application.WorldTeleportService;
import com.halokaryamedia.lazybuilder.world.conversion.ChunkerCliAdapter;
import com.halokaryamedia.lazybuilder.world.conversion.ConversionJobCoordinator;
import com.halokaryamedia.lazybuilder.world.conversion.ConversionRuntimePolicy;
import com.halokaryamedia.lazybuilder.world.conversion.ConversionRuntimeStore;
import com.halokaryamedia.lazybuilder.world.conversion.ConversionUpdateService;
import com.halokaryamedia.lazybuilder.world.conversion.ConverterAdapter;
import com.halokaryamedia.lazybuilder.world.conversion.GitHubChunkerReleaseSource;
import com.halokaryamedia.lazybuilder.world.conversion.LocalConversionRuntimeStore;
import com.halokaryamedia.lazybuilder.world.conversion.OnDemandProcessRunner;
import com.halokaryamedia.lazybuilder.world.files.LocalWorldBackupStore;
import com.halokaryamedia.lazybuilder.world.files.LocalWorldExportArtifactStore;
import com.halokaryamedia.lazybuilder.world.files.LocalWorldFileRepository;
import com.halokaryamedia.lazybuilder.world.files.LocalWorldImportArtifactStore;
import com.halokaryamedia.lazybuilder.world.files.WorldBackupStore;
import com.halokaryamedia.lazybuilder.world.files.WorldExportArtifactStore;
import com.halokaryamedia.lazybuilder.world.files.WorldFileRepository;
import com.halokaryamedia.lazybuilder.world.files.WorldImportArtifactStore;
import com.halokaryamedia.lazybuilder.world.paper.PaperWorldLocationGateway;
import com.halokaryamedia.lazybuilder.world.paper.PaperWorldRuntimeGateway;
import com.halokaryamedia.lazybuilder.world.registry.WorldLifecycle;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistryPersistence;
import com.halokaryamedia.lazybuilder.world.registry.YamlWorldRegistryPersistence;
import com.halokaryamedia.lazybuilder.world.transfer.TransferPolicy;
import com.halokaryamedia.lazybuilder.world.transfer.TransferSessionService;
import com.halokaryamedia.lazybuilder.world.transfer.TransferWireProtocol;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

/** Canonical server-side owner for World Manager runtime coordination. */
public final class WorldManager {
    private final JavaPlugin plugin;
    private final WorldStorageLayout storageLayout;
    private final ConversionRuntimePolicy conversionRuntimePolicy;
    private final ConversionRuntimeStore conversionRuntimeStore;
    private final ConversionJobCoordinator conversionJobCoordinator;
    private final ConverterAdapter converterAdapter;
    private final ConversionUpdateService conversionUpdateService;
    private final TransferPolicy transferPolicy;
    private final TransferSessionService transferSessionService;
    private final BuildReadyPolicy buildReadyPolicy;
    private final WorldRegistry worldRegistry;
    private final WorldRegistryPersistence registryPersistence;
    private final WorldRuntimeStateRegistry runtimeStates;
    private final WorldRuntimeGateway runtimeGateway;
    private final WorldLocationGateway locationGateway;
    private final WorldRuntimeService worldRuntimeService;
    private final WorldCreationService worldCreationService;
    private final WorldTeleportService worldTeleportService;
    private final WorldLocationTeleportService worldLocationTeleportService;
    private final WorldSettingsService worldSettingsService;
    private final WorldOperationCoordinator worldOperationCoordinator;
    private final WorldFileRepository worldFileRepository;
    private final WorldBackupStore worldBackupStore;
    private final WorldExportArtifactStore worldExportArtifactStore;
    private final WorldImportArtifactStore worldImportArtifactStore;
    private final WorldLifecycleService worldLifecycleService;
    private final WorldCloneService worldCloneService;
    private final WorldBackupService worldBackupService;
    private final WorldDeleteService worldDeleteService;
    private final WorldExportService worldExportService;
    private final WorldImportService worldImportService;

    public WorldManager(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.storageLayout = WorldStorageLayout.resolve(
                plugin.getServer().getWorldContainer().toPath(),
                plugin.getDataFolder().toPath(),
                System.getenv(WorldStorageLayout.WORKSPACE_ENV)
        );
        this.storageLayout.ensureDirectories();
        this.conversionRuntimePolicy = ConversionRuntimePolicy.defaults();
        this.buildReadyPolicy = BuildReadyPolicy.defaults();
        this.worldRegistry = new WorldRegistry();
        this.runtimeStates = new WorldRuntimeStateRegistry();
        this.worldOperationCoordinator = new WorldOperationCoordinator();
        this.conversionJobCoordinator = new ConversionJobCoordinator();

        this.registryPersistence = new YamlWorldRegistryPersistence(storageLayout.registryPath());
        this.worldFileRepository = new LocalWorldFileRepository(
                storageLayout.worldsRoot(),
                storageLayout.workRoot()
        );
        this.worldBackupStore = new LocalWorldBackupStore(storageLayout.backupsRoot());
        this.worldExportArtifactStore = new LocalWorldExportArtifactStore(storageLayout.exportsRoot());
        long maxImportFiles = Math.max(1L, plugin.getConfig().getLong("world-manager.import.max-files", 200_000L));
        long maxImportMb = Math.max(1L, plugin.getConfig().getLong("world-manager.import.max-uncompressed-mb", 65_536L));
        this.worldImportArtifactStore = new LocalWorldImportArtifactStore(
                storageLayout.importsRoot(), maxImportFiles, Math.multiplyExact(maxImportMb, 1024L * 1024L)
        );

        int configuredTransferChunkBytes = plugin.getConfig().getInt(
                "world-manager.transfer.chunk-bytes", TransferWireProtocol.MAX_CHUNK_BYTES);
        int transferChunkBytes = Math.min(
                TransferWireProtocol.MAX_CHUNK_BYTES,
                Math.max(1024, configuredTransferChunkBytes)
        );
        long maxUploadMb = Math.max(1L,
                plugin.getConfig().getLong("world-manager.transfer.max-upload-mb", 16_384L));
        long transferIdleSeconds = Math.max(30L,
                plugin.getConfig().getLong("world-manager.transfer.session-idle-seconds", 300L));
        this.transferPolicy = new TransferPolicy(
                transferChunkBytes,
                Math.multiplyExact(maxUploadMb, 1024L * 1024L),
                1,
                2,
                Duration.ofSeconds(transferIdleSeconds)
        );
        this.transferSessionService = new TransferSessionService(
                storageLayout.importsRoot(), storageLayout.exportsRoot(), storageLayout.transferRoot(), transferPolicy
        );

        this.conversionRuntimeStore = new LocalConversionRuntimeStore(storageLayout.conversionRoot());
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
                storageLayout.conversionRoot().resolve("downloads"),
                Clock.systemUTC()
        );

        this.runtimeGateway = new PaperWorldRuntimeGateway(
                plugin.getServer(),
                () -> plugin.getConfig().getString("world-manager.fallback-world", "")
        );
        this.locationGateway = new PaperWorldLocationGateway(plugin.getServer());
        this.worldRuntimeService = new WorldRuntimeService(worldRegistry, runtimeStates, runtimeGateway);
        this.worldCreationService = new WorldCreationService(
                worldRegistry, registryPersistence, runtimeGateway, runtimeStates, buildReadyPolicy
        );
        this.worldTeleportService = new WorldTeleportService(worldRegistry, worldRuntimeService, runtimeGateway);
        this.worldLocationTeleportService = new WorldLocationTeleportService(
                worldRegistry, worldRuntimeService, locationGateway
        );
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
        this.worldBackupService = new WorldBackupService(
                worldRegistry, worldRuntimeService, runtimeStates, worldOperationCoordinator,
                worldFileRepository, worldBackupStore
        );
        this.worldDeleteService = new WorldDeleteService(
                worldRegistry, registryPersistence, worldRuntimeService, runtimeStates,
                worldOperationCoordinator, worldFileRepository,
                world -> {
                    String configured = plugin.getConfig().getString("world-manager.fallback-world", "");
                    if (configured != null && !configured.isBlank()) {
                        return world.folderName().equals(configured.strip());
                    }
                    return !plugin.getServer().getWorlds().isEmpty()
                            && world.folderName().equals(plugin.getServer().getWorlds().get(0).getName());
                }
        );
        this.worldExportService = new WorldExportService(
                worldRegistry, worldRuntimeService, runtimeStates, worldOperationCoordinator,
                worldFileRepository, worldExportArtifactStore, conversionRuntimeStore,
                conversionUpdateService, converterAdapter, conversionJobCoordinator
        );
        this.worldImportService = new WorldImportService(
                worldRegistry, registryPersistence, runtimeStates, worldFileRepository,
                worldImportArtifactStore, conversionRuntimeStore, conversionUpdateService,
                converterAdapter, conversionJobCoordinator
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

        // Reconcile Paper-loaded managed worlds with the registry once at startup.
        // A managed world marked autoLoad=false should not keep chunk/entity ticking merely
        // because Paper happened to load it before this plugin. The runtime gateway keeps the
        // configured/default fallback world loaded, so this cannot remove the server's safety world.
        for (WorldRecord world : worldRegistry.all()) {
            if (world.lifecycle() == WorldLifecycle.ACTIVE && !world.autoLoad()
                    && runtimeStates.get(world.id()) == WorldRuntimeState.LOADED) {
                try {
                    worldRuntimeService.unload(world.id());
                    plugin.getLogger().fine("Unloaded idle managed world " + world.folderName()
                            + " because autoLoad is disabled.");
                } catch (RuntimeException exception) {
                    plugin.getLogger().fine("Kept managed world " + world.folderName()
                            + " loaded because Paper requires it as an active/fallback world: "
                            + exception.getMessage());
                }
            }
        }

        plugin.getLogger().fine("World Manager ready with " + worldRegistry.size()
                + " managed worlds using " + (storageLayout.canonical() ? "canonical" : "legacy-compatible")
                + " storage layout.");
    }

    public void stop() {
        // Transfer/conversion/file workers are request-bound; there is no idle process to stop.
    }

    public WorldStorageLayout storageLayout() { return storageLayout; }
    public ConversionRuntimePolicy conversionRuntimePolicy() { return conversionRuntimePolicy; }
    public ConversionRuntimeStore conversionRuntimeStore() { return conversionRuntimeStore; }
    public ConversionJobCoordinator conversionJobCoordinator() { return conversionJobCoordinator; }
    public ConverterAdapter converterAdapter() { return converterAdapter; }
    public ConversionUpdateService conversionUpdateService() { return conversionUpdateService; }
    public TransferPolicy transferPolicy() { return transferPolicy; }
    public TransferSessionService transferSessionService() { return transferSessionService; }
    public BuildReadyPolicy buildReadyPolicy() { return buildReadyPolicy; }
    public WorldRegistry worldRegistry() { return worldRegistry; }
    public WorldRuntimeService worldRuntimeService() { return worldRuntimeService; }
    public WorldCreationService worldCreationService() { return worldCreationService; }
    public WorldTeleportService worldTeleportService() { return worldTeleportService; }
    public WorldLocationTeleportService worldLocationTeleportService() { return worldLocationTeleportService; }
    public WorldSettingsService worldSettingsService() { return worldSettingsService; }
    public WorldOperationCoordinator worldOperationCoordinator() { return worldOperationCoordinator; }
    public WorldFileRepository worldFileRepository() { return worldFileRepository; }
    public WorldBackupStore worldBackupStore() { return worldBackupStore; }
    public WorldExportArtifactStore worldExportArtifactStore() { return worldExportArtifactStore; }
    public WorldImportArtifactStore worldImportArtifactStore() { return worldImportArtifactStore; }
    public WorldLifecycleService worldLifecycleService() { return worldLifecycleService; }
    public WorldCloneService worldCloneService() { return worldCloneService; }
    public WorldBackupService worldBackupService() { return worldBackupService; }
    public WorldDeleteService worldDeleteService() { return worldDeleteService; }
    public WorldExportService worldExportService() { return worldExportService; }
    public WorldImportService worldImportService() { return worldImportService; }
}
