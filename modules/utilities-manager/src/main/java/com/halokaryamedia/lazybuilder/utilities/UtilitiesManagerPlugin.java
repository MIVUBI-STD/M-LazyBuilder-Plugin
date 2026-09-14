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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/** Paper bootstrap for LazyBuilder Utilities-Manager. */
public final class UtilitiesManagerPlugin extends JavaPlugin {
    private static final List<String> CANONICAL_COMMANDS = List.of(
            "gmc", "gms", "gma", "gmsp", "fly", "noclip", "nightvision", "lb"
    );

    public enum FeatureStatus {
        READY,
        DISABLED,
        FAILED
    }

    private UtilityFeatureRegistry featureRegistry;
    private final Map<String, Boolean> requestedFeatures = new HashMap<>();
    private final Map<String, String> featureFailures = new HashMap<>();
    private MovementSettings movementSettings;
    private BuildHelpersSettings buildHelpersSettings;
    private WorldSafetySettings worldSafetySettings;

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
                + boundCommandCount() + "/" + CANONICAL_COMMANDS.size() + " command bindings ready.");
    }

    @Override
    public void onDisable() {
        disableCurrentRegistry("One or more Utilities-Manager features did not disable cleanly.");
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

        YamlConfiguration previous = new YamlConfiguration();
        try {
            previous.loadFromString(getConfig().saveToString());
            validateConfiguration(previous);
        } catch (InvalidConfigurationException | RuntimeException exception) {
            getLogger().log(Level.SEVERE, "Current Utilities configuration could not be snapshotted safely.", exception);
            return false;
        }

        UtilityFeatureRegistry oldRegistry = featureRegistry;
        try {
            if (oldRegistry != null) oldRegistry.disableAll();
        } catch (RuntimeException exception) {
            getLogger().log(Level.SEVERE, "Utilities reload stopped because current features did not disable cleanly.", exception);
            return false;
        }

        try {
            installFeatures(candidate);
            if (hasFailedFeatures()) {
                throw new IllegalStateException("One or more requested Utilities features failed to activate");
            }
            bindHubCommand();
            reloadConfig();
            getLogger().info("Utilities-Manager configuration reloaded.");
            return true;
        } catch (RuntimeException activationFailure) {
            getLogger().log(Level.SEVERE, "Utilities candidate activation failed; restoring previous runtime.", activationFailure);
            if (!disableCurrentRegistry("Partially activated Utilities candidate did not disable cleanly during rollback.")) {
                getLogger().severe("Utilities rollback cannot continue safely; disabling plugin.");
                getServer().getPluginManager().disablePlugin(this);
                return false;
            }
            try {
                installFeatures(previous);
                if (hasFailedFeatures()) {
                    throw new IllegalStateException("Previous Utilities runtime could not be fully restored");
                }
                bindHubCommand();
                getLogger().warning("Previous Utilities runtime restored after reload failure.");
            } catch (RuntimeException rollbackFailure) {
                getLogger().log(Level.SEVERE, "Utilities rollback failed; disabling plugin to avoid a partial runtime.", rollbackFailure);
                getServer().getPluginManager().disablePlugin(this);
            }
            return false;
        }
    }

    public FeatureStatus featureStatus(String featureId) {
        if (!requestedFeatures.getOrDefault(featureId, false)) return FeatureStatus.DISABLED;
        if (featureFailures.containsKey(featureId)) return FeatureStatus.FAILED;
        return featureRegistry != null && featureRegistry.isEnabled(featureId)
                ? FeatureStatus.READY
                : FeatureStatus.FAILED;
    }

    public String featureFailure(String featureId) {
        return featureFailures.get(featureId);
    }

    public MovementSettings movementSettings() {
        return movementSettings;
    }

    public BuildHelpersSettings buildHelpersSettings() {
        return buildHelpersSettings;
    }

    public WorldSafetySettings worldSafetySettings() {
        return worldSafetySettings;
    }

    public int boundCommandCount() {
        int healthy = 0;
        for (String commandName : CANONICAL_COMMANDS) {
            if (isCommandBound(commandName)) healthy++;
        }
        return healthy;
    }

    public boolean isCommandBound(String commandName) {
        PluginCommand command = getCommand(commandName);
        return command != null && command.getExecutor() != null;
    }

    public int canonicalCommandCount() {
        return CANONICAL_COMMANDS.size();
    }

    public boolean hasWorldEdit() {
        return getServer().getPluginManager().getPlugin("WorldEdit") != null
                || getServer().getPluginManager().getPlugin("FastAsyncWorldEdit") != null;
    }

    private void installFeatures(Configuration configuration) {
        ConfigurationSection worldSafetySection = requireSection(configuration, "features.world-safety");
        ConfigurationSection movementSection = requireSection(configuration, "features.movement");
        ConfigurationSection buildHelpersSection = requireSection(configuration, "features.build-helpers");

        WorldSafetySettings nextWorldSafety = WorldSafetySettings.from(worldSafetySection);
        MovementSettings nextMovement = MovementSettings.from(movementSection);
        BuildHelpersSettings nextBuildHelpers = BuildHelpersSettings.from(buildHelpersSection);

        UtilityFeatureRegistry registry = new UtilityFeatureRegistry();
        registry.register(new WorldSafetyFeature(this, nextWorldSafety));
        registry.register(new MovementFeature(this, nextMovement));
        registry.register(new BuildHelpersFeature(this, nextBuildHelpers));

        featureRegistry = registry;
        worldSafetySettings = nextWorldSafety;
        movementSettings = nextMovement;
        buildHelpersSettings = nextBuildHelpers;
        requestedFeatures.clear();
        featureFailures.clear();

        enableConfiguredFeature(registry, WorldSafetyFeature.ID, worldSafetySection.getBoolean("enabled", true));
        enableConfiguredFeature(registry, MovementFeature.ID, movementSection.getBoolean("enabled", true));
        enableConfiguredFeature(registry, BuildHelpersFeature.ID, buildHelpersSection.getBoolean("enabled", true));
    }

    private void enableConfiguredFeature(UtilityFeatureRegistry registry, String id, boolean requested) {
        requestedFeatures.put(id, requested);
        if (!requested) return;
        try {
            registry.enable(id);
        } catch (RuntimeException exception) {
            featureFailures.put(id, conciseFailure(exception));
            getLogger().log(Level.SEVERE, "Utilities feature '" + id + "' failed to enable; other families will continue.", exception);
        }
    }

    private boolean hasFailedFeatures() {
        return !featureFailures.isEmpty();
    }

    private String conciseFailure(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private boolean disableCurrentRegistry(String failureMessage) {
        if (featureRegistry == null) return true;
        try {
            featureRegistry.disableAll();
            return true;
        } catch (RuntimeException exception) {
            getLogger().log(Level.SEVERE, failureMessage, exception);
            return false;
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
