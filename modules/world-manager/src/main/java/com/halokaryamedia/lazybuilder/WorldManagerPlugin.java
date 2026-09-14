package com.halokaryamedia.lazybuilder;

import com.halokaryamedia.lazybuilder.world.WorldManager;
import com.halokaryamedia.lazybuilder.world.application.WorldIdleUnloadService;
import com.halokaryamedia.lazybuilder.world.paper.BuildPerformanceCommand;
import com.halokaryamedia.lazybuilder.world.paper.BuildPerformanceController;
import com.halokaryamedia.lazybuilder.world.paper.ChunkPregenerationController;
import com.halokaryamedia.lazybuilder.world.paper.LocalControlImportUploadService;
import com.halokaryamedia.lazybuilder.world.paper.PaperLocalControlServer;
import com.halokaryamedia.lazybuilder.world.paper.PaperMainThreadDispatcher;
import com.halokaryamedia.lazybuilder.world.paper.PaperMapActionPayloadAdapter;
import com.halokaryamedia.lazybuilder.world.paper.PaperTransferPayloadAdapter;
import com.halokaryamedia.lazybuilder.world.paper.PaperWorldControlPayloadAdapter;
import com.halokaryamedia.lazybuilder.world.paper.WorldHeavyOperationOrchestrator;
import com.halokaryamedia.lazybuilder.world.task.WorldTaskRegistry;
import com.halokaryamedia.lazybuilder.world.task.WorldTaskRunner;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;

/** Canonical Paper entry point and lifecycle owner for the World-Manager module. */
public final class WorldManagerPlugin extends JavaPlugin {
    private WorldManager worldManager;
    private WorldTaskRegistry worldTaskRegistry;
    private WorldTaskRunner worldTaskRunner;
    private BuildPerformanceController buildPerformanceController;
    private PaperTransferPayloadAdapter transferPayloadAdapter;
    private PaperMapActionPayloadAdapter mapActionPayloadAdapter;
    private PaperWorldControlPayloadAdapter worldControlPayloadAdapter;
    private PaperLocalControlServer localControlServer;
    private BukkitTask idleUnloadTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.worldManager = new WorldManager(this);
        this.worldTaskRegistry = new WorldTaskRegistry();
        this.worldTaskRunner = new WorldTaskRunner(worldTaskRegistry);
        this.worldManager.start();

        this.buildPerformanceController = new BuildPerformanceController(this, worldManager.worldRegistry());
        this.buildPerformanceController.start();

        long idleMinutes = Math.max(1L, getConfig().getLong("world-manager.idle-unload.minutes", 10L));
        boolean idleUnloadEnabled = getConfig().getBoolean("world-manager.idle-unload.enabled", true);
        if (idleUnloadEnabled) {
            WorldIdleUnloadService idleUnload = new WorldIdleUnloadService(
                    worldManager.worldRegistry(),
                    worldManager.worldRuntimeService(),
                    worldManager.worldOperationCoordinator(),
                    world -> getServer().getWorld(world.folderName()) != null,
                    world -> {
                        var loaded = getServer().getWorld(world.folderName());
                        return loaded != null && !loaded.getPlayers().isEmpty();
                    },
                    worldManager.worldProtectionPolicy(),
                    Duration.ofMinutes(idleMinutes)
            );
            this.idleUnloadTask = getServer().getScheduler().runTaskTimer(
                    this,
                    () -> idleUnload.tick(System.currentTimeMillis()),
                    20L * 30L,
                    20L * 30L
            );
        }

        ChunkPregenerationController pregeneration = new ChunkPregenerationController(
                getServer(), worldManager.worldRegistry());
        BuildPerformanceCommand performanceCommand = new BuildPerformanceCommand(getServer(), pregeneration);
        PluginCommand lazyperf = getCommand("lazyperf");
        if (lazyperf == null) {
            throw new IllegalStateException("lazyperf command is missing from plugin.yml");
        }
        lazyperf.setExecutor(performanceCommand);
        lazyperf.setTabCompleter(performanceCommand);

        PaperMainThreadDispatcher mainThread = new PaperMainThreadDispatcher(this);
        WorldHeavyOperationOrchestrator heavyOperations = new WorldHeavyOperationOrchestrator(
                mainThread,
                worldManager.worldDuplicateService(),
                worldManager.worldDeleteService(),
                worldManager.worldExportService(),
                worldManager.worldImportService()
        );

        this.transferPayloadAdapter = new PaperTransferPayloadAdapter(this, worldManager.transferSessionService());
        this.transferPayloadAdapter.start();

        this.mapActionPayloadAdapter = new PaperMapActionPayloadAdapter(
                this,
                worldManager.worldRegistry(),
                worldManager.worldLocationTeleportService(),
                worldManager.worldExportService()
        );
        this.mapActionPayloadAdapter.start();

        this.worldControlPayloadAdapter = new PaperWorldControlPayloadAdapter(
                this,
                worldManager.worldRegistry(),
                worldManager.worldCreationService(),
                worldManager.worldTeleportService(),
                worldManager.worldLifecycleService(),
                worldManager.worldSettingsService(),
                worldManager.worldExportService(),
                worldManager.worldImportService(),
                worldManager.conversionUpdateService(),
                heavyOperations
        );
        this.worldControlPayloadAdapter.start();

        LocalControlImportUploadService importUploads = new LocalControlImportUploadService(
                worldManager.transferSessionService(),
                worldManager.transferPolicy()
        );
        this.localControlServer = new PaperLocalControlServer(
                this,
                worldManager.worldRegistry(),
                worldManager.worldCreationService(),
                worldManager.worldSettingsService(),
                worldManager.worldLifecycleService(),
                worldManager.worldBackupService(),
                heavyOperations,
                importUploads,
                worldTaskRegistry,
                worldTaskRunner
        );
        this.localControlServer.start();
        getLogger().info("World-Manager enabled. Chunky integration: "
                + (pregeneration.available() ? "available" : "optional/not installed") + ".");
    }

    @Override
    public void onDisable() {
        if (idleUnloadTask != null) idleUnloadTask.cancel();
        if (localControlServer != null) localControlServer.stop();
        if (worldTaskRunner != null) worldTaskRunner.close();
        if (worldControlPayloadAdapter != null) worldControlPayloadAdapter.stop();
        if (mapActionPayloadAdapter != null) mapActionPayloadAdapter.stop();
        if (transferPayloadAdapter != null) transferPayloadAdapter.stop();
        if (buildPerformanceController != null) buildPerformanceController.stop();
        if (worldManager != null) worldManager.stop();
        getLogger().info("World-Manager disabled.");
    }
}
