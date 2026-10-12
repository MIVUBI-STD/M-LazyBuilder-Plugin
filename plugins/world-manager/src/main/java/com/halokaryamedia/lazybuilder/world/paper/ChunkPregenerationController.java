package com.halokaryamedia.lazybuilder.world.paper;

import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Objects;

/**
 * Optional bridge to the Chunky Bukkit plugin.
 *
 * <p>LazyBuilder does not depend on Chunky at compile time. Commands are dispatched
 * through Bukkit only when the Chunky plugin is present and enabled. The optional
 * service API is inspected solely to prevent idle-unloading worlds during pregeneration.</p>
 */
public final class ChunkPregenerationController {
    private static final String CHUNKY_PLUGIN_NAME = "Chunky";
    private static final String CHUNKY_API_CLASS = "org.popcraft.chunky.api.ChunkyAPI";
    private static final int SUPPORTED_API_VERSION = 0;

    private final Server server;
    private final WorldRegistry registry;
    private boolean activityWarningLogged;

    public ChunkPregenerationController(Server server, WorldRegistry registry) {
        this.server = Objects.requireNonNull(server, "server");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public boolean available() {
        Plugin chunky = server.getPluginManager().getPlugin(CHUNKY_PLUGIN_NAME);
        return chunky != null && chunky.isEnabled();
    }

    /**
     * Checks the real Chunky service, including Nether/End world-family tasks.
     * This runs only when the existing Paper idle-unload timer checks a world.
     * An installed but unverifiable Chunky API keeps worlds loaded rather than
     * risking an unload while an external plugin still owns chunk generation.
     */
    public boolean keepsWorldLoaded(String folderName) {
        if (!available()) return false;
        Plugin chunky = server.getPluginManager().getPlugin(CHUNKY_PLUGIN_NAME);
        try {
            Class<?> apiClass = Class.forName(CHUNKY_API_CLASS, false, chunky.getClass().getClassLoader());
            Object api = server.getServicesManager().load(apiClass);
            if (api == null) return protectUnknownActivity("Chunky did not register its service API.");
            Method version = apiClass.getMethod("version");
            if (!(version.invoke(api) instanceof Integer apiVersion)
                    || apiVersion != SUPPORTED_API_VERSION) {
                return protectUnknownActivity("Chunky API version is not supported by the idle-unload guard.");
            }
            Method isRunning = apiClass.getMethod("isRunning", String.class);
            return Boolean.TRUE.equals(isRunning.invoke(api, folderName))
                    || Boolean.TRUE.equals(isRunning.invoke(api, folderName + "_nether"))
                    || Boolean.TRUE.equals(isRunning.invoke(api, folderName + "_the_end"));
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
                 | InvocationTargetException | LinkageError | SecurityException failure) {
            return protectUnknownActivity("Chunky activity could not be read ("
                    + failure.getClass().getSimpleName() + ").");
        }
    }

    private boolean protectUnknownActivity(String reason) {
        if (!activityWarningLogged) {
            activityWarningLogged = true;
            server.getLogger().warning("LazyBuilder: " + reason
                    + " Automatic idle unload is held for managed worlds while Chunky is enabled."
                    + " Update Chunky or disable the integration to restore automatic unload.");
        }
        return true;
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
