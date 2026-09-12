package com.halokaryamedia.lazybuilder.utilities;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Paper bootstrap for LazyBuilder Utilities-Manager.
 *
 * <p>The module intentionally starts with no background tasks. Features are added as small,
 * independently owned components under this module so builder utilities can evolve without
 * coupling to World-Manager or desktop management code.</p>
 */
public final class UtilitiesManagerPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("Utilities-Manager enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Utilities-Manager disabled.");
    }
}
