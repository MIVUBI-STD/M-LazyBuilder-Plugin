package com.halokaryamedia.lazybuilder.world.paper;

import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.Objects;

/**
 * Optional bridge to the Chunky Bukkit plugin.
 *
 * <p>LazyBuilder does not depend on Chunky at compile time. Commands are dispatched
 * through Bukkit only when the Chunky plugin is present and enabled, keeping the
 * World-Manager module usable without any additional plugin.</p>
 */
public final class ChunkPregenerationController {
    private static final String CHUNKY_PLUGIN_NAME = "Chunky";

    private final Server server;
    private final WorldRegistry registry;

    public ChunkPregenerationController(Server server, WorldRegistry registry) {
        this.server = Objects.requireNonNull(server, "server");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public boolean available() {
        Plugin chunky = server.getPluginManager().getPlugin(CHUNKY_PLUGIN_NAME);
        return chunky != null && chunky.isEnabled();
    }

    public void start(String worldName, String shape, int centerX, int centerZ, int radiusBlocks) {
        String world = requireManagedWorld(worldName);
        String normalizedShape = normalizeShape(shape);
        if (radiusBlocks < 16 || radiusBlocks > 30_000_000) {
            throw new IllegalArgumentException("Pregeneration radius must be between 16 and 30000000 blocks");
        }
        dispatch("chunky start " + world + " " + normalizedShape + " " + centerX + " " + centerZ + " " + radiusBlocks);
    }

    public void pause(String worldName) {
        dispatch("chunky pause " + requireManagedWorld(worldName));
    }

    public void resume(String worldName) {
        dispatch("chunky continue " + requireManagedWorld(worldName));
    }

    public void cancel(String worldName) {
        dispatch("chunky cancel " + requireManagedWorld(worldName));
    }

    private void dispatch(String command) {
        if (!available()) {
            throw new IllegalStateException("Chunky is not installed or enabled. LazyBuilder pregeneration is optional and requires the Chunky plugin.");
        }
        CommandSender console = server.getConsoleSender();
        if (!server.dispatchCommand(console, command)) {
            throw new IllegalStateException("Chunky rejected command: " + command);
        }
    }

    private String requireManagedWorld(String worldName) {
        String normalized = Objects.requireNonNull(worldName, "worldName").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("World name must not be empty");
        }
        if (normalized.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Managed world name contains unsupported whitespace for Chunky command dispatch: " + normalized);
        }
        if (registry.findByFolderName(normalized).isEmpty()) {
            throw new IllegalArgumentException("World is not managed by LazyBuilder: " + normalized);
        }
        return normalized;
    }

    private static String normalizeShape(String shape) {
        String normalized = Objects.requireNonNull(shape, "shape").strip().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "square", "circle" -> normalized;
            default -> throw new IllegalArgumentException("Pregeneration shape must be square or circle");
        };
    }
}
