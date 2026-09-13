package com.halokaryamedia.lazybuilder.utilities.feature.spectator;

import com.halokaryamedia.lazybuilder.utilities.feature.UtilityFeature;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/**
 * Spectator camera helpers only. Entering/leaving spectator remains owned by Movement/Noclip
 * or normal Minecraft game-mode controls, so this feature does not duplicate game-mode state.
 */
public final class SpectatorFeature implements UtilityFeature, CommandExecutor {
    public static final String ID = "spectator";

    private final JavaPlugin plugin;
    private final SpectatorSettings settings;
    private boolean enabled;

    public SpectatorFeature(JavaPlugin plugin, SpectatorSettings settings) {
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
        PluginCommand command = plugin.getCommand("spectate");
        if (command == null) throw new IllegalStateException("Missing command declaration: spectate");
        command.setExecutor(this);
        enabled = true;
    }

    @Override
    public void disable() {
        enabled = false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }
        if (!enabled) {
            player.sendMessage("Spectator helpers are disabled.");
            return true;
        }
        if (!settings.playerTargeting()) {
            player.sendMessage("Spectator player targeting is disabled in Utilities-Manager config.");
            return true;
        }
        if (!player.hasPermission("lazybuilder.utilities.spectate")) {
            player.sendMessage("You do not have permission to use Spectator helpers.");
            return true;
        }
        if (player.getGameMode() != GameMode.SPECTATOR) {
            player.sendMessage("You must already be in Spectator mode. Use /noclip if you want spectator movement.");
            return true;
        }
        if (args.length != 1) {
            player.sendMessage("Usage: /spectate <player|clear>");
            return true;
        }
        if (args[0].equalsIgnoreCase("clear")) {
            player.setSpectatorTarget(null);
            player.sendMessage("Spectator target cleared.");
            return true;
        }

        Player target = plugin.getServer().getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            player.sendMessage("Player not found: " + args[0]);
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage("You cannot spectate yourself.");
            return true;
        }

        player.setSpectatorTarget(target);
        player.sendMessage("Now spectating " + target.getName() + ". Use /spectate clear to detach.");
        return true;
    }
}
