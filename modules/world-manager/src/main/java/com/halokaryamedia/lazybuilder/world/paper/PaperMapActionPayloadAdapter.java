package com.halokaryamedia.lazybuilder.world.paper;

import com.halokaryamedia.lazybuilder.world.application.WorldAreaSelection;
import com.halokaryamedia.lazybuilder.world.application.WorldExportService;
import com.halokaryamedia.lazybuilder.world.application.WorldLocationTeleportService;
import com.halokaryamedia.lazybuilder.world.map.MapActionWireProtocol;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Thin Paper transport for map intents; domain authority stays in application services. */
public final class PaperMapActionPayloadAdapter implements PluginMessageListener, Listener {
    public static final String CHANNEL = "lazybuilder:map";
    public static final String TELEPORT_PERMISSION = "lazybuilder.world.teleport";
    public static final String MANAGE_PERMISSION = "lazybuilder.world.manage";

    private final JavaPlugin plugin;
    private final WorldRegistry registry;
    private final WorldLocationTeleportService teleportService;
    private final WorldExportService exportService;
    private final Set<UUID> exportInFlight = ConcurrentHashMap.newKeySet();
    private final Map<UUID, WorldExportService.ExportTask> activeExports = new ConcurrentHashMap<>();
    private final Map<UUID, byte[]> pendingExportCompletion = new ConcurrentHashMap<>();
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
    }

    public void start() {
        if (started) return;
        stopping = false;
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
        exportInFlight.clear();
        pendingExportCompletion.clear();
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        if (!started || stopping) return;
        Player player = event.getPlayer();
        if (!hasAnyWorldPermission(player)) return;
        sendCurrentWorldState(player);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!started || stopping || !CHANNEL.equals(channel)) return;

        // Area export can finish while its owner is disconnected. The next map request
        // after reconnect replays at most one bounded completion before the fresh response.
        flushPendingCompletion(player);

        final MapActionWireProtocol.Request request;
        try {
            request = MapActionWireProtocol.decodeRequest(message);
        } catch (Exception exception) {
            send(player, MapActionWireProtocol.error(exception.getMessage()));
            return;
        }

        switch (request) {
            case MapActionWireProtocol.CurrentWorldRequest ignored -> handleCurrentWorld(player);
            case MapActionWireProtocol.TeleportLocation teleport -> handleTeleport(player, teleport);
            case MapActionWireProtocol.ExportArea export -> handleExportArea(player, export);
        }
    }

    private void handleCurrentWorld(Player player) {
        if (!hasAnyWorldPermission(player)) {
            send(player, MapActionWireProtocol.error("Missing LazyBuilder world permission"));
            return;
        }
        sendCurrentWorldState(player);
    }

    private void sendCurrentWorldState(Player player) {
        WorldRecord world = registry.findByFolderName(player.getWorld().getName()).orElse(null);
        if (world == null) {
            send(player, MapActionWireProtocol.currentWorldCleared());
            return;
        }
        send(player, MapActionWireProtocol.currentWorld(world.id(), world.displayName(), world.folderName()));
    }

    private boolean hasAnyWorldPermission(Player player) {
        return player.hasPermission(TELEPORT_PERMISSION) || player.hasPermission(MANAGE_PERMISSION);
    }

    private void handleTeleport(Player player, MapActionWireProtocol.TeleportLocation request) {
        if (!player.hasPermission(TELEPORT_PERMISSION)) {
            send(player, MapActionWireProtocol.error("Missing permission: " + TELEPORT_PERMISSION));
            return;
        }
        try {
            var result = teleportService.teleport(
                    player.getUniqueId(), request.worldId(), request.blockX(), request.blockZ());
            var location = result.resolved();
            send(player, MapActionWireProtocol.teleportOk(
                    request.worldId(), location.x(), location.y(), location.z()));
        } catch (RuntimeException exception) {
            send(player, MapActionWireProtocol.error(exception.getMessage()));
        }
    }

    private void handleExportArea(Player player, MapActionWireProtocol.ExportArea request) {
        if (!player.hasPermission(MANAGE_PERMISSION)) {
            send(player, MapActionWireProtocol.error("Missing permission: " + MANAGE_PERMISSION));
            return;
        }
        if (stopping) {
            send(player, MapActionWireProtocol.error("LazyBuilder is shutting down"));
            return;
        }

        UUID owner = player.getUniqueId();
        if (!exportInFlight.add(owner)) {
            send(player, MapActionWireProtocol.error("Previous Export Area request is still processing"));
            return;
        }

        final WorldExportService.ExportTask task;
        try {
            WorldAreaSelection area = WorldAreaSelection.ofCorners(
                    request.x1(), request.z1(), request.x2(), request.z2());
            task = exportService.prepareArea(
                    request.worldId(), request.targetFormat(), request.artifactName(), area);
            activeExports.put(owner, task);
            send(player, MapActionWireProtocol.exportAccepted(request.worldId()));
        } catch (RuntimeException exception) {
            exportInFlight.remove(owner);
            send(player, MapActionWireProtocol.error(exception.getMessage()));
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Throwable captureFailure = null;
            try {
                exportService.captureSnapshot(task);
            } catch (Throwable exception) {
                captureFailure = exception;
            }

            if (stopping || !started) {
                completeAbandoned(owner, task);
                return;
            }

            Throwable finalCaptureFailure = captureFailure;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (stopping || !started) {
                    completeAbandoned(owner, task);
                    return;
                }
                if (finalCaptureFailure != null) {
                    finishFailure(owner, task, finalCaptureFailure);
                    return;
                }

                try {
                    exportService.resumeSourceAfterSnapshot(task);
                } catch (RuntimeException resumeFailure) {
                    finishFailure(owner, task, resumeFailure);
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

            if (stopping || !started) {
                completeAbandoned(owner, task);
                return;
            }

            WorldExportService.ExportResult finalResult = result;
            Throwable finalFailure = failure;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    exportService.finish(task);
                    byte[] completion = finalFailure != null
                            ? MapActionWireProtocol.error(finalFailure.getMessage())
                            : MapActionWireProtocol.exportComplete(
                                    request.worldId(),
                                    finalResult.artifact().getFileName().toString(),
                                    finalResult.targetFormat());
                    deliverOrRemember(owner, completion);
                } catch (RuntimeException finishFailure) {
                    deliverOrRemember(owner, MapActionWireProtocol.error(finishFailure.getMessage()));
                } finally {
                    activeExports.remove(owner, task);
                    exportInFlight.remove(owner);
                }
            });
        });
    }

    private void finishFailure(UUID owner, WorldExportService.ExportTask task, Throwable failure) {
        try {
            exportService.finish(task);
        } catch (RuntimeException finishFailure) {
            failure.addSuppressed(finishFailure);
        } finally {
            activeExports.remove(owner, task);
            exportInFlight.remove(owner);
        }
        deliverOrRemember(owner, MapActionWireProtocol.error(failure.getMessage()));
    }

    private void completeAbandoned(UUID owner, WorldExportService.ExportTask task) {
        activeExports.remove(owner, task);
        exportInFlight.remove(owner);
        exportService.abandon(task);
    }

    private void deliverOrRemember(UUID owner, byte[] payload) {
        if (stopping || !started) return;
        Player online = plugin.getServer().getPlayer(owner);
        if (online != null && online.isOnline()) {
            send(online, payload);
            return;
        }
        pendingExportCompletion.put(owner, payload);
    }

    private void flushPendingCompletion(Player player) {
        byte[] payload = pendingExportCompletion.remove(player.getUniqueId());
        if (payload != null) send(player, payload);
    }

    private void send(Player player, byte[] payload) {
        if (payload.length > MapActionWireProtocol.MAX_MESSAGE_BYTES) {
            plugin.getLogger().warning("Refusing oversized map response for " + player.getUniqueId());
            return;
        }
        player.sendPluginMessage(plugin, CHANNEL, payload);
    }
}
