package com.halokaryamedia.lazybuilder.utilities;

import com.halokaryamedia.lazybuilder.utilities.feature.UtilityFeatureRegistry;
import com.halokaryamedia.lazybuilder.utilities.feature.buildhelpers.BuildHelpersFeature;
import com.halokaryamedia.lazybuilder.utilities.feature.buildhelpers.BuildHelpersSettings;
import com.halokaryamedia.lazybuilder.utilities.feature.movement.MovementFeature;
import com.halokaryamedia.lazybuilder.utilities.feature.movement.MovementSettings;
import com.halokaryamedia.lazybuilder.utilities.feature.worldsafety.WorldSafetyFeature;
import com.halokaryamedia.lazybuilder.utilities.feature.worldsafety.WorldSafetySettings;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/** Paper bootstrap for LazyBuilder Utilities-Manager. */
public final class UtilitiesManagerPlugin extends JavaPlugin {
    private static final List<String> CANONICAL_COMMANDS = List.of(
            "gmc", "gms", "gma", "gmsp", "fly", "noclip", "nightvision", "lb"
    );

    private UtilityFeatureRegistry featureRegistry;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            validateConfiguration(getConfig());
            installFeatures(getConfig());
            bindHubCommand();
        } catch (RuntimeException exception) {
            getLogger().log(Level.SEVERE, "Utilities-Manager could not start safely.", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("Utilities-Manager enabled with "
                + featureRegistry.registeredFeatureIds().size() + " registered feature families and "
                + healthyCommandCount() + "/" + CANONICAL_COMMANDS.size() + " command bindings ready.");
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

    public boolean reloadUtilities() {
        File configFile = new File(getDataFolder(), "config.yml");
        YamlConfiguration candidate = new YamlConfiguration();
        try {
            candidate.load(configFile);
            validateConfiguration(candidate);
        } catch (IOException | InvalidConfigurationException | RuntimeException exception) {
            getLogger().log(Level.WARNING, "Utilities config reload rejected; current runtime was kept.", exception);
            return false;
        }

        UtilityFeatureRegistry previous = featureRegistry;
        try {
            if (previous != null) {
                previous.disableAll();
            }
        } catch (RuntimeException exception) {
            getLogger().log(Level.SEVERE, "Utilities reload stopped because current features did not disable cleanly.", exception);
            return false;
        }

        try {
            reloadConfig();
            installFeatures(candidate);
            bindHubCommand();
            getLogger().info("Utilities-Manager configuration reloaded.");
            return true;
        } catch (RuntimeException exception) {
            getLogger().log(Level.SEVERE, "Utilities config was valid but could not be activated cleanly.", exception);
            return false;
        }
    }

    public boolean featureEnabled(String featureId) {
        return featureRegistry != null && featureRegistry.isEnabled(featureId);
    }

    public int healthyCommandCount() {
        int healthy = 0;
        for (String commandName : CANONICAL_COMMANDS) {
            PluginCommand command = getCommand(commandName);
            if (command != null && command.getExecutor() != null) {
                healthy++;
            }
        }
        return healthy;
    }

    public int canonicalCommandCount() {
        return CANONICAL_COMMANDS.size();
    }

    public boolean hasWorldEdit() {
        return getServer().getPluginManager().getPlugin("WorldEdit") != null
                || getServer().getPluginManager().getPlugin("FastAsyncWorldEdit") != null;
    }

    private void installFeatures(Configuration configuration) {
        UtilityFeatureRegistry registry = new UtilityFeatureRegistry();

        ConfigurationSection worldSafetySection = requireSection(configuration, "features.world-safety");
        ConfigurationSection movementSection = requireSection(configuration, "features.movement");
        ConfigurationSection buildHelpersSection = requireSection(configuration, "features.build-helpers");

        registry.register(new WorldSafetyFeature(this, WorldSafetySettings.from(worldSafetySection)));
        registry.register(new MovementFeature(this, MovementSettings.from(movementSection)));
        registry.register(new BuildHelpersFeature(this, BuildHelpersSettings.from(buildHelpersSection)));

        featureRegistry = registry;
        enableConfiguredFeature(registry, WorldSafetyFeature.ID, worldSafetySection.getBoolean("enabled", true));
        enableConfiguredFeature(registry, MovementFeature.ID, movementSection.getBoolean("enabled", true));
        enableConfiguredFeature(registry, BuildHelpersFeature.ID, buildHelpersSection.getBoolean("enabled", true));
    }

    private void enableConfiguredFeature(UtilityFeatureRegistry registry, String id, boolean requested) {
        if (!requested) return;
        try {
            registry.enable(id);
        } catch (RuntimeException exception) {
            getLogger().log(Level.SEVERE, "Utilities feature '" + id + "' failed to enable; other families will continue.", exception);
        }
    }

    private void bindHubCommand() {
        PluginCommand command = getCommand("lb");
        if (command == null) {
            throw new IllegalStateException("Missing command declaration: lb");
        }
        UtilitiesCommand handler = new UtilitiesCommand(this);
        command.setExecutor(handler);
        command.setTabCompleter(handler);
    }

    private void validateConfiguration(Configuration configuration) {
        requireSection(configuration, "features.world-safety");
        requireSection(configuration, "features.movement");
        requireSection(configuration, "features.build-helpers");

        String scopeMode = configuration.getString("features.world-safety.scope.mode", "all")
                .toLowerCase(Locale.ROOT);
        if (!scopeMode.equals("all") && !scopeMode.equals("include")) {
            throw new IllegalArgumentException("features.world-safety.scope.mode must be 'all' or 'include'");
        }
    }

    private ConfigurationSection requireSection(Configuration configuration, String path) {
        ConfigurationSection section = configuration.getConfigurationSection(path);
        if (section == null) {
            throw new IllegalArgumentException("Missing required configuration section: " + path);
        }
        return section;
    }
}
