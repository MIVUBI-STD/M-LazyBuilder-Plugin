package com.halokaryamedia.lazybuilder.utilities;

import com.halokaryamedia.lazybuilder.utilities.UtilitiesManagerPlugin.FeatureStatus;
import com.halokaryamedia.lazybuilder.utilities.feature.buildhelpers.BuildHelpersFeature;
import com.halokaryamedia.lazybuilder.utilities.feature.buildhelpers.BuildHelpersSettings;
import com.halokaryamedia.lazybuilder.utilities.feature.movement.MovementFeature;
import com.halokaryamedia.lazybuilder.utilities.feature.movement.MovementSettings;
import com.halokaryamedia.lazybuilder.utilities.feature.worldsafety.WorldSafetyFeature;
import com.halokaryamedia.lazybuilder.utilities.feature.worldsafety.WorldSafetySettings;
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
                showHelp(sender, args);
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
                        : "Utilities reload failed; previous runtime was kept when possible. Check console if needed.");
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
            List<String> options = new ArrayList<>(List.of("movement", "build", "world", "minecraft"));
            if (plugin.hasWorldEdit()) options.add("worldedit");
            return filter(options, args[1]);
        }
        if (args.length == 3
                && args[0].equalsIgnoreCase("help")
                && args[1].equalsIgnoreCase("worldedit")
                && plugin.hasWorldEdit()) {
            return filter(List.of("navigation", "selection", "edit", "clipboard", "history"), args[2]);
        }
        return List.of();
    }

    private void showHome(CommandSender sender) {
        sender.sendMessage(Component.text("LazyBuilder Utilities"));
        if (sender instanceof Player player) {
            Component quick = Component.text("Quick: ")
                    .append(quickLink("Fly", "/fly", "Toggle or set builder flight", canUseFly(player)))
                    .append(Component.space())
                    .append(quickLink("Noclip", "/noclip", "Temporary spectator-based noclip", canUseNoclip(player)))
                    .append(Component.space())
                    .append(quickLink("NV", "/nv", "Toggle Night Vision", canUseNightVision(player)))
                    .append(Component.space())
                    .append(quickLink("Creative", "/gmc", "Switch to Creative", canUseGamemode(player)));
            player.sendMessage(quick);

            Component categories = menuLink("[ Movement ]", "/lb help movement", "Movement shortcuts")
                    .append(Component.space())
                    .append(menuLink("[ Build ]", "/lb help build", "Builder interaction helpers"))
                    .append(Component.space())
                    .append(menuLink("[ World ]", "/lb help world", "World protection status"));
            player.sendMessage(categories);

            Component references = menuLink("[ Minecraft ]", "/lb help minecraft", "Useful vanilla commands");
            if (plugin.hasWorldEdit()) {
                references = references.append(Component.space())
                        .append(menuLink("[ WorldEdit ]", "/lb help worldedit", "Useful familiar WorldEdit commands"));
            }
            player.sendMessage(references);
        } else {
            sender.sendMessage("Use /lb help movement|build|world|minecraft for guidance.");
        }
    }

    private void showHelp(CommandSender sender, String[] args) {
        String category = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "home";
        switch (category) {
            case "movement" -> showMovementHelp(sender);
            case "build" -> showBuildHelp(sender);
            case "world" -> showWorldHelp(sender);
            case "minecraft" -> showMinecraftHelp(sender);
            case "worldedit" -> showWorldEditHelp(sender, args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : "home");
            default -> showHome(sender);
        }
    }

    private void showMovementHelp(CommandSender sender) {
        sender.sendMessage("Movement");
        if (plugin.featureStatus(MovementFeature.ID) != FeatureStatus.READY) {
            sender.sendMessage("Movement utilities are " + statusWord(MovementFeature.ID) + ".");
            return;
        }

        MovementSettings settings = plugin.movementSettings();
        int shown = 0;
        if (sender.hasPermission("lazybuilder.utilities.fly") && settings.fly()) {
            sender.sendMessage("/fly [speed] - Toggle flight or set/update fly speed.");
            shown++;
        }
        if (sender.hasPermission("lazybuilder.utilities.noclip") && settings.noclip()) {
            sender.sendMessage("/noclip - Toggle temporary spectator-based noclip.");
            shown++;
        }
        if (sender.hasPermission("lazybuilder.utilities.nightvision") && settings.nightVision()) {
            sender.sendMessage("/nv - Toggle Night Vision. /nightvision also works.");
            shown++;
        }
        if (sender.hasPermission("lazybuilder.utilities.gamemode")) {
            sender.sendMessage("/gmc /gms /gma /gmsp - Familiar gamemode shortcuts.");
            shown++;
        }
        if (shown == 0) sender.sendMessage("No Movement utilities are available for your role.");
    }

    private void showBuildHelp(CommandSender sender) {
        sender.sendMessage("Build Helpers");
        if (!sender.hasPermission("lazybuilder.utilities.build")) {
            sender.sendMessage("Build helpers are not available for your role.");
            return;
        }
        if (plugin.featureStatus(BuildHelpersFeature.ID) != FeatureStatus.READY) {
            sender.sendMessage("Build Helpers are " + statusWord(BuildHelpersFeature.ID) + ".");
            return;
        }

        BuildHelpersSettings settings = plugin.buildHelpersSettings();
        sender.sendMessage("Iron Door: " + onOff(settings.ironDoorToggle()) + " - right-click to toggle.");

        String slabAction = settings.requireSneakForSlab()
                ? "sneak + break removes one layer (bottom remains)."
                : "break a double slab to remove one layer (bottom remains).";
        sender.sendMessage("Double Slab: " + onOff(settings.doubleSlabBreak()) + " - " + slabAction);

        String rotateAction = settings.requireSneakForRotate()
                ? "sneak + right-click rotates."
                : "right-click rotates.";
        sender.sendMessage("Glazed Terracotta: " + onOff(settings.glazedTerracottaRotate()) + " - " + rotateAction);
    }

    private void showWorldHelp(CommandSender sender) {
        sender.sendMessage("World Protection");
        if (plugin.featureStatus(WorldSafetyFeature.ID) != FeatureStatus.READY) {
            sender.sendMessage("World Safety is " + statusWord(WorldSafetyFeature.ID) + ".");
            return;
        }
        WorldSafetySettings settings = plugin.worldSafetySettings();
        sender.sendMessage("Explosions: " + onOff(settings.explosions()) + " | Leaves: " + onOff(settings.leavesDecay()));
        sender.sendMessage("Farmland: " + onOff(settings.farmlandTrample()) + " | Dragon Egg: " + onOff(settings.dragonEggTeleport()));
        sender.sendMessage("Scope: " + scopeSummary(sender, settings));
    }

    private void showMinecraftHelp(CommandSender sender) {
        sender.sendMessage("Useful Minecraft Commands");
        sender.sendMessage("/tp - Teleport players or yourself.");
        sender.sendMessage("/time - Change world time.");
        sender.sendMessage("/weather - Change weather.");
        sender.sendMessage("/gamerule - Adjust vanilla world behavior.");
    }

    private void showWorldEditHelp(CommandSender sender, String page) {
        if (!plugin.hasWorldEdit()) {
            sender.sendMessage("WorldEdit/FAWE is not currently enabled.");
            return;
        }
        switch (page) {
            case "navigation" -> {
                sender.sendMessage("WorldEdit - Navigation");
                sender.sendMessage("/j  /thru  /up  /asc  /desc");
            }
            case "selection" -> {
                sender.sendMessage("WorldEdit - Selection");
                sender.sendMessage("//wand  //pos1  //pos2  //expand  //contract");
            }
            case "edit" -> {
                sender.sendMessage("WorldEdit - Edit");
                sender.sendMessage("//set  //replace  //stack  //move");
            }
            case "clipboard" -> {
                sender.sendMessage("WorldEdit - Clipboard");
                sender.sendMessage("//copy  //cut  //paste  //rotate  //flip");
            }
            case "history" -> {
                sender.sendMessage("WorldEdit - History");
                sender.sendMessage("//undo  //redo  //clearhistory");
            }
            default -> {
                sender.sendMessage("WorldEdit - Builder Reference");
                if (sender instanceof Player player) {
                    player.sendMessage(menuLink("[ Navigation ]", "/lb help worldedit navigation", "Movement commands")
                            .append(Component.space()).append(menuLink("[ Selection ]", "/lb help worldedit selection", "Selection commands"))
                            .append(Component.space()).append(menuLink("[ Edit ]", "/lb help worldedit edit", "Editing commands")));
                    player.sendMessage(menuLink("[ Clipboard ]", "/lb help worldedit clipboard", "Clipboard commands")
                            .append(Component.space()).append(menuLink("[ History ]", "/lb help worldedit history", "Undo/redo commands")));
                } else {
                    sender.sendMessage("Use /lb help worldedit navigation|selection|edit|clipboard|history");
                }
            }
        }
    }

    private void showStatus(CommandSender sender) {
        sender.sendMessage("LazyBuilder Utilities Status");
        showFeatureStatus(sender, "Movement", MovementFeature.ID);
        showFeatureStatus(sender, "Build Helpers", BuildHelpersFeature.ID);
        showFeatureStatus(sender, "World Safety", WorldSafetyFeature.ID);
        sender.sendMessage("Commands: " + plugin.readyCommandCount() + "/" + plugin.canonicalCommandCount() + " ready (+ /nv alias)");

        List<String> shadowed = plugin.shadowedCommands();
        if (!shadowed.isEmpty()) {
            sender.sendMessage("Command conflicts: " + String.join(", ", shadowed) + " (another plugin may own the short label)");
        }
        sender.sendMessage("Version: " + plugin.getPluginMeta().getVersion());
    }

    private void showFeatureStatus(CommandSender sender, String label, String featureId) {
        FeatureStatus status = plugin.featureStatus(featureId);
        sender.sendMessage(label + ": " + status.name());
        if (status == FeatureStatus.FAILED) {
            String reason = plugin.featureFailure(featureId);
            if (reason != null) sender.sendMessage("  Reason: " + reason);
        }
    }

    private boolean canUseFly(CommandSender sender) {
        return plugin.featureStatus(MovementFeature.ID) == FeatureStatus.READY
                && plugin.movementSettings().fly()
                && sender.hasPermission("lazybuilder.utilities.fly");
    }

    private boolean canUseNoclip(CommandSender sender) {
        return plugin.featureStatus(MovementFeature.ID) == FeatureStatus.READY
                && plugin.movementSettings().noclip()
                && sender.hasPermission("lazybuilder.utilities.noclip");
    }

    private boolean canUseNightVision(CommandSender sender) {
        return plugin.featureStatus(MovementFeature.ID) == FeatureStatus.READY
                && plugin.movementSettings().nightVision()
                && sender.hasPermission("lazybuilder.utilities.nightvision");
    }

    private boolean canUseGamemode(CommandSender sender) {
        return plugin.featureStatus(MovementFeature.ID) == FeatureStatus.READY
                && sender.hasPermission("lazybuilder.utilities.gamemode");
    }

    private String statusWord(String featureId) {
        return plugin.featureStatus(featureId).name().toLowerCase(Locale.ROOT);
    }

    private String onOff(boolean enabled) {
        return enabled ? "ON" : "OFF";
    }

    private String scopeSummary(CommandSender sender, WorldSafetySettings settings) {
        boolean detailed = sender.hasPermission("lazybuilder.utilities.status");
        if (settings.scopeMode().equals("include")) {
            if (!detailed) return "selected worlds";
            return "included worlds: " + String.join(", ", settings.includeWorlds());
        }
        if (!settings.excludeWorlds().isEmpty()) {
            if (!detailed) return "all worlds with exclusions";
            return "all worlds except: " + String.join(", ", settings.excludeWorlds());
        }
        return "all worlds";
    }

    private boolean requirePermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) return true;
        sender.sendMessage("You do not have permission to use this command.");
        return false;
    }

    private Component quickLink(String label, String command, String hover, boolean available) {
        if (!available) {
            return Component.text("[ " + label + " ]")
                    .hoverEvent(HoverEvent.showText(Component.text("Not available for your current role/config")));
        }
        return menuLink("[ " + label + " ]", command, hover);
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
