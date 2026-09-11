package com.halokaryamedia.lazybuilder;

import com.halokaryamedia.lazybuilder.world.WorldManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class LazyBuilderPlugin extends JavaPlugin {
    private WorldManager worldManager;

    @Override
    public void onEnable() {
        this.worldManager = new WorldManager(this);
        this.worldManager.start();
        getLogger().info("LazyBuilder enabled.");
    }

    @Override
    public void onDisable() {
        if (worldManager != null) {
            worldManager.stop();
        }
    }
}
