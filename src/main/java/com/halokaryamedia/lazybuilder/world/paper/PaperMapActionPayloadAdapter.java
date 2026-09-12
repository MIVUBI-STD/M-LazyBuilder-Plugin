package com.halokaryamedia.lazybuilder.world.paper;

import com.halokaryamedia.lazybuilder.LazyBuilderPlugin;
import com.halokaryamedia.lazybuilder.world.application.WorldAreaSelection;
import com.halokaryamedia.lazybuilder.world.application.WorldExportService;
import com.halokaryamedia.lazybuilder.world.application.WorldLocationTeleportService;
import com.halokaryamedia.lazybuilder.world.map.MapActionWireProtocol;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Thin Paper transport for map/Xaero intents; domain authority stays in application services. */
public final class PaperMapActionPayloadAdapter implements PluginMessageListener {
    public static final String CHANNEL = "lazybuilder:map";
    public static final String TELEPORT_PERMISSION = "lazybuilder.world.teleport";
    public static final String MANAGE_PERMISSION = "lazybuilder.world.manage";

    private final LazyBuilderPlugin plugin;
    private final WorldRegistry registry;
    private final WorldLocationTeleportService teleportService;
    private final WorldExportService exportService;
    private final Set<UUID> exportInFlight = ConcurrentHashMap.newKeySet();
    private volatile boolean started;

    public PaperMapActionPayloadAdapter(
            LazyBuilderPlugin plugin,
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
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        started = true;
    }

    public void stop() {
        if (!started) return;
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
        exportInFlight.clear();
        started = false;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!started || !CHANNEL.equals(channel)) return;

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
        if (!player.hasPermission(TELEPORT_PERMISSION) && !player.hasPermission(MANAGE_PERMISSION)) {
            send(player, MapActionWireProtocol.error("Missing LazyBuilder world permission"));
            return;
        }
        WorldRecord world = registry.findByFolderName(player.getWorld().getName()).orElse(null);
        if (world == null) {
            send(player, MapActionWireProtocol.error("Current world is not managed by LazyBuilder"));
            return;
        }
        send(player, MapActionWireProtocol.currentWorld(world.id(), world.displayName(), world.folderName()));
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
            send(player, MapActionWireProtocol.exportAccepted(request.worldId()));
        } catch (RuntimeException exception) {
            exportInFlight.remove(owner);
            send(player, MapActionWireProtocol.error(exception.getMessage()));
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            WorldExportService.ExportResult result = null;
            Throwable failure = null;
            try {
                result = exportService.executeFilePhase(task);
            } catch (Throwable exception) {
                failure = exception;
            }

            WorldExportService.ExportResult finalResult = result;
            Throwable finalFailure = failure;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    exportService.finish(task);
                    Player online = plugin.getServer().getPlayer(owner);
                    if (online == null || !online.isOnline()) return;
                    if (finalFailure != null) {
                        send(online, MapActionWireProtocol.error(finalFailure.getMessage()));
                    } else {
                        send(online, MapActionWireProtocol.exportComplete(
                                request.worldId(),
                                finalResult.artifact().getFileName().toString(),
                                finalResult.targetFormat()
                        ));
                    }
                } catch (RuntimeException finishFailure) {
                    Player online = plugin.getServer().getPlayer(owner);
                    if (online != null && online.isOnline()) {
                        send(online, MapActionWireProtocol.error(finishFailure.getMessage()));
                    }
                } finally {
                    exportInFlight.remove(owner);
                }
            });
        });
    }

    private void send(Player player, byte[] payload) {
        if (payload.length > MapActionWireProtocol.MAX_MESSAGE_BYTES) {
            plugin.getLogger().warning("Refusing oversized map response for " + player.getUniqueId());
            return;
        }
        player.sendPluginMessage(plugin, CHANNEL, payload);
    }
}
