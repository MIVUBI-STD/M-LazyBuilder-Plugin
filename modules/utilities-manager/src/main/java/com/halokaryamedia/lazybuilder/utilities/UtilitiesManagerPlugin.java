package com.halokaryamedia.lazybuilder.utilities;

import com.halokaryamedia.lazybuilder.utilities.feature.UtilityFeatureRegistry;
import com.halokaryamedia.lazybuilder.utilities.feature.movement.MovementFeature;
import com.halokaryamedia.lazybuilder.utilities.feature.movement.MovementSettings;
import com.halokaryamedia.lazybuilder.utilities.feature.worldsafety.WorldSafetyFeature;
import com.halokaryamedia.lazybuilder.utilities.feature.worldsafety.WorldSafetySettings;
import org.bukkit.configuration.ConfigurationSection;
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

        ConfigurationSection movementSection = getConfig().getConfigurationSection("features.movement");
        if (movementSection == null) {
            movementSection = getConfig().createSection("features.movement");
        }
        MovementSettings movementSettings = MovementSettings.from(movementSection);
        featureRegistry.register(new MovementFeature(this, movementSettings));

        if (getConfig().getBoolean("features.world-safety.enabled", true)) {
            featureRegistry.enable(WorldSafetyFeature.ID);
        }
        if (getConfig().getBoolean("features.movement.enabled", true)) {
            featureRegistry.enable(MovementFeature.ID);
        }

        getLogger().info("Utilities-Manager enabled with "
                + featureRegistry.registeredFeatureIds().size() + " registered feature families.");
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
