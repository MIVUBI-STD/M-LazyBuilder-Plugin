package com.halokaryamedia.lazybuilder.utilities;

import com.halokaryamedia.lazybuilder.utilities.feature.buildhelpers.BuildHelpersFeature;
import com.halokaryamedia.lazybuilder.utilities.feature.movement.MovementFeature;
import com.halokaryamedia.lazybuilder.utilities.feature.worldsafety.WorldSafetyFeature;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Builder-facing discovery hub plus small admin diagnostics. */
public final class UtilitiesCommand implements CommandExecutor, TabCompleter {
    private final UtilitiesManagerPlugin plugin;

    public UtilitiesCommand(UtilitiesManagerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showHome(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "help" -> {
                showHelp(sender, args.length >= 2 ? args[1] : "home");
                yield true;
            }
            case "status" -> {
                if (!requirePermission(sender, "lazybuilder.utilities.status")) yield true;
                showStatus(sender);
                yield true;
            }
            case "reload" -> {
                if (!requirePermission(sender, "lazybuilder.utilities.reload")) yield true;
                boolean success = plugin.reloadUtilities();
                sender.sendMessage(success
                        ? "LazyBuilder Utilities reloaded."
                        : "Utilities reload failed; check the server console.");
                yield true;
            }
            default -> {
                sender.sendMessage("Usage: /lb [help|status|reload]");
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            options.add("help");
            if (sender.hasPermission("lazybuilder.utilities.status")) options.add("status");
            if (sender.hasPermission("lazybuilder.utilities.reload")) options.add("reload");
            return filter(options, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("help")) {
            List<String> options = new ArrayList<>(List.of("movement", "build", "minecraft"));
            if (plugin.hasWorldEdit()) options.add("worldedit");
            return filter(options, args[1]);
        }
        return List.of();
    }

    private void showHome(CommandSender sender) {
        sender.sendMessage("LazyBuilder Utilities");
        sender.sendMessage("Quick commands: /fly  /noclip  /nv  /gmc");
        if (sender instanceof Player player) {
            player.sendMessage(menuLink("[ Movement ]", "/lb help movement", "Movement shortcuts"));
            player.sendMessage(menuLink("[ Build ]", "/lb help build", "Builder interaction helpers"));
            player.sendMessage(menuLink("[ Minecraft ]", "/lb help minecraft", "Useful vanilla commands"));
            if (plugin.hasWorldEdit()) {
                player.sendMessage(menuLink("[ WorldEdit ]", "/lb help worldedit", "Useful familiar WorldEdit commands"));
            }
        } else {
            sender.sendMessage("Use /lb help for command guidance.");
        }
    }

    private void showHelp(CommandSender sender, String category) {
        switch (category.toLowerCase(Locale.ROOT)) {
            case "movement" -> {
                sender.sendMessage("Movement");
                sender.sendMessage("/fly [speed] - Toggle flight or set/update fly speed.");
                sender.sendMessage("/noclip - Toggle temporary spectator-based noclip.");
                sender.sendMessage("/nv - Toggle Night Vision. /nightvision also works.");
                sender.sendMessage("/gmc /gms /gma /gmsp - Familiar gamemode shortcuts.");
            }
            case "build" -> {
                sender.sendMessage("Build Helpers");
                sender.sendMessage("Iron Door - right-click to toggle.");
                sender.sendMessage("Double Slab - sneak + break to remove one layer.");
                sender.sendMessage("Glazed Terracotta - sneak + right-click to rotate.");
            }
            case "minecraft" -> {
                sender.sendMessage("Useful Minecraft Commands");
                sender.sendMessage("/tp  /time  /weather  /gamerule  /effect  /give  /clear");
                sender.sendMessage("LazyBuilder reuses these instead of creating duplicate commands.");
            }
            case "worldedit" -> {
                if (!plugin.hasWorldEdit()) {
                    sender.sendMessage("WorldEdit/FAWE is not currently detected.");
                    return;
                }
                sender.sendMessage("Useful WorldEdit Commands");
                sender.sendMessage("/j  /thru  /up  /asc  /desc");
                sender.sendMessage("//wand  //set  //replace");
                sender.sendMessage("These remain owned by WorldEdit/FAWE.");
            }
            default -> showHome(sender);
        }
    }

    private void showStatus(CommandSender sender) {
        sender.sendMessage("LazyBuilder Utilities Status");
        sender.sendMessage("Movement: " + ready(MovementFeature.ID));
        sender.sendMessage("Build Helpers: " + ready(BuildHelpersFeature.ID));
        sender.sendMessage("World Safety: " + ready(WorldSafetyFeature.ID));
        sender.sendMessage("Commands: " + plugin.healthyCommandCount() + "/" + plugin.canonicalCommandCount() + " bound (+ /nv alias)");
        sender.sendMessage("Version: " + plugin.getPluginMeta().getVersion());
    }

    private String ready(String featureId) {
        return plugin.featureEnabled(featureId) ? "READY" : "DISABLED/FAILED";
    }

    private boolean requirePermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) return true;
        sender.sendMessage("You do not have permission to use this command.");
        return false;
    }

    private Component menuLink(String label, String command, String hover) {
        return Component.text(label)
                .clickEvent(ClickEvent.suggestCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover)));
    }

    private List<String> filter(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.startsWith(normalized))
                .toList();
    }
}
