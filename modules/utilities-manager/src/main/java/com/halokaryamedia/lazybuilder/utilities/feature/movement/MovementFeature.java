package com.halokaryamedia.lazybuilder.utilities.feature.movement;

import com.halokaryamedia.lazybuilder.utilities.feature.UtilityFeature;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Player movement conveniences with reversible per-player state and no background worker.
 *
 * <p>Noclip intentionally uses Bukkit spectator mode rather than NMS collision hacks. This
 * keeps the feature stable across Paper updates and lets the original game mode be restored.</p>
 */
public final class MovementFeature implements UtilityFeature, Listener, CommandExecutor {
    public static final String ID = "movement";

    private final JavaPlugin plugin;
    private final MovementSettings settings;
    private final Map<UUID, FlightState> flightStates = new HashMap<>();
    private final Map<UUID, GameMode> noclipGameModes = new HashMap<>();
    private final Map<UUID, PotionEffect> nightVisionStates = new HashMap<>();
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
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        bind("fly");
        bind("noclip");
        bind("nightvision");
        enabled = true;
    }

    @Override
    public void disable() {
        if (!enabled) return;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            restorePlayer(player);
        }
        flightStates.clear();
        noclipGameModes.clear();
        nightVisionStates.clear();
        HandlerList.unregisterAll(this);
        enabled = false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }

        return switch (command.getName().toLowerCase()) {
            case "fly" -> handleFly(player, args);
            case "noclip" -> handleNoclip(player);
            case "nightvision" -> handleNightVision(player);
            default -> false;
        };
    }

    private boolean handleFly(Player player, String[] args) {
        if (!settings.advancedFly()) {
            player.sendMessage("Advanced Fly is disabled in Utilities-Manager config.");
            return true;
        }
        if (!player.hasPermission("lazybuilder.utilities.fly")) {
            player.sendMessage("You do not have permission to use Advanced Fly.");
            return true;
        }

        UUID id = player.getUniqueId();
        if (flightStates.containsKey(id)) {
            restoreFlight(player);
            player.sendMessage("Advanced Fly disabled.");
            return true;
        }

        float speed = 0.1F;
        if (args.length > 0) {
            try {
                double multiplier = Double.parseDouble(args[0]);
                if (multiplier < 0.1D || multiplier > 10.0D) {
                    player.sendMessage("Fly speed must be between 0.1 and 10.0.");
                    return true;
                }
                speed = (float) Math.min(1.0D, 0.1D * multiplier);
            } catch (NumberFormatException exception) {
                player.sendMessage("Usage: /fly [speed 0.1-10.0]");
                return true;
            }
        }

        flightStates.put(id, new FlightState(player.getAllowFlight(), player.isFlying(), player.getFlySpeed()));
        player.setAllowFlight(true);
        player.setFlySpeed(speed);
        player.setFlying(true);
        player.sendMessage("Advanced Fly enabled.");
        return true;
    }

    private boolean handleNoclip(Player player) {
        if (!settings.noclip()) {
            player.sendMessage("Noclip is disabled in Utilities-Manager config.");
            return true;
        }
        if (!player.hasPermission("lazybuilder.utilities.noclip")) {
            player.sendMessage("You do not have permission to use Noclip.");
            return true;
        }

        UUID id = player.getUniqueId();
        GameMode previous = noclipGameModes.remove(id);
        if (previous != null) {
            player.setGameMode(previous);
            player.sendMessage("Noclip disabled.");
            return true;
        }

        noclipGameModes.put(id, player.getGameMode());
        player.setGameMode(GameMode.SPECTATOR);
        player.sendMessage("Noclip enabled (Spectator movement).");
        return true;
    }

    private boolean handleNightVision(Player player) {
        if (!settings.nightVision()) {
            player.sendMessage("Night Vision is disabled in Utilities-Manager config.");
            return true;
        }
        if (!player.hasPermission("lazybuilder.utilities.nightvision")) {
            player.sendMessage("You do not have permission to use Night Vision.");
            return true;
        }

        UUID id = player.getUniqueId();
        if (nightVisionStates.containsKey(id)) {
            restoreNightVision(player);
            player.sendMessage("Night Vision disabled.");
            return true;
        }

        PotionEffect previous = player.getPotionEffect(PotionEffectType.NIGHT_VISION);
        nightVisionStates.put(id, previous);
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
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!noclipGameModes.containsKey(id)) return;
        if (event.getNewGameMode() == GameMode.SPECTATOR) return;

        // An external game-mode change takes ownership; do not overwrite it later.
        noclipGameModes.remove(id);
    }

    private void restorePlayer(Player player) {
        restoreFlight(player);
        restoreNightVision(player);
        GameMode previous = noclipGameModes.remove(player.getUniqueId());
        if (previous != null && player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(previous);
        }
    }

    private void restoreFlight(Player player) {
        FlightState previous = flightStates.remove(player.getUniqueId());
        if (previous == null) return;
        player.setFlying(false);
        player.setAllowFlight(previous.allowFlight());
        player.setFlySpeed(previous.flySpeed());
        if (previous.allowFlight() && previous.flying()) {
            player.setFlying(true);
        }
    }

    private void restoreNightVision(Player player) {
        UUID id = player.getUniqueId();
        if (!nightVisionStates.containsKey(id)) return;
        PotionEffect previous = nightVisionStates.remove(id);
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        if (previous != null) {
            player.addPotionEffect(previous);
        }
    }

    private void bind(String commandName) {
        PluginCommand command = plugin.getCommand(commandName);
        if (command == null) {
            throw new IllegalStateException("Missing command declaration: " + commandName);
        }
        command.setExecutor(this);
    }

    private record FlightState(boolean allowFlight, boolean flying, float flySpeed) {}
}
