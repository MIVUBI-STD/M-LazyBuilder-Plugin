package com.halokaryamedia.lazybuilder;

import com.halokaryamedia.lazybuilder.world.WorldManager;
import com.halokaryamedia.lazybuilder.world.paper.PaperMapActionPayloadAdapter;
import com.halokaryamedia.lazybuilder.world.paper.PaperTransferPayloadAdapter;
import com.halokaryamedia.lazybuilder.world.paper.PaperWorldControlPayloadAdapter;
import org.bukkit.plugin.java.JavaPlugin;

public final class LazyBuilderPlugin extends JavaPlugin {
    private WorldManager worldManager;
    private PaperTransferPayloadAdapter transferPayloadAdapter;
    private PaperMapActionPayloadAdapter mapActionPayloadAdapter;
    private PaperWorldControlPayloadAdapter worldControlPayloadAdapter;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.worldManager = new WorldManager(this);
        this.worldManager.start();

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
                worldManager.worldCloneService(),
                worldManager.worldDeleteService(),
                worldManager.worldSettingsService(),
                worldManager.worldExportService(),
                worldManager.worldImportService()
        );
        this.worldControlPayloadAdapter.start();
        getLogger().info("LazyBuilder enabled.");
    }

    @Override
    public void onDisable() {
        if (worldControlPayloadAdapter != null) {
            worldControlPayloadAdapter.stop();
        }
        if (mapActionPayloadAdapter != null) {
            mapActionPayloadAdapter.stop();
        }
        if (transferPayloadAdapter != null) {
            transferPayloadAdapter.stop();
        }
        if (worldManager != null) {
            worldManager.stop();
        }
    }
}
