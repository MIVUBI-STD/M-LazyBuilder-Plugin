package com.halokaryamedia.lazybuilder.world.paper;

import com.halokaryamedia.lazybuilder.world.application.WorldAreaSelection;
import com.halokaryamedia.lazybuilder.world.application.WorldExportOptions;
import com.halokaryamedia.lazybuilder.world.application.WorldExportService;
import com.halokaryamedia.lazybuilder.world.application.WorldLocationTeleportService;
import com.halokaryamedia.lazybuilder.world.export.ExportSettingsWire;
import com.halokaryamedia.lazybuilder.world.map.MapActionWireProtocol;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Thin Paper transport for map intents; domain authority stays in application services. */
public final class PaperMapActionPayloadAdapter implements PluginMessageListener, Listener {
    public static final String CHANNEL = "lazybuilder:map";
    public static final String TELEPORT_PERMISSION = "lazybuilder.world.teleport";
    public static final String MANAGE_PERMISSION = "lazybuilder.world.manage";
    private static final String PAPER_NETHER_SUFFIX = "_nether";
    private static final String PAPER_END_SUFFIX = "_the_end";

    private final JavaPlugin plugin;
    private final WorldRegistry registry;
    private final WorldLocationTeleportService teleportService;
    private final WorldExportService exportService;
    private final Map<UUID, WorldExportService.ExportTask> activeExports = new HashMap<>();
    private final PendingMapExportCompletionStore pendingExportCompletions;
    private volatile boolean started;
    private volatile boolean stopping;

    public PaperMapActionPayloadAdapter(
            JavaPlugin plugin,
            WorldRegistry registry,
            WorldLocationTeleportService teleportService,
            WorldExportService exportService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.teleportService = Objects.requireNonNull(teleportService, "teleportService");
        this.exportService = Objects.requireNonNull(exportService, "exportService");
        this.pendingExportCompletions = new PendingMapExportCompletionStore(
                plugin.getDataFolder().toPath().resolve("pending-map-export-completions"));
    }

    public void start() {
        if (started) return;
        stopping = false;
        try {
            int recoveredTemps = pendingExportCompletions.recoverTemps();
            if (recoveredTemps > 0) {
                plugin.getLogger().info("Recovered " + recoveredTemps
                        + " interrupted pending map-export completion write"
                        + (recoveredTemps == 1 ? "" : "s") + ".");
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not recover pending map-export completion writes: "
                    + exception.getMessage());
        }
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        started = true;
    }

    public void stop() {
        if (!started) return;
        stopping = true;
        HandlerList.unregisterAll(this);
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
        started = false;

        activeExports.values().forEach(exportService::abandon);
        activeExports.clear();
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        if (!started || stopping) return;
        Player player = event.getPlayer();
        if (!hasAnyWorldPermission(player)) return;
        sendCurrentWorldState(player, 0L);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!started || stopping || !CHANNEL.equals(channel)) return;
        flushPendingCompletion(player);

        final MapActionWireProtocol.Request request;
        try {
            request = MapActionWireProtocol.decodeRequest(message);
        } catch (Exception exception) {
            send(player, MapActionWireProtocol.error(exception.getMessage()));
            return;
        }

        String permissionDenial = MapActionPermissionPolicy.denial(request, player::hasPermission);
        if (permissionDenial != null) {
            send(player, MapActionWireProtocol.error(request.requestId(), permissionDenial));
            return;
        }

