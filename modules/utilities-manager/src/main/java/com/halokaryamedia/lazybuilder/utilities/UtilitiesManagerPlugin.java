package com.halokaryamedia.lazybuilder.utilities;

import com.halokaryamedia.lazybuilder.utilities.feature.UtilityFeatureRegistry;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Paper bootstrap for LazyBuilder Utilities-Manager.
 *
 * <p>The bootstrap owns only module lifecycle. Each utility feature is registered as an
 * independent component and may be changed without coupling unrelated feature slices.</p>
 */
public final class UtilitiesManagerPlugin extends JavaPlugin {
    private UtilityFeatureRegistry featureRegistry;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.featureRegistry = new UtilityFeatureRegistry();

        // Feature modules are registered here as they are implemented. The registry keeps
        // lifecycle independent so one feature can be updated or disabled without owning
        // another feature's implementation.
        getLogger().info("Utilities-Manager enabled.");
    }

    @Override
    public void onDisable() {
        if (featureRegistry != null) {
            featureRegistry.disableAll();
        }
        getLogger().info("Utilities-Manager disabled.");
    }
}
