package com.halokaryamedia.lazybuilder;

import com.halokaryamedia.lazybuilder.world.WorldManager;
import com.halokaryamedia.lazybuilder.world.paper.BuildPerformanceController;
import com.halokaryamedia.lazybuilder.world.paper.LocalControlImportUploadService;
import com.halokaryamedia.lazybuilder.world.paper.PaperLocalControlServer;
import com.halokaryamedia.lazybuilder.world.paper.PaperMainThreadDispatcher;
import com.halokaryamedia.lazybuilder.world.paper.PaperMapActionPayloadAdapter;
import com.halokaryamedia.lazybuilder.world.paper.PaperTransferPayloadAdapter;
import com.halokaryamedia.lazybuilder.world.paper.PaperWorldControlPayloadAdapter;
import com.halokaryamedia.lazybuilder.world.paper.WorldHeavyOperationOrchestrator;
import com.halokaryamedia.lazybuilder.world.task.WorldTaskRegistry;
import com.halokaryamedia.lazybuilder.world.task.WorldTaskRunner;
import org.bukkit.plugin.java.JavaPlugin;

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

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.worldManager = new WorldManager(this);
        this.worldTaskRegistry = new WorldTaskRegistry();
        this.worldTaskRunner = new WorldTaskRunner(worldTaskRegistry);
        this.worldManager.start();

        this.buildPerformanceController = new BuildPerformanceController(this, worldManager.worldRegistry());
        this.buildPerformanceController.start();

        PaperMainThreadDispatcher mainThread = new PaperMainThreadDispatcher(this);
        WorldHeavyOperationOrchestrator heavyOperations = new WorldHeavyOperationOrchestrator(
                mainThread,
                worldManager.worldCloneService(),
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
                worldManager.worldRuntimeService(),
                worldManager.worldTeleportService(),
                worldManager.worldLifecycleService(),
                worldManager.worldSettingsService(),
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
                worldManager.worldRuntimeService(),
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
        getLogger().info("World-Manager enabled.");
    }

    @Override
    public void onDisable() {
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
