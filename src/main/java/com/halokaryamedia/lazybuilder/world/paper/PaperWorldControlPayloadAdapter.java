package com.halokaryamedia.lazybuilder.world.paper;

import com.halokaryamedia.lazybuilder.LazyBuilderPlugin;
import com.halokaryamedia.lazybuilder.world.application.WorldCloneService;
import com.halokaryamedia.lazybuilder.world.application.WorldCreationService;
import com.halokaryamedia.lazybuilder.world.application.WorldDeleteService;
import com.halokaryamedia.lazybuilder.world.application.WorldLifecycleService;
import com.halokaryamedia.lazybuilder.world.application.WorldRuntimeService;
import com.halokaryamedia.lazybuilder.world.application.WorldTeleportService;
import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;
import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import com.halokaryamedia.lazybuilder.world.registry.WorldKind;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Thin Paper adapter for the general World Manager control surface. */
public final class PaperWorldControlPayloadAdapter implements PluginMessageListener {
    public static final String CHANNEL = "lazybuilder:world";
    public static final String MANAGE_PERMISSION = "lazybuilder.world.manage";
    public static final String TELEPORT_PERMISSION = "lazybuilder.world.teleport";

    private final LazyBuilderPlugin plugin;
    private final WorldRegistry registry;
    private final WorldCreationService creation;
    private final WorldRuntimeService runtime;
    private final WorldTeleportService teleport;
    private final WorldLifecycleService lifecycle;
    private final WorldCloneService cloneService;
    private final WorldDeleteService deleteService;
    private final Set<UUID> heavyInFlight = ConcurrentHashMap.newKeySet();
    private volatile boolean started;
    private volatile boolean stopping;

    public PaperWorldControlPayloadAdapter(
            LazyBuilderPlugin plugin,
            WorldRegistry registry,
            WorldCreationService creation,
            WorldRuntimeService runtime,
            WorldTeleportService teleport,
            WorldLifecycleService lifecycle,
            WorldCloneService cloneService,
            WorldDeleteService deleteService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.creation = Objects.requireNonNull(creation, "creation");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.teleport = Objects.requireNonNull(teleport, "teleport");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.cloneService = Objects.requireNonNull(cloneService, "cloneService");
        this.deleteService = Objects.requireNonNull(deleteService, "deleteService");
    }

    public void start() {
        if (started) return;
        stopping = false;
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        started = true;
    }

    public void stop() {
        if (!started) return;
        stopping = true;
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
        started = false;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!started || stopping || !CHANNEL.equals(channel)) return;

        final WorldControlWireProtocol.Request request;
        try {
            request = WorldControlWireProtocol.decodeRequest(message);
        } catch (IOException | RuntimeException exception) {
            send(player, WorldControlWireProtocol.error(exception.getMessage()));
            return;
        }

        if (request instanceof WorldControlWireProtocol.CloneWorld clone) {
            handleClone(player, clone);
            return;
        }
        if (request instanceof WorldControlWireProtocol.DeleteWorld delete) {
            handleDelete(player, delete);
            return;
        }

