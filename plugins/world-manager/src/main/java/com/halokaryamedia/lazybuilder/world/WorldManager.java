package com.halokaryamedia.lazybuilder.world;

import com.halokaryamedia.lazybuilder.world.application.BuildReadyPolicy;
import com.halokaryamedia.lazybuilder.world.application.WorldBackupService;
import com.halokaryamedia.lazybuilder.world.application.WorldCreationService;
import com.halokaryamedia.lazybuilder.world.application.WorldDeleteService;
import com.halokaryamedia.lazybuilder.world.application.WorldDuplicateService;
import com.halokaryamedia.lazybuilder.world.application.WorldExportService;
import com.halokaryamedia.lazybuilder.world.application.WorldImportService;
import com.halokaryamedia.lazybuilder.world.application.WorldLifecycleService;
import com.halokaryamedia.lazybuilder.world.application.WorldLocationGateway;
import com.halokaryamedia.lazybuilder.world.application.WorldLocationTeleportService;
import com.halokaryamedia.lazybuilder.world.application.WorldOperationCoordinator;
import com.halokaryamedia.lazybuilder.world.application.WorldProtectionPolicy;
import com.halokaryamedia.lazybuilder.world.application.WorldRuntimeGateway;
import com.halokaryamedia.lazybuilder.world.application.WorldRuntimeService;
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
import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import com.halokaryamedia.lazybuilder.world.registry.WorldKind;
import com.halokaryamedia.lazybuilder.world.registry.WorldLifecycle;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistryPersistence;
import com.halokaryamedia.lazybuilder.world.registry.YamlWorldRegistryPersistence;
import com.halokaryamedia.lazybuilder.world.transfer.TransferPolicy;
import com.halokaryamedia.lazybuilder.world.transfer.TransferSessionService;
import com.halokaryamedia.lazybuilder.world.transfer.TransferWireProtocol;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

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
    private final WorldRuntimeGateway runtimeGateway;
    private final WorldLocationGateway locationGateway;
    private final WorldProtectionPolicy worldProtectionPolicy;
    private final WorldOperationCoordinator worldOperationCoordinator;
    private final WorldRuntimeService worldRuntimeService;
    private final WorldCreationService worldCreationService;
    private final WorldTeleportService worldTeleportService;
    private final WorldLocationTeleportService worldLocationTeleportService;
    private final WorldSettingsService worldSettingsService;
    private final WorldFileRepository worldFileRepository;
    private final WorldBackupStore worldBackupStore;
    private final WorldExportArtifactStore worldExportArtifactStore;
    private final WorldImportArtifactStore worldImportArtifactStore;
    private final WorldLifecycleService worldLifecycleService;
    private final WorldDuplicateService worldDuplicateService;
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
        this.worldOperationCoordinator = new WorldOperationCoordinator();
        this.conversionJobCoordinator = new ConversionJobCoordinator();

        this.registryPersistence = new YamlWorldRegistryPersistence(storageLayout.registryPath());
        this.worldFileRepository = new LocalWorldFileRepository(
                storageLayout.worldsRoot(), storageLayout.workRoot());
        this.worldBackupStore = new LocalWorldBackupStore(storageLayout.backupsRoot());
        this.worldExportArtifactStore = new LocalWorldExportArtifactStore(storageLayout.exportsRoot());

        long maxImportFiles = Math.max(1L, plugin.getConfig().getLong("world-manager.import.max-files", 100_000L));
        long maxImportMb = Math.max(1L, plugin.getConfig().getLong("world-manager.import.max-uncompressed-mb", 16_384L));
        this.worldImportArtifactStore = new LocalWorldImportArtifactStore(
                storageLayout.importsRoot(), maxImportFiles, Math.multiplyExact(maxImportMb, 1024L * 1024L));

        int configuredTransferChunkBytes = plugin.getConfig().getInt(
                "world-manager.transfer.chunk-bytes", TransferWireProtocol.MAX_CHUNK_BYTES);
        int transferChunkBytes = Math.min(
                TransferWireProtocol.MAX_CHUNK_BYTES,
                Math.max(1024, configuredTransferChunkBytes));
        long maxUploadMb = Math.max(1L,
                plugin.getConfig().getLong("world-manager.transfer.max-upload-mb", 8_192L));
        long transferIdleSeconds = Math.max(30L,
                plugin.getConfig().getLong("world-manager.transfer.session-idle-seconds", 300L));
        this.transferPolicy = new TransferPolicy(
                transferChunkBytes,
                Math.multiplyExact(maxUploadMb, 1024L * 1024L),
                1,
                2,
                Duration.ofSeconds(transferIdleSeconds));
        this.transferSessionService = new TransferSessionService(
                storageLayout.importsRoot(), storageLayout.exportsRoot(), storageLayout.transferRoot(), transferPolicy);

        this.conversionRuntimeStore = new LocalConversionRuntimeStore(storageLayout.conversionRoot());
        int conversionHeapMb = plugin.getConfig().getInt("world-manager.conversion.max-heap-mb", 3072);
        long conversionTimeoutMinutes = plugin.getConfig().getLong("world-manager.conversion.timeout-minutes", 60L);
        this.converterAdapter = new ChunkerCliAdapter(
                ChunkerCliAdapter.currentJavaExecutable(),
                conversionHeapMb,
                Duration.ofSeconds(30),
                Duration.ofMinutes(Math.max(1L, conversionTimeoutMinutes)),
                new OnDemandProcessRunner());
        GitHubChunkerReleaseSource releaseSource = new GitHubChunkerReleaseSource();
        this.conversionUpdateService = new ConversionUpdateService(
                conversionRuntimePolicy,
                conversionRuntimeStore,
                releaseSource,
                releaseSource,
                converterAdapter,
                storageLayout.conversionRoot().resolve("downloads"),
                Clock.systemUTC());

        this.runtimeGateway = new PaperWorldRuntimeGateway(
                plugin.getServer(),
                () -> plugin.getConfig().getString("world-manager.fallback-world", ""));
        this.locationGateway = new PaperWorldLocationGateway(plugin.getServer());
        this.worldProtectionPolicy = new WorldProtectionPolicy(
                () -> plugin.getConfig().getString("world-manager.fallback-world", ""),
                () -> plugin.getServer().getWorlds().isEmpty()
                        ? null
                        : plugin.getServer().getWorlds().get(0).getName()
        );

        this.worldRuntimeService = new WorldRuntimeService(
                worldRegistry,
                runtimeGateway,
                worldOperationCoordinator,
                world -> {
                    World loaded = plugin.getServer().getWorld(world.folderName());
                    return loaded != null && !loaded.getPlayers().isEmpty();
                });
        this.worldCreationService = new WorldCreationService(
                worldRegistry, registryPersistence, runtimeGateway, worldFileRepository, buildReadyPolicy);
        this.worldTeleportService = new WorldTeleportService(
                worldRegistry, worldRuntimeService, runtimeGateway, worldOperationCoordinator);
        this.worldLocationTeleportService = new WorldLocationTeleportService(
                worldRegistry, worldRuntimeService, locationGateway, worldOperationCoordinator);
        this.worldSettingsService = new WorldSettingsService(
                worldRegistry, registryPersistence, worldRuntimeService, runtimeGateway, buildReadyPolicy);
        this.worldLifecycleService = new WorldLifecycleService(
                worldRegistry, registryPersistence, worldRuntimeService,
                worldOperationCoordinator, worldProtectionPolicy);
        this.worldDuplicateService = new WorldDuplicateService(
                worldRegistry, registryPersistence, worldRuntimeService,
                worldOperationCoordinator, worldFileRepository);
        this.worldBackupService = new WorldBackupService(
                worldRegistry, worldRuntimeService, worldOperationCoordinator,
                worldFileRepository, worldBackupStore);
        this.worldDeleteService = new WorldDeleteService(
                worldRegistry, registryPersistence, worldRuntimeService,
                worldOperationCoordinator, worldFileRepository, worldProtectionPolicy);
        this.worldExportService = new WorldExportService(
                worldRegistry, worldRuntimeService, worldOperationCoordinator,
                worldFileRepository, worldExportArtifactStore, conversionRuntimeStore,
                conversionUpdateService, converterAdapter, conversionJobCoordinator);
        this.worldImportService = new WorldImportService(
                worldRegistry, registryPersistence, worldFileRepository,
                worldImportArtifactStore, conversionRuntimeStore, conversionUpdateService,
                converterAdapter, conversionJobCoordinator);
    }

    public void start() {
        try {
            int recoveredWorkspaces = worldFileRepository.recoverTransientWorkspaces();
            if (recoveredWorkspaces > 0) {
                plugin.getLogger().info("Recovered " + recoveredWorkspaces
                        + " interrupted transient World Manager workspace"
                        + (recoveredWorkspaces == 1 ? "" : "s") + ".");
            }
            for (WorldRecord world : registryPersistence.load()) {
                worldRegistry.register(world);
            }

            WorldFileRepository.CreateRecovery createRecovery =
                    worldFileRepository.recoverCreateTransactions(worldRegistry.all());
            if (createRecovery.committed() > 0 || createRecovery.rolledBack() > 0 || createRecovery.preserved() > 0) {
                plugin.getLogger().info("Create recovery: committed=" + createRecovery.committed()
                        + ", rolledBack=" + createRecovery.rolledBack()
                        + ", preserved=" + createRecovery.preserved() + ".");
            }

            WorldFileRepository.DeleteRecovery deleteRecovery =
                    worldFileRepository.recoverDeleteWorkspaces(worldRegistry.all());
            if (deleteRecovery.restored() > 0 || deleteRecovery.discarded() > 0 || deleteRecovery.preserved() > 0) {
                plugin.getLogger().info("Delete recovery: restored=" + deleteRecovery.restored()
                        + ", discarded=" + deleteRecovery.discarded()
                        + ", preserved=" + deleteRecovery.preserved() + ".");
            }

            requireResolvedTransactionRecovery(createRecovery, deleteRecovery);

            WorldFileRepository.PublishRecovery publishRecovery =
                    worldFileRepository.recoverPublishedWorlds(worldRegistry.all());
            if (publishRecovery.finalized() > 0 || publishRecovery.discarded() > 0) {
                plugin.getLogger().info("Publish recovery: finalized=" + publishRecovery.finalized()
                        + ", discarded=" + publishRecovery.discarded() + ".");
            }

            WorldFileRepository.ManagedWorldAudit audit =
                    worldFileRepository.auditManagedWorldFolders(worldRegistry.all());
            if (!audit.healthy()) {
                throw new IllegalStateException("Managed world filesystem divergence; missing="
                        + audit.missingFolders() + ", unsafe=" + audit.unsafeFolders());
            }

            int reconciledArchivedWorlds = worldLifecycleService.reconcilePersistedRuntimeState();
            if (reconciledArchivedWorlds > 0) {
                plugin.getLogger().info("Unloaded " + reconciledArchivedWorlds
                        + " archived world" + (reconciledArchivedWorlds == 1 ? "" : "s")
                        + " to match persisted lifecycle state.");
            }

            discoverExistingWorlds();
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Failed to initialize LazyBuilder world registry", exception);
        }

        worldImportService.recoverPendingCommittedArtifactCleanup();

        plugin.getLogger().fine("World Manager ready with " + worldRegistry.size()
                + " managed worlds using " + (storageLayout.canonical() ? "canonical" : "legacy-compatible")
                + " storage layout.");
    }

    static void requireResolvedTransactionRecovery(
            WorldFileRepository.CreateRecovery createRecovery,
            WorldFileRepository.DeleteRecovery deleteRecovery
    ) {
        Objects.requireNonNull(createRecovery, "createRecovery");
        Objects.requireNonNull(deleteRecovery, "deleteRecovery");
        if (createRecovery.preserved() == 0 && deleteRecovery.preserved() == 0) return;

        throw new IllegalStateException(
                "World Manager found ambiguous preserved transaction state "
                        + "(create=" + createRecovery.preserved()
                        + ", delete=" + deleteRecovery.preserved()
                        + "). Automatic world discovery is blocked until the preserved "
                        + "recovery evidence is reconciled manually."
        );
    }

    private void discoverExistingWorlds() throws IOException {
        Path worldsRoot = storageLayout.worldsRoot().toAbsolutePath().normalize();
        if (Files.notExists(worldsRoot)) return;
        if (!Files.isDirectory(worldsRoot) || Files.isSymbolicLink(worldsRoot)) {
            throw new IOException("World discovery root is unsafe: " + worldsRoot);
        }
        Path canonicalRoot = worldsRoot.toRealPath();

        List<Path> candidates;
        try (var paths = Files.list(worldsRoot)) {
            candidates = paths
                    .filter(path -> isDirectContainedWorldDirectory(canonicalRoot, path))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
        }

        int discovered = 0;
        String defaultGameMode = plugin.getServer().getDefaultGameMode().name();
        for (Path candidate : candidates) {
            String folderName = candidate.getFileName().toString();
            if (worldRegistry.findByFolderName(folderName).isPresent()) continue;

            Path levelDat = candidate.resolve("level.dat");
            if (!Files.isRegularFile(levelDat) || Files.isSymbolicLink(levelDat)) continue;

            World loaded = plugin.getServer().getWorld(folderName);
            if (loaded != null && loaded.getEnvironment() != World.Environment.NORMAL) continue;
            if (loaded == null && looksLikeDimensionFolder(worldsRoot, folderName)) continue;

            WorldRecord discoveredWorld = new WorldRecord(
                    WorldId.create(),
                    folderName,
                    folderName,
                    WorldKind.IMPORTED,
                    WorldLifecycle.ACTIVE,
                    defaultGameMode);
            worldRegistry.register(discoveredWorld);
            discovered++;
        }

        if (discovered > 0) {
            try {
                registryPersistence.save(worldRegistry.all());
            } catch (IOException | RuntimeException failure) {
                // Discovery is provisional until one registry commit succeeds.
                // Revert only worlds created by this pass so the in-memory authority
                // remains aligned with durable registry truth after a failed save.
                worldRegistry.all().stream()
                        .filter(world -> world.kind() == WorldKind.IMPORTED)
                        .filter(world -> candidates.stream().anyMatch(path ->
                                path.getFileName().toString().equalsIgnoreCase(world.folderName())))
                        .map(WorldRecord::id)
                        .toList()
                        .forEach(worldRegistry::remove);
                throw failure;
            }
            plugin.getLogger().info("Adopted " + discovered
                    + " existing Paper world" + (discovered == 1 ? "" : "s")
                    + " into the LazyBuilder registry.");
        }
    }

    static boolean isDirectContainedWorldDirectory(Path canonicalRoot, Path candidate) {
        Objects.requireNonNull(canonicalRoot, "canonicalRoot");
        Objects.requireNonNull(candidate, "candidate");
        try {
            if (!Files.isDirectory(candidate) || Files.isSymbolicLink(candidate)) return false;
            Path canonicalCandidate = candidate.toRealPath();
            return canonicalRoot.equals(canonicalCandidate.getParent());
        } catch (IOException ignored) {
            return false;
        }
    }

    private static boolean looksLikeDimensionFolder(Path worldsRoot, String folderName) {
        String normalized = folderName.toLowerCase(Locale.ROOT);
        String baseName;
        if (normalized.endsWith("_nether")) {
            baseName = folderName.substring(0, folderName.length() - "_nether".length());
        } else if (normalized.endsWith("_the_end")) {
            baseName = folderName.substring(0, folderName.length() - "_the_end".length());
        } else {
            return false;
        }
        return !baseName.isBlank() && Files.isRegularFile(worldsRoot.resolve(baseName).resolve("level.dat"));
    }

    public void stop() {
        worldImportService.retryPendingCommittedArtifactCleanup();
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
    public WorldProtectionPolicy worldProtectionPolicy() { return worldProtectionPolicy; }
    public WorldOperationCoordinator worldOperationCoordinator() { return worldOperationCoordinator; }
    public WorldFileRepository worldFileRepository() { return worldFileRepository; }
    public WorldBackupStore worldBackupStore() { return worldBackupStore; }
    public WorldExportArtifactStore worldExportArtifactStore() { return worldExportArtifactStore; }
    public WorldImportArtifactStore worldImportArtifactStore() { return worldImportArtifactStore; }
    public WorldLifecycleService worldLifecycleService() { return worldLifecycleService; }
    public WorldDuplicateService worldDuplicateService() { return worldDuplicateService; }
    public WorldBackupService worldBackupService() { return worldBackupService; }
    public WorldDeleteService worldDeleteService() { return worldDeleteService; }
    public WorldExportService worldExportService() { return worldExportService; }
    public WorldImportService worldImportService() { return worldImportService; }
}
