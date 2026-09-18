package com.halokaryamedia.lazybuilder.utilities;

import com.halokaryamedia.lazybuilder.utilities.feature.UtilityFeatureRegistry;
import com.halokaryamedia.lazybuilder.utilities.feature.buildhelpers.BuildHelpersFeature;
import com.halokaryamedia.lazybuilder.utilities.feature.buildhelpers.BuildHelpersSettings;
import com.halokaryamedia.lazybuilder.utilities.feature.movement.MovementFeature;
import com.halokaryamedia.lazybuilder.utilities.feature.movement.MovementSettings;
import com.halokaryamedia.lazybuilder.utilities.feature.worldsafety.WorldSafetyFeature;
import com.halokaryamedia.lazybuilder.utilities.feature.worldsafety.WorldSafetySettings;
import com.halokaryamedia.lazybuilder.utilities.telemetry.PaperUtilityTelemetryAdapter;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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
    private static final List<String> MOVEMENT_COMMANDS = List.of(
            "gmc", "gms", "gma", "gmsp", "fly", "noclip", "nightvision"
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
    private PaperUtilityTelemetryAdapter telemetryAdapter;
    private PaperBuilderExtensionPayloadAdapter builderExtensionPayloads;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            validateConfiguration(getConfig());
            installFeatures(getConfig());
            bindHubCommand();
            telemetryAdapter = new PaperUtilityTelemetryAdapter(this);
            telemetryAdapter.start();
            builderExtensionPayloads =
                    new PaperBuilderExtensionPayloadAdapter(this);
            builderExtensionPayloads.start();
        } catch (RuntimeException exception) {
            getLogger().log(Level.SEVERE, "Utilities-Manager could not start safely.", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("Utilities-Manager enabled with "
                + featureRegistry.registeredFeatureIds().size() + " registered feature families and "
                + readyCommandCount() + "/" + CANONICAL_COMMANDS.size() + " command bindings ready.");
        List<String> shadowed = shadowedCommands();
        if (!shadowed.isEmpty()) {
            getLogger().warning("LazyBuilder command label collision detected for: " + String.join(", ", shadowed));
        }
    }

    @Override
    public void onDisable() {
        if (builderExtensionPayloads != null) builderExtensionPayloads.stop();
        if (telemetryAdapter != null) telemetryAdapter.stop();
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
            requireHealthyRequestedFeatures("candidate");
            bindHubCommand();
            reloadConfig();
            getLogger().info("Utilities-Manager configuration reloaded.");
            return true;
        } catch (RuntimeException activationFailure) {
            getLogger().log(Level.SEVERE, "Utilities candidate activation failed; restoring previous runtime.", activationFailure);
            disableCurrentRegistry("Partially activated Utilities candidate did not disable cleanly during rollback.");
            try {
                installFeatures(previous);
                requireHealthyRequestedFeatures("rollback");
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

    public int readyCommandCount() {
        int healthy = 0;
        for (String commandName : CANONICAL_COMMANDS) {
            if (isCommandReady(commandName)) healthy++;
        }
        return healthy;
    }

    public boolean isCommandReady(String commandName) {
        PluginCommand command = getCommand(commandName);
        if (command == null || !command.isRegistered() || !hasExpectedExecutor(commandName, command.getExecutor())) {
            return false;
        }
        return command.getLabel().equalsIgnoreCase(commandName);
    }

    public List<String> shadowedCommands() {
        List<String> shadowed = new ArrayList<>();
        for (String commandName : CANONICAL_COMMANDS) {
            PluginCommand command = getCommand(commandName);
            if (command == null || !command.isRegistered() || !hasExpectedExecutor(commandName, command.getExecutor())) {
                continue;
            }
            if (!command.getLabel().equalsIgnoreCase(commandName)) {
                shadowed.add("/" + commandName);
            }
        }
        return List.copyOf(shadowed);
    }

    public int canonicalCommandCount() {
        return CANONICAL_COMMANDS.size();
    }

    public boolean hasWorldEdit() {
        return pluginEnabled("WorldEdit") || pluginEnabled("FastAsyncWorldEdit");
    }

    private boolean pluginEnabled(String name) {
        Plugin plugin = getServer().getPluginManager().getPlugin(name);
        return plugin != null && plugin.isEnabled();
    }

    private boolean hasExpectedExecutor(String commandName, CommandExecutor executor) {
        if (commandName.equals("lb")) {
            return executor instanceof UtilitiesCommand;
        }
        return MOVEMENT_COMMANDS.contains(commandName) && executor instanceof MovementFeature;
    }

    private void installFeatures(Configuration configuration) {
        ConfigurationSection worldSafetySection = requireSection(configuration, "features.world-safety");
        ConfigurationSection movementSection = requireSection(configuration, "features.movement");
        ConfigurationSection buildHelpersSection = requireSection(configuration, "features.build-helpers");

        WorldSafetySettings nextWorldSafety = WorldSafetySettings.from(worldSafetySection);
        MovementSettings nextMovement = MovementSettings.from(movementSection);
        BuildHelpersSettings nextBuildHelpers = BuildHelpersSettings.from(buildHelpersSection);

        UtilityFeatureRegistry registry = new UtilityFeatureRegistry();
        WorldSafetyFeature worldSafetyFeature = new WorldSafetyFeature(this, nextWorldSafety);
        MovementFeature movementFeature = new MovementFeature(this, nextMovement);
        BuildHelpersFeature buildHelpersFeature = new BuildHelpersFeature(this, nextBuildHelpers);

        registry.register(worldSafetyFeature);
        registry.register(movementFeature);
        registry.register(buildHelpersFeature);

        // Commands stay bound even when Movement is intentionally disabled, so users get
        // an explicit disabled-state response instead of falling back to generic plugin behavior.
        movementFeature.bindCommands();

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

    private void requireHealthyRequestedFeatures(String activationName) {
        if (featureFailures.isEmpty()) return;
        throw new IllegalStateException(
                "Utilities " + activationName + " activation left failed requested feature(s): "
                        + String.join(", ", featureFailures.keySet())
        );
    }

    private String conciseFailure(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private void disableCurrentRegistry(String failureMessage) {
        if (featureRegistry == null) return;
        try {
            featureRegistry.disableAll();
        } catch (RuntimeException exception) {
            getLogger().log(Level.SEVERE, failureMessage, exception);
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

    static void validateConfiguration(Configuration configuration) {
        requireSection(configuration, "features.world-safety");
        requireSection(configuration, "features.movement");
        requireSection(configuration, "features.build-helpers");

        validateOptionalBoolean(configuration, "features.world-safety.enabled");
        validateOptionalBoolean(configuration, "features.world-safety.protections.explosions");
        validateOptionalBoolean(configuration, "features.world-safety.protections.leaves-decay");
        validateOptionalBoolean(configuration, "features.world-safety.protections.farmland-trample");
        validateOptionalBoolean(configuration, "features.world-safety.protections.dragon-egg-teleport");

        validateOptionalBoolean(configuration, "features.movement.enabled");
        validateOptionalBoolean(configuration, "features.movement.abilities.fly");
        validateOptionalBoolean(configuration, "features.movement.abilities.advanced-fly");
        validateOptionalBoolean(configuration, "features.movement.abilities.noclip");
        validateOptionalBoolean(configuration, "features.movement.abilities.night-vision");

        validateOptionalBoolean(configuration, "features.build-helpers.enabled");
        validateOptionalBoolean(configuration, "features.build-helpers.helpers.iron-door-toggle");
        validateOptionalBoolean(configuration, "features.build-helpers.helpers.double-slab-break");
        validateOptionalBoolean(configuration, "features.build-helpers.helpers.glazed-terracotta-rotate");
        validateOptionalBoolean(configuration, "features.build-helpers.interaction.require-sneak-for-slab");
        validateOptionalBoolean(configuration, "features.build-helpers.interaction.require-sneak-for-rotate");

        String scopePath = "features.world-safety.scope.mode";
        if (configuration.contains(scopePath) && !configuration.isString(scopePath)) {
            throw new IllegalArgumentException(scopePath + " must be a string");
        }
        validateOptionalList(configuration, "features.world-safety.scope.include-worlds");
        validateOptionalList(configuration, "features.world-safety.scope.exclude-worlds");

        String scopeMode = configuration.getString(scopePath, "all").trim().toLowerCase(Locale.ROOT);
        if (!scopeMode.equals("all") && !scopeMode.equals("include")) {
            throw new IllegalArgumentException(scopePath + " must be 'all' or 'include'");
        }

        boolean worldSafetyEnabled = configuration.getBoolean("features.world-safety.enabled", true);
        if (worldSafetyEnabled && scopeMode.equals("include")) {
            boolean hasIncludedWorld = configuration.getStringList("features.world-safety.scope.include-worlds")
                    .stream()
                    .anyMatch(value -> value != null && !value.trim().isEmpty());
            if (!hasIncludedWorld) {
                throw new IllegalArgumentException(
                        "features.world-safety.scope.include-worlds must contain at least one world when scope.mode is 'include'"
                );
            }
        }
    }

    private static void validateOptionalBoolean(Configuration configuration, String path) {
        if (configuration.contains(path) && !configuration.isBoolean(path)) {
            throw new IllegalArgumentException(path + " must be true or false");
        }
    }

    private static void validateOptionalList(Configuration configuration, String path) {
        if (configuration.contains(path) && !configuration.isList(path)) {
            throw new IllegalArgumentException(path + " must be a YAML list");
        }
    }

    private static ConfigurationSection requireSection(Configuration configuration, String path) {
        ConfigurationSection section = configuration.getConfigurationSection(path);
        if (section == null) {
            throw new IllegalArgumentException("Missing required configuration section: " + path);
        }
        return section;
    }
}