        try {
            WorldControlWireProtocol.Response response = handle(player, request);
            send(player, WorldControlWireProtocol.encodeResponse(response));
        } catch (IOException | RuntimeException exception) {
            send(player, WorldControlWireProtocol.error(exception.getMessage()));
        }
    }

    private WorldControlWireProtocol.Response handle(Player player, WorldControlWireProtocol.Request request) {
        return switch (request) {
            case WorldControlWireProtocol.ListWorlds ignored -> {
                requireAnyWorldPermission(player);
                List<WorldControlWireProtocol.WorldSummary> worlds = registry.all().stream()
                        .map(this::summary)
                        .toList();
                yield new WorldControlWireProtocol.WorldList(worlds);
            }
            case WorldControlWireProtocol.CreateWorld create -> {
                requireManage(player);
                WorldKind kind = WorldKind.valueOf(create.kind().strip().toUpperCase(Locale.ROOT));
                WorldRecord world = creation.create(create.folderName(), create.displayName(), kind);
                yield new WorldControlWireProtocol.WorldChanged("CREATE", summary(world));
            }
            case WorldControlWireProtocol.LoadWorld load -> {
                requireManage(player);
                WorldRecord world = runtime.load(new WorldId(load.worldId()));
                yield new WorldControlWireProtocol.WorldChanged("LOAD", summary(world));
            }
            case WorldControlWireProtocol.UnloadWorld unload -> {
                requireManage(player);
                WorldRecord world = runtime.unload(new WorldId(unload.worldId()));
                yield new WorldControlWireProtocol.WorldChanged("UNLOAD", summary(world));
            }
            case WorldControlWireProtocol.TeleportWorld teleportRequest -> {
                requireTeleport(player);
                WorldRecord world = teleport.teleportToWorld(
                        player.getUniqueId(), new WorldId(teleportRequest.worldId()));
                yield new WorldControlWireProtocol.TeleportOk(summary(world));
            }
            case WorldControlWireProtocol.ArchiveWorld archive -> {
                requireManage(player);
                WorldRecord world = lifecycle.archive(new WorldId(archive.worldId()));
                yield new WorldControlWireProtocol.WorldChanged("ARCHIVE", summary(world));
            }
            case WorldControlWireProtocol.RestoreWorld restore -> {
                requireManage(player);
                WorldRecord world = lifecycle.restore(new WorldId(restore.worldId()));
                yield new WorldControlWireProtocol.WorldChanged("RESTORE", summary(world));
            }
            case WorldControlWireProtocol.CloneWorld ignored -> throw new IllegalStateException("Clone must use async path");
            case WorldControlWireProtocol.DeleteWorld ignored -> throw new IllegalStateException("Delete must use async path");
        };
    }

    private void handleClone(Player player, WorldControlWireProtocol.CloneWorld request) {
        try {
            requireManage(player);
        } catch (RuntimeException exception) {
            send(player, WorldControlWireProtocol.error(exception.getMessage()));
            return;
        }
        UUID owner = player.getUniqueId();
        if (!heavyInFlight.add(owner)) {
            send(player, WorldControlWireProtocol.error("Another World Manager file operation is still processing"));
            return;
        }

        final WorldCloneService.CloneTask task;
        try {
            task = cloneService.prepare(
                    new WorldId(request.sourceWorldId()), request.destinationFolder(), request.displayName());
        } catch (RuntimeException exception) {
            heavyInFlight.remove(owner);
            send(player, WorldControlWireProtocol.error(exception.getMessage()));
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            WorldRecord result = null;
            Throwable failure = null;
            try {
                result = cloneService.executeFilePhase(task);
            } catch (Throwable exception) {
                failure = exception;
            }
            WorldRecord finalResult = result;
            Throwable finalFailure = failure;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Throwable outcome = finalFailure;
                try {
                    cloneService.finish(task);
                } catch (Throwable finishFailure) {
                    if (outcome == null) outcome = finishFailure;
                    else outcome.addSuppressed(finishFailure);
                }
                try {
                    Player online = plugin.getServer().getPlayer(owner);
                    if (online == null || !online.isOnline()) return;
                    if (outcome != null) send(online, WorldControlWireProtocol.error(outcome.getMessage()));
                    else send(online, encode(new WorldControlWireProtocol.WorldChanged("CLONE", summary(finalResult))));
                } finally {
                    heavyInFlight.remove(owner);
                }
            });
        });
    }

    private void handleDelete(Player player, WorldControlWireProtocol.DeleteWorld request) {
        try {
            requireManage(player);
        } catch (RuntimeException exception) {
            send(player, WorldControlWireProtocol.error(exception.getMessage()));
            return;
        }
        UUID owner = player.getUniqueId();
        if (!heavyInFlight.add(owner)) {
            send(player, WorldControlWireProtocol.error("Another World Manager file operation is still processing"));
            return;
        }

        final WorldDeleteService.DeleteTask task;
        final WorldControlWireProtocol.WorldSummary deletedSummary;
        try {
            task = deleteService.prepare(new WorldId(request.worldId()), request.typedFolderName());
            deletedSummary = summary(task.world());
        } catch (RuntimeException exception) {
            heavyInFlight.remove(owner);
            send(player, WorldControlWireProtocol.error(exception.getMessage()));
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Throwable failure = null;
            try {
                deleteService.executeFilePhase(task);
            } catch (Throwable exception) {
                failure = exception;
            }
            Throwable finalFailure = failure;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Throwable outcome = finalFailure;
                try {
                    deleteService.finish(task);
                } catch (Throwable finishFailure) {
                    if (outcome == null) outcome = finishFailure;
                    else outcome.addSuppressed(finishFailure);
                }
                try {
                    Player online = plugin.getServer().getPlayer(owner);
                    if (online == null || !online.isOnline()) return;
                    if (outcome != null) send(online, WorldControlWireProtocol.error(outcome.getMessage()));
                    else send(online, encode(new WorldControlWireProtocol.WorldChanged("DELETE", deletedSummary)));
                } finally {
                    heavyInFlight.remove(owner);
                }
            });
        });
    }

    private WorldControlWireProtocol.WorldSummary summary(WorldRecord world) {
        return new WorldControlWireProtocol.WorldSummary(
                world.id().value(),
                world.folderName(),
                world.displayName(),
                world.kind().name(),
                world.lifecycle().name(),
                runtime.state(world.id()).name(),
                world.autoLoad(),
                world.defaultGameMode()
        );
    }

    private void requireAnyWorldPermission(Player player) {
        if (!player.hasPermission(MANAGE_PERMISSION) && !player.hasPermission(TELEPORT_PERMISSION)) {
            throw new IllegalStateException("Missing LazyBuilder world permission");
        }
    }

    private static void requirePermission(Player player, String permission) {
        if (!player.hasPermission(permission)) {
            throw new IllegalStateException("Missing permission: " + permission);
        }
    }

    private static void requireManage(Player player) { requirePermission(player, MANAGE_PERMISSION); }
    private static void requireTeleport(Player player) { requirePermission(player, TELEPORT_PERMISSION); }

    private static byte[] encode(WorldControlWireProtocol.Response response) {
        try {
            return WorldControlWireProtocol.encodeResponse(response);
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private void send(Player player, byte[] payload) {
        if (payload.length > WorldControlWireProtocol.MAX_MESSAGE_BYTES) {
            plugin.getLogger().warning("Refusing oversized world-control response for " + player.getUniqueId());
            return;
        }
        player.sendPluginMessage(plugin, CHANNEL, payload);
    }
}
