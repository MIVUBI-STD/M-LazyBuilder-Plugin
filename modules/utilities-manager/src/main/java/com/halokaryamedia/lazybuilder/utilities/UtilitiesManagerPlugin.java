package com.halokaryamedia.lazybuilder.utilities;

import com.halokaryamedia.lazybuilder.utilities.feature.UtilityFeatureRegistry;
import com.halokaryamedia.lazybuilder.utilities.feature.worldsafety.WorldSafetyFeature;
import com.halokaryamedia.lazybuilder.utilities.feature.worldsafety.WorldSafetySettings;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/** Paper bootstrap for LazyBuilder Utilities-Manager. */
public final class UtilitiesManagerPlugin extends JavaPlugin {
    private UtilityFeatureRegistry featureRegistry;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.featureRegistry = new UtilityFeatureRegistry();

        WorldSafetySettings worldSafetySettings = new WorldSafetySettings(
                getConfig().getBoolean("features.world-safety.protections.explosions", true),
                getConfig().getBoolean("features.world-safety.protections.leaves-decay", true),
                getConfig().getBoolean("features.world-safety.protections.farmland-trample", true),
                getConfig().getBoolean("features.world-safety.protections.dragon-egg-teleport", true)
        );
        featureRegistry.register(new WorldSafetyFeature(this, worldSafetySettings));

        if (getConfig().getBoolean("features.world-safety.enabled", true)) {
            featureRegistry.enable(WorldSafetyFeature.ID);
        }

        getLogger().info("Utilities-Manager enabled with "
                + featureRegistry.registeredFeatureIds().size() + " registered feature family.");
    }

    @Override
    public void onDisable() {
        if (featureRegistry != null) {
            try {
                featureRegistry.disableAll();
            } catch (RuntimeException exception) {
                getLogger().log(Level.SEVERE,
                        "One or more Utilities-Manager features did not disable cleanly.", exception);
            }
        }
        getLogger().info("Utilities-Manager disabled.");
    }
}
