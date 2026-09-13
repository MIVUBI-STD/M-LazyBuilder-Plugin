package com.halokaryamedia.lazybuilder.world.paper;

import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Operator-only command surface for build-server performance tools. */
public final class BuildPerformanceCommand implements CommandExecutor, TabCompleter {
    private final Server server;
    private final ChunkPregenerationController pregeneration;

    public BuildPerformanceCommand(Server server, ChunkPregenerationController pregeneration) {
        this.server = Objects.requireNonNull(server, "server");
        this.pregeneration = Objects.requireNonNull(pregeneration, "pregeneration");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("lazybuilder.performance.manage")) {
            sender.sendMessage("You do not have permission to manage LazyBuilder performance tools.");
            return true;
        }
        if (args.length == 0 || "status".equalsIgnoreCase(args[0])) {
            sendStatus(sender);
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        try {
            switch (action) {
                case "pregen" -> handlePregen(sender, args);
                case "pause" -> handleChunkyAction(sender, () -> pregeneration.pause(requireWorld(args)), "Pregeneration paused.");
                case "continue", "resume" -> handleChunkyAction(sender, () -> pregeneration.resume(requireWorld(args)), "Pregeneration resumed.");
                case "cancel" -> handleChunkyAction(sender, () -> pregeneration.cancel(requireWorld(args)), "Pregeneration cancelled. Existing generated chunks are retained.");
                case "profile" -> handleProfile(sender, args, false);
                case "profilelag" -> handleProfile(sender, args, true);
                default -> sender.sendMessage("Usage: /" + label + " status|pregen|pause|continue|cancel|profile|profilelag");
            }
        } catch (RuntimeException exception) {
            sender.sendMessage("LazyBuilder performance: " + exception.getMessage());
        }
        return true;
    }

    private void sendStatus(CommandSender sender) {
        double[] tps = server.getTPS();
        double tps1m = tps.length > 0 ? Math.min(20.0, tps[0]) : 0.0;
        double tps5m = tps.length > 1 ? Math.min(20.0, tps[1]) : tps1m;
        double mspt = server.getAverageTickTime();

        sender.sendMessage("LazyBuilder build performance tools");
        sender.sendMessage(String.format(Locale.ROOT, "- TPS: %.2f (1m) / %.2f (5m)", tps1m, tps5m));
        sender.sendMessage(String.format(Locale.ROOT, "- MSPT: %.2f ms", mspt));
        sender.sendMessage("- Chunky: " + (pregeneration.available() ? "available" : "optional/not installed"));
        sender.sendMessage("- spark: bundled by Paper 1.21+");
        sender.sendMessage("- AI policy tags: lazybuilder_gameplay_ai (opt out), lazybuilder_decorative (force no-AI)");
    }

    private void handlePregen(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /lazyperf pregen <world> <radiusBlocks> [square|circle] [centerX] [centerZ]");
            return;
        }
        String world = args[1];
        int radius = parseInt(args[2], "radiusBlocks");
        String shape = args.length >= 4 ? args[3] : "square";
        int centerX = args.length >= 5 ? parseInt(args[4], "centerX") : 0;
        int centerZ = args.length >= 6 ? parseInt(args[5], "centerZ") : 0;
        pregeneration.start(world, shape, centerX, centerZ, radius);
        sender.sendMessage("Chunky pregeneration started for " + world + " with " + shape + " radius " + radius + " blocks.");
    }

    private void handleProfile(CommandSender sender, String[] args, boolean lagOnly) {
        int timeoutSeconds = lagOnly
                ? (args.length >= 3 ? boundedPositive(args[2], "timeoutSeconds", 30, 1800) : 300)
                : (args.length >= 2 ? boundedPositive(args[1], "timeoutSeconds", 30, 1800) : 300);

        String command;
        if (lagOnly) {
            int thresholdMs = args.length >= 2 ? boundedPositive(args[1], "thresholdMs", 50, 5000) : 100;
            command = "spark profiler start --only-ticks-over " + thresholdMs + " --timeout " + timeoutSeconds;
            sender.sendMessage("spark lag profiler started for ticks over " + thresholdMs + " ms, timeout " + timeoutSeconds + " s.");
        } else {
            command = "spark profiler start --timeout " + timeoutSeconds;
            sender.sendMessage("spark profiler started for " + timeoutSeconds + " seconds.");
        }

        if (!server.dispatchCommand(server.getConsoleSender(), command)) {
            throw new IllegalStateException("Paper/spark rejected profiling command");
        }
    }

    private static void handleChunkyAction(CommandSender sender, Runnable action, String successMessage) {
        action.run();
        sender.sendMessage(successMessage);
    }

    private static String requireWorld(String[] args) {
        if (args.length < 2 || args[1].isBlank()) {
            throw new IllegalArgumentException("A managed world name is required");
        }
        return args[1];
    }

    private static int boundedPositive(String value, String name, int min, int max) {
        int parsed = parseInt(value, name);
        if (parsed < min || parsed > max) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max);
        }
        return parsed;
    }

    private static int parseInt(String value, String name) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be a valid integer", exception);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("status", "pregen", "pause", "continue", "cancel", "profile", "profilelag"), args[0]);
        }
        if (args.length == 4 && "pregen".equalsIgnoreCase(args[0])) {
            return filter(List.of("square", "circle"), args[3]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.startsWith(normalized)) result.add(value);
        }
        return result;
    }
}
