package com.halokaryamedia.lazybuilder.utilities;

import com.halokaryamedia.lazybuilder.utilities.feature.UtilityFeatureRegistry;
import com.halokaryamedia.lazybuilder.utilities.feature.buildhelpers.BuildHelpersFeature;
import com.halokaryamedia.lazybuilder.utilities.feature.buildhelpers.BuildHelpersSettings;
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
        BasicCommands basicCommands = new BasicCommands();
        getCommand("gmc").setExecutor(basicCommands);
        getCommand("gms").setExecutor(basicCommands);
        getCommand("gma").setExecutor(basicCommands);
        getCommand("gmsp").setExecutor(basicCommands);
        this.featureRegistry = new UtilityFeatureRegistry();

        ConfigurationSection worldSafetySection = requireSection("features.world-safety");
        featureRegistry.register(new WorldSafetyFeature(this, WorldSafetySettings.from(worldSafetySection)));

        ConfigurationSection movementSection = requireSection("features.movement");
        featureRegistry.register(new MovementFeature(this, MovementSettings.from(movementSection)));

        ConfigurationSection buildHelpersSection = requireSection("features.build-helpers");
        featureRegistry.register(new BuildHelpersFeature(this, BuildHelpersSettings.from(buildHelpersSection)));

        if (worldSafetySection.getBoolean("enabled", true)) {
            featureRegistry.enable(WorldSafetyFeature.ID);
        }
        if (movementSection.getBoolean("enabled", true)) {
            featureRegistry.enable(MovementFeature.ID);
        }
        if (buildHelpersSection.getBoolean("enabled", true)) {
            featureRegistry.enable(BuildHelpersFeature.ID);
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

    private ConfigurationSection requireSection(String path) {
        ConfigurationSection section = getConfig().getConfigurationSection(path);
        return section != null ? section : getConfig().createSection(path);
    }
}
