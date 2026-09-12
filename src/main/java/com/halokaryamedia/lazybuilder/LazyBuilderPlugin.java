package com.halokaryamedia.lazybuilder;

import com.halokaryamedia.lazybuilder.world.WorldManager;
import com.halokaryamedia.lazybuilder.world.paper.PaperTransferPayloadAdapter;
import org.bukkit.plugin.java.JavaPlugin;

public final class LazyBuilderPlugin extends JavaPlugin {
    private WorldManager worldManager;
    private PaperTransferPayloadAdapter transferPayloadAdapter;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.worldManager = new WorldManager(this);
        this.worldManager.start();
        this.transferPayloadAdapter = new PaperTransferPayloadAdapter(this, worldManager.transferSessionService());
        this.transferPayloadAdapter.start();
        getLogger().info("LazyBuilder enabled.");
    }

    @Override
    public void onDisable() {
        if (transferPayloadAdapter != null) {
            transferPayloadAdapter.stop();
        }
        if (worldManager != null) {
            worldManager.stop();
        }
    }
}
