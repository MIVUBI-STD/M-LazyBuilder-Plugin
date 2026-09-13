package com.halokaryamedia.lazybuilder.utilities;

import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Minimal builder-friendly aliases for the vanilla gamemode command. */
public final class BasicCommands implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }

        GameMode mode = switch (command.getName().toLowerCase()) {
            case "gmc" -> GameMode.CREATIVE;
            case "gms" -> GameMode.SURVIVAL;
            case "gma" -> GameMode.ADVENTURE;
            case "gmsp" -> GameMode.SPECTATOR;
            default -> null;
        };
        if (mode == null) {
            return false;
        }

        player.setGameMode(mode);
        player.sendMessage("Game mode set to " + mode.name().toLowerCase() + ".");
        return true;
    }
}
