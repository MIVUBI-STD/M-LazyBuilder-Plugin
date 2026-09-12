package com.halokaryamedia.lazybuilder;

import com.halokaryamedia.lazybuilder.world.WorldManager;
import com.halokaryamedia.lazybuilder.world.paper.PaperMapActionPayloadAdapter;
import com.halokaryamedia.lazybuilder.world.paper.PaperTransferPayloadAdapter;
import org.bukkit.plugin.java.JavaPlugin;

public final class LazyBuilderPlugin extends JavaPlugin {
    private WorldManager worldManager;
    private PaperTransferPayloadAdapter transferPayloadAdapter;
    private PaperMapActionPayloadAdapter mapActionPayloadAdapter;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.worldManager = new WorldManager(this);
        this.worldManager.start();

        this.transferPayloadAdapter = new PaperTransferPayloadAdapter(this, worldManager.transferSessionService());
        this.transferPayloadAdapter.start();

        this.mapActionPayloadAdapter = new PaperMapActionPayloadAdapter(
                this,
                worldManager.worldLocationTeleportService(),
                worldManager.worldExportService()
        );
        this.mapActionPayloadAdapter.start();
        getLogger().info("LazyBuilder enabled.");
    }

    @Override
    public void onDisable() {
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
