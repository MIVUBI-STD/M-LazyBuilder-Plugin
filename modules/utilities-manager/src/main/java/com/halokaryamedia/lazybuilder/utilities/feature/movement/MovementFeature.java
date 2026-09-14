package com.halokaryamedia.lazybuilder.utilities.feature.movement;

import com.halokaryamedia.lazybuilder.utilities.feature.UtilityFeature;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Player movement conveniences with one coherent per-player runtime state. */
public final class MovementFeature implements UtilityFeature, Listener, CommandExecutor, TabCompleter {
    public static final String ID = "movement";
    private static final List<String> COMMANDS = List.of(
            "gmc", "gms", "gma", "gmsp", "fly", "noclip", "nightvision"
    );

    private final JavaPlugin plugin;
    private final MovementSettings settings;
    private final Map<UUID, PlayerUtilityState> states = new HashMap<>();
    private final Set<UUID> internalGameModeChanges = new HashSet<>();
    private boolean enabled;

    public MovementFeature(JavaPlugin plugin, MovementSettings settings) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable() {
        if (enabled) return;
        bindCommands();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        enabled = true;
    }

    @Override
    public void disable() {
        if (!enabled) return;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            restorePlayer(player);
        }
        states.clear();
        internalGameModeChanges.clear();
        HandlerList.unregisterAll(this);
        enabled = false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!enabled) {
            sender.sendMessage("Movement utilities are currently disabled.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }

        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "gmc" -> handleGameMode(player, args, GameMode.CREATIVE);
            case "gms" -> handleGameMode(player, args, GameMode.SURVIVAL);
            case "gma" -> handleGameMode(player, args, GameMode.ADVENTURE);
            case "gmsp" -> handleGameMode(player, args, GameMode.SPECTATOR);
            case "fly" -> handleFly(player, args);
            case "noclip" -> handleNoclip(player, args);
            case "nightvision" -> handleNightVision(player, args);
            default -> false;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("fly") || args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return List.of("1", "2", "4", "8").stream()
                .filter(value -> value.startsWith(prefix))
                .toList();
    }

    private boolean handleGameMode(Player player, String[] args, GameMode mode) {
        if (args.length != 0) {
            player.sendMessage("Usage: /" + modeCommand(mode));
            return true;
        }
        if (!player.hasPermission("lazybuilder.utilities.gamemode")) {
            player.sendMessage("You do not have permission to change gamemode with LazyBuilder.");
            return true;
        }

        PlayerUtilityState state = states.get(player.getUniqueId());
        if (state != null && state.noclipActive) {
            state.noclipActive = false;
            state.noclipBaseline = null;
        }

        changeGameModeInternally(player, mode);
        if (state != null && state.flyActive) {
            state.flightBaseline = FlightState.capture(player);
            applyFly(player, state);
        }
        player.sendMessage("Game mode set to " + mode.name().toLowerCase(Locale.ROOT) + ".");
        cleanupState(player.getUniqueId(), state);
        return true;
    }

    private boolean handleFly(Player player, String[] args) {
        if (!settings.advancedFly()) {
            player.sendMessage("Fly is disabled in Utilities config.");
            return true;
        }
        if (!player.hasPermission("lazybuilder.utilities.fly")) {
            player.sendMessage("You do not have permission to use Fly.");
            return true;
        }
        if (args.length > 1) {
            player.sendMessage("Usage: /fly [speed 0.1-10]");
            return true;
        }

        UUID id = player.getUniqueId();
        PlayerUtilityState state = states.computeIfAbsent(id, ignored -> new PlayerUtilityState());

        if (args.length == 1) {
            double multiplier;
            try {
                multiplier = Double.parseDouble(args[0]);
            } catch (NumberFormatException exception) {
                player.sendMessage("Usage: /fly [speed 0.1-10]");
                return true;
            }
            if (multiplier < 0.1D || multiplier > 10.0D) {
                player.sendMessage("Fly speed must be between 0.1 and 10.");
                return true;
            }
            if (!state.flyActive) {
                state.flightBaseline = FlightState.capture(player);
                state.flyActive = true;
            }
            state.flySpeed = toFlySpeed(multiplier);
            applyFly(player, state);
            player.sendMessage("Fly enabled. Speed: " + trimMultiplier(multiplier) + "x.");
            return true;
        }

        if (state.flyActive) {
            restoreFlight(player, state);
            player.sendMessage("Fly disabled.");
            cleanupState(id, state);
            return true;
        }

        state.flightBaseline = FlightState.capture(player);
        state.flyActive = true;
        state.flySpeed = 0.1F;
        applyFly(player, state);
        player.sendMessage("Fly enabled.");
        return true;
    }

    private boolean handleNoclip(Player player, String[] args) {
        if (args.length != 0) {
            player.sendMessage("Usage: /noclip");
            return true;
        }
        if (!settings.noclip()) {
            player.sendMessage("Noclip is disabled in Utilities config.");
            return true;
        }
        if (!player.hasPermission("lazybuilder.utilities.noclip")) {
            player.sendMessage("You do not have permission to use Noclip.");
            return true;
        }

        UUID id = player.getUniqueId();
        PlayerUtilityState state = states.computeIfAbsent(id, ignored -> new PlayerUtilityState());
        if (state.noclipActive) {
            GameMode restoreMode = state.noclipBaseline;
            state.noclipActive = false;
            state.noclipBaseline = null;
            if (restoreMode != null && player.getGameMode() == GameMode.SPECTATOR) {
                changeGameModeInternally(player, restoreMode);
            }
            if (state.flyActive) {
                applyFly(player, state);
            }
            player.sendMessage("Noclip disabled.");
            cleanupState(id, state);
            return true;
        }

        if (!shouldCreateNoclipSession(player.getGameMode())) {
            player.sendMessage("You are already in Spectator mode.");
            cleanupState(id, state);
            return true;
        }

        state.noclipBaseline = player.getGameMode();
        state.noclipActive = true;
        changeGameModeInternally(player, GameMode.SPECTATOR);
        player.sendMessage("Noclip enabled.");
        return true;
    }

    private boolean handleNightVision(Player player, String[] args) {
        if (args.length != 0) {
            player.sendMessage("Usage: /nightvision");
            return true;
        }
        if (!settings.nightVision()) {
            player.sendMessage("Night Vision is disabled in Utilities config.");
            return true;
        }
        if (!player.hasPermission("lazybuilder.utilities.nightvision")) {
            player.sendMessage("You do not have permission to use Night Vision.");
            return true;
        }

        UUID id = player.getUniqueId();
        PlayerUtilityState state = states.computeIfAbsent(id, ignored -> new PlayerUtilityState());
        if (state.nightVisionActive) {
            restoreNightVision(player, state);
            player.sendMessage("Night Vision disabled.");
            cleanupState(id, state);
            return true;
        }

        state.nightVisionBaseline = player.getPotionEffect(PotionEffectType.NIGHT_VISION);
        state.nightVisionActive = true;
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.NIGHT_VISION,
                Integer.MAX_VALUE,
                0,
                false,
                false,
                true
        ));
        player.sendMessage("Night Vision enabled.");
        return true;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        restorePlayer(event.getPlayer());
        states.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (internalGameModeChanges.contains(id)) return;

        PlayerUtilityState state = states.get(id);
        if (state == null) return;

        if (state.noclipActive && event.getNewGameMode() != GameMode.SPECTATOR) {
            state.noclipActive = false;
            state.noclipBaseline = null;
        }

        if (state.flyActive) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                PlayerUtilityState current = states.get(id);
                if (current == null || !current.flyActive || !player.isOnline()) return;
                current.flightBaseline = FlightState.capture(player);
                applyFly(player, current);
            });
        }
        cleanupState(id, state);
    }

    private void restorePlayer(Player player) {
        PlayerUtilityState state = states.get(player.getUniqueId());
        if (state == null) return;

        if (state.noclipActive && state.noclipBaseline != null && player.getGameMode() == GameMode.SPECTATOR) {
            changeGameModeInternally(player, state.noclipBaseline);
        }
        restoreFlight(player, state);
        restoreNightVision(player, state);
    }

    private void applyFly(Player player, PlayerUtilityState state) {
        player.setAllowFlight(true);
        player.setFlySpeed(state.flySpeed);
        player.setFlying(true);
    }

    private void restoreFlight(Player player, PlayerUtilityState state) {
        if (!state.flyActive || state.flightBaseline == null) return;
        FlightState baseline = state.flightBaseline;
        state.flyActive = false;
        state.flightBaseline = null;
        player.setFlying(false);
        player.setAllowFlight(baseline.allowFlight());
        player.setFlySpeed(baseline.flySpeed());
        if (baseline.allowFlight() && baseline.flying()) {
            player.setFlying(true);
        }
    }

    private void restoreNightVision(Player player, PlayerUtilityState state) {
        if (!state.nightVisionActive) return;
        PotionEffect previous = state.nightVisionBaseline;
        state.nightVisionActive = false;
        state.nightVisionBaseline = null;
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        if (previous != null) {
            player.addPotionEffect(previous);
        }
    }

    private void changeGameModeInternally(Player player, GameMode mode) {
        UUID id = player.getUniqueId();
        internalGameModeChanges.add(id);
        try {
            player.setGameMode(mode);
        } finally {
            internalGameModeChanges.remove(id);
        }
    }

    private void cleanupState(UUID id, PlayerUtilityState state) {
        if (state != null && !state.flyActive && !state.noclipActive && !state.nightVisionActive) {
            states.remove(id, state);
        }
    }

    private void bindCommands() {
        List<PluginCommand> resolved = new ArrayList<>();
        for (String commandName : COMMANDS) {
            PluginCommand command = plugin.getCommand(commandName);
            if (command == null) {
                throw new IllegalStateException("Missing command declaration: " + commandName);
            }
            resolved.add(command);
        }
        for (PluginCommand command : resolved) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
    }

    static float toFlySpeed(double multiplier) {
        return (float) Math.min(1.0D, 0.1D * multiplier);
    }

    static boolean shouldCreateNoclipSession(GameMode mode) {
        return mode != GameMode.SPECTATOR;
    }

    private String trimMultiplier(double multiplier) {
        if (multiplier == Math.rint(multiplier)) {
            return Long.toString(Math.round(multiplier));
        }
        return Double.toString(multiplier);
    }

    private String modeCommand(GameMode mode) {
        return switch (mode) {
            case CREATIVE -> "gmc";
            case SURVIVAL -> "gms";
            case ADVENTURE -> "gma";
            case SPECTATOR -> "gmsp";
        };
    }

    private static final class PlayerUtilityState {
        private FlightState flightBaseline;
        private boolean flyActive;
        private float flySpeed = 0.1F;
        private GameMode noclipBaseline;
        private boolean noclipActive;
        private PotionEffect nightVisionBaseline;
        private boolean nightVisionActive;
    }

    private record FlightState(boolean allowFlight, boolean flying, float flySpeed) {
        static FlightState capture(Player player) {
            return new FlightState(player.getAllowFlight(), player.isFlying(), player.getFlySpeed());
        }
    }
}