        switch (request) {
            case MapActionWireProtocol.CurrentWorldRequest current -> handleCurrentWorld(player, current);
            case MapActionWireProtocol.TeleportLocation teleport -> handleTeleport(player, teleport);
            case MapActionWireProtocol.ExportArea export -> handleExportArea(player, export);
        }
    }

    private void handleCurrentWorld(Player player, MapActionWireProtocol.CurrentWorldRequest request) {
        sendCurrentWorldState(player, request.requestId());
    }

    private void sendCurrentWorldState(Player player, long requestId) {
        WorldRecord world = currentManagedWorld(player);
        if (world == null) {
            send(player, MapActionWireProtocol.currentWorldCleared(requestId));
            return;
        }
        send(player, MapActionWireProtocol.currentWorld(
                requestId, world.id(), world.displayName(), world.folderName()));
    }

    private boolean hasAnyWorldPermission(Player player) {
        return player.hasPermission(TELEPORT_PERMISSION) || player.hasPermission(MANAGE_PERMISSION);
    }

    private void handleTeleport(Player player, MapActionWireProtocol.TeleportLocation request) {
        try {
            var result = teleportService.teleport(
                    player.getUniqueId(), request.worldId(), request.blockX(), request.blockZ());
            var location = result.resolved();
            send(player, MapActionWireProtocol.teleportOk(
                    request.requestId(), request.worldId(), location.x(), location.y(), location.z()));
        } catch (RuntimeException exception) {
            send(player, MapActionWireProtocol.error(request.requestId(), exception.getMessage()));
        }
    }

    private void handleExportArea(Player player, MapActionWireProtocol.ExportArea request) {
        if (stopping) {
            send(player, MapActionWireProtocol.error(request.requestId(), "LazyBuilder is shutting down"));
            return;
        }

        UUID owner = player.getUniqueId();
        if (activeExports.containsKey(owner)) {
            send(player, MapActionWireProtocol.error(
                    request.requestId(), "Previous Export Area request is still processing"));
            return;
        }

        WorldExportService.ExportTask prepared = null;
        try {
            WorldRecord current = currentManagedWorld(player);
            if (current == null || !current.id().equals(request.worldId())) {
                throw new IllegalArgumentException("Selected Area world changed; reopen the map and select the area again");
            }
            String activeDimension = vanillaDimensionId(player.getWorld().getEnvironment());
            if (activeDimension == null) {
                throw new IllegalArgumentException("Selected Area export does not support this custom dimension");
            }
            if (!activeDimension.equals(request.dimensionId())) {
                throw new IllegalArgumentException("Selected Area dimension changed; reopen the map and select the area again");
            }
            WorldAreaSelection area = WorldAreaSelection.ofCorners(
                    activeDimension, request.x1(), request.z1(), request.x2(), request.z2());
            WorldExportOptions options = ExportSettingsMapper.toOptions(request.settings());
            prepared = exportService.prepareArea(
                    request.worldId(), request.targetFormat(), request.artifactName(), area, options);
            exportService.validateSnapshotSourceForAsyncCapture(prepared);
        } catch (RuntimeException exception) {
            if (prepared != null) {
                try {
                    exportService.abandon(prepared);
                } catch (RuntimeException cleanupFailure) {
                    exception.addSuppressed(cleanupFailure);
                }
            }
            send(player, MapActionWireProtocol.error(request.requestId(), exception.getMessage()));
            return;
        }

        final WorldExportService.ExportTask task = prepared;
        activeExports.put(owner, task);
        send(player, MapActionWireProtocol.exportAccepted(request.requestId(), request.worldId()));

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Throwable captureFailure = null;
            try {
                exportService.captureSnapshotAfterValidation(task);
            } catch (Throwable exception) {
                captureFailure = exception;
            }

            // stop() owns shutdown abandonment on the Paper thread. Async workers
            // must never restore/load Paper runtime state while the plugin is stopping.
            if (stopping || !started) return;

            Throwable finalCaptureFailure = captureFailure;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (stopping || !started) {
                    completeAbandoned(owner, task);
                    return;
                }
                if (finalCaptureFailure != null) {
                    finishFailure(owner, request.requestId(), task, finalCaptureFailure);
                    return;
                }

                try {
                    exportService.resumeSourceAfterSnapshot(task);
                } catch (RuntimeException resumeFailure) {
                    finishFailure(owner, request.requestId(), task, resumeFailure);
                    return;
                }

                scheduleExportProcessing(owner, request, task);
            });
        });
    }

    private void scheduleExportProcessing(
            UUID owner,
            MapActionWireProtocol.ExportArea request,
            WorldExportService.ExportTask task
    ) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            WorldExportService.ExportResult result = null;
            Throwable failure = null;
            try {
                result = exportService.processSnapshot(task);
            } catch (Throwable exception) {
                failure = exception;
            }

            // The main-thread stop path owns cleanup after shutdown begins.
            if (stopping || !started) return;

            WorldExportService.ExportResult finalResult = result;
            Throwable finalFailure = failure;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    exportService.finish(task);
                    byte[] completion = finalFailure != null
                            ? MapActionWireProtocol.error(request.requestId(), finalFailure.getMessage())
                            : MapActionWireProtocol.exportComplete(
                                    request.requestId(),
                                    request.worldId(),
                                    finalResult.artifact().getFileName().toString(),
                                    finalResult.targetFormat());
                    deliverOrRemember(owner, completion);
                } catch (RuntimeException finishFailure) {
                    deliverOrRemember(owner, MapActionWireProtocol.error(
                            request.requestId(), finishFailure.getMessage()));
                } finally {
                    activeExports.remove(owner, task);
                }
            });
        });
    }

    private void finishFailure(
            UUID owner,
            long requestId,
            WorldExportService.ExportTask task,
            Throwable failure
    ) {
        try {
            exportService.finish(task);
        } catch (RuntimeException finishFailure) {
            failure.addSuppressed(finishFailure);
        } finally {
            activeExports.remove(owner, task);
        }
        deliverOrRemember(owner, MapActionWireProtocol.error(requestId, failure.getMessage()));
    }

    private void completeAbandoned(UUID owner, WorldExportService.ExportTask task) {
        activeExports.remove(owner, task);
        exportService.abandon(task);
    }

    private void deliverOrRemember(UUID owner, byte[] payload) {
        if (stopping || !started) return;
        Player online = plugin.getServer().getPlayer(owner);
        if (online != null && online.isOnline()) {
            send(online, payload);
            return;
        }
        try {
            pendingExportCompletions.put(owner, payload);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not persist pending map-export completion for " + owner
                    + ": " + exception.getMessage());
        }
    }

    private void flushPendingCompletion(Player player) {
        try {
            byte[] payload = pendingExportCompletions.take(player.getUniqueId());
            if (payload != null) send(player, payload);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not load pending map-export completion for "
                    + player.getUniqueId() + ": " + exception.getMessage());
        }
    }

    private void send(Player player, byte[] payload) {
        if (payload.length > MapActionWireProtocol.MAX_MESSAGE_BYTES) {
            plugin.getLogger().warning("Refusing oversized map response for " + player.getUniqueId());
            return;
        }
        player.sendPluginMessage(plugin, CHANNEL, payload);
    }

    private WorldRecord currentManagedWorld(Player player) {
        World world = Objects.requireNonNull(player, "player").getWorld();
        WorldRecord direct = registry.findByFolderName(world.getName()).orElse(null);
        if (direct != null) return direct;

        String suffix = switch (world.getEnvironment()) {
            case NETHER -> PAPER_NETHER_SUFFIX;
            case THE_END -> PAPER_END_SUFFIX;
            case NORMAL, CUSTOM -> null;
        };
        String name = world.getName();
        if (suffix == null || !name.endsWith(suffix) || name.length() <= suffix.length()) return null;
        String rootFolder = name.substring(0, name.length() - suffix.length());
        return registry.findByFolderName(rootFolder).orElse(null);
    }

    private static String vanillaDimensionId(World.Environment environment) {
        return switch (environment) {
            case NORMAL -> "minecraft:overworld";
            case NETHER -> "minecraft:the_nether";
            case THE_END -> "minecraft:the_end";
            case CUSTOM -> null;
        };
    }
}
