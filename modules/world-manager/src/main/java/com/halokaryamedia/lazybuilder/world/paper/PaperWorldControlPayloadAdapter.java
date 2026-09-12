package com.halokaryamedia.lazybuilder.world.paper;

import com.halokaryamedia.lazybuilder.LazyBuilderPlugin;
import com.halokaryamedia.lazybuilder.world.application.WorldCloneService;
import com.halokaryamedia.lazybuilder.world.application.WorldCreationService;
import com.halokaryamedia.lazybuilder.world.application.WorldDeleteService;
import com.halokaryamedia.lazybuilder.world.application.WorldDifficulty;
import com.halokaryamedia.lazybuilder.world.application.WorldExportService;
import com.halokaryamedia.lazybuilder.world.application.WorldGameMode;
import com.halokaryamedia.lazybuilder.world.application.WorldImportService;
import com.halokaryamedia.lazybuilder.world.application.WorldLifecycleService;
import com.halokaryamedia.lazybuilder.world.application.WorldRuntimeService;
import com.halokaryamedia.lazybuilder.world.application.WorldSettingsService;
import com.halokaryamedia.lazybuilder.world.application.WorldSettingsSnapshot;
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
    private final WorldSettingsService settingsService;
    private final WorldExportService exportService;
    private final WorldImportService importService;
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
            WorldDeleteService deleteService,
            WorldSettingsService settingsService,
            WorldExportService exportService,
            WorldImportService importService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.creation = Objects.requireNonNull(creation, "creation");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.teleport = Objects.requireNonNull(teleport, "teleport");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.cloneService = Objects.requireNonNull(cloneService, "cloneService");
        this.deleteService = Objects.requireNonNull(deleteService, "deleteService");
        this.settingsService = Objects.requireNonNull(settingsService, "settingsService");
        this.exportService = Objects.requireNonNull(exportService, "exportService");
        this.importService = Objects.requireNonNull(importService, "importService");
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
        if (request instanceof WorldControlWireProtocol.ExportWorld export) {
            handleExport(player, export);
            return;
        }
        if (request instanceof WorldControlWireProtocol.ImportWorld importWorld) {
            handleImport(player, importWorld);
            return;
        }

        try {
            send(player, WorldControlWireProtocol.encodeResponse(handle(player, request)));
        } catch (IOException | RuntimeException exception) {
            send(player, WorldControlWireProtocol.error(exception.getMessage()));
        }
    }

    private WorldControlWireProtocol.Response handle(Player player, WorldControlWireProtocol.Request request) {
        return switch (request) {
            case WorldControlWireProtocol.ListWorlds ignored -> {
                requireAnyWorldPermission(player);
                List<WorldControlWireProtocol.WorldSummary> worlds = registry.all().stream().map(this::summary).toList();
                yield new WorldControlWireProtocol.WorldList(worlds);
            }
            case WorldControlWireProtocol.CreateWorld create -> {
                requireManage(player);
                WorldRecord world = creation.create(create.folderName(), create.displayName(),
                        WorldKind.valueOf(create.kind().strip().toUpperCase(Locale.ROOT)));
                yield new WorldControlWireProtocol.WorldChanged("CREATE", summary(world));
            }
            case WorldControlWireProtocol.LoadWorld load -> {
                requireManage(player);
                yield new WorldControlWireProtocol.WorldChanged("LOAD", summary(runtime.load(new WorldId(load.worldId()))));
            }
            case WorldControlWireProtocol.UnloadWorld unload -> {
                requireManage(player);
                yield new WorldControlWireProtocol.WorldChanged("UNLOAD", summary(runtime.unload(new WorldId(unload.worldId()))));
            }
            case WorldControlWireProtocol.TeleportWorld teleportRequest -> {
                requireTeleport(player);
                yield new WorldControlWireProtocol.TeleportOk(summary(
                        teleport.teleportToWorld(player.getUniqueId(), new WorldId(teleportRequest.worldId()))));
            }
            case WorldControlWireProtocol.ArchiveWorld archive -> {
                requireManage(player);
                yield new WorldControlWireProtocol.WorldChanged("ARCHIVE", summary(lifecycle.archive(new WorldId(archive.worldId()))));
            }
            case WorldControlWireProtocol.RestoreWorld restore -> {
                requireManage(player);
                yield new WorldControlWireProtocol.WorldChanged("RESTORE", summary(lifecycle.restore(new WorldId(restore.worldId()))));
            }
            case WorldControlWireProtocol.GetSettings settings -> {
                requireManage(player);
                yield settingsSummary(settingsService.snapshot(new WorldId(settings.worldId())));
            }
            case WorldControlWireProtocol.SetAutoLoad setting -> {
                requireManage(player);
                settingsService.setAutoLoad(new WorldId(setting.worldId()), setting.enabled());
                yield settingsSummary(settingsService.snapshot(new WorldId(setting.worldId())));
            }
            case WorldControlWireProtocol.SetDefaultMode setting -> {
                requireManage(player);
                settingsService.setDefaultGameMode(new WorldId(setting.worldId()),
                        WorldGameMode.valueOf(setting.gameMode().toUpperCase(Locale.ROOT)));
                yield settingsSummary(settingsService.snapshot(new WorldId(setting.worldId())));
            }
            case WorldControlWireProtocol.SetDifficulty setting -> {
                requireManage(player);
                settingsService.setDifficulty(new WorldId(setting.worldId()),
                        WorldDifficulty.valueOf(setting.difficulty().toUpperCase(Locale.ROOT)));
                yield settingsSummary(settingsService.snapshot(new WorldId(setting.worldId())));
            }
            case WorldControlWireProtocol.SetPvp setting -> {
                requireManage(player);
                settingsService.setPvp(new WorldId(setting.worldId()), setting.enabled());
                yield settingsSummary(settingsService.snapshot(new WorldId(setting.worldId())));
            }
            case WorldControlWireProtocol.ResetBuildReady setting -> {
                requireManage(player);
                yield settingsSummary(settingsService.resetToBuildReady(new WorldId(setting.worldId())));
            }
            case WorldControlWireProtocol.SetSpawnHere setting -> {
                requireManage(player);
                settingsService.setSpawnToPlayer(player.getUniqueId(), new WorldId(setting.worldId()));
                yield settingsSummary(settingsService.snapshot(new WorldId(setting.worldId())));
            }
            case WorldControlWireProtocol.CloneWorld ignored -> throw new IllegalStateException("Clone must use async path");
            case WorldControlWireProtocol.DeleteWorld ignored -> throw new IllegalStateException("Delete must use async path");
            case WorldControlWireProtocol.ExportWorld ignored -> throw new IllegalStateException("Export must use async path");
            case WorldControlWireProtocol.ImportWorld ignored -> throw new IllegalStateException("Import must use async path");
        };
    }

    private boolean beginHeavy(Player player) {
        try { requireManage(player); }
        catch (RuntimeException exception) { send(player, WorldControlWireProtocol.error(exception.getMessage())); return false; }
        if (!heavyInFlight.add(player.getUniqueId())) {
            send(player, WorldControlWireProtocol.error("Another World Manager file operation is still processing"));
            return false;
        }
        return true;
    }

    private void handleClone(Player player, WorldControlWireProtocol.CloneWorld request) {
        if (!beginHeavy(player)) return;
        UUID owner = player.getUniqueId();
        final WorldCloneService.CloneTask task;
        try { task = cloneService.prepare(new WorldId(request.sourceWorldId()), request.destinationFolder(), request.displayName()); }
        catch (RuntimeException exception) { heavyInFlight.remove(owner); send(player, WorldControlWireProtocol.error(exception.getMessage())); return; }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            WorldRecord result = null; Throwable failure = null;
            try { result = cloneService.executeFilePhase(task); } catch (Throwable exception) { failure = exception; }
            WorldRecord finalResult = result; Throwable finalFailure = failure;
            plugin.getServer().getScheduler().runTask(plugin, () -> completeHeavy(owner, () -> {
                Throwable outcome = finalFailure;
                try { cloneService.finish(task); } catch (Throwable finishFailure) { outcome = combine(outcome, finishFailure); }
                if (outcome != null) return WorldControlWireProtocol.error(outcome.getMessage());
                return encode(new WorldControlWireProtocol.WorldChanged("CLONE", summary(finalResult)));
            }));
        });
    }

    private void handleDelete(Player player, WorldControlWireProtocol.DeleteWorld request) {
        if (!beginHeavy(player)) return;
        UUID owner = player.getUniqueId();
        final WorldDeleteService.DeleteTask task;
        final WorldControlWireProtocol.WorldSummary deletedSummary;
        try {
            task = deleteService.prepare(new WorldId(request.worldId()), request.typedFolderName());
            deletedSummary = summary(task.world());
        } catch (RuntimeException exception) { heavyInFlight.remove(owner); send(player, WorldControlWireProtocol.error(exception.getMessage())); return; }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Throwable failure = null;
            try { deleteService.executeFilePhase(task); } catch (Throwable exception) { failure = exception; }
            Throwable finalFailure = failure;
            plugin.getServer().getScheduler().runTask(plugin, () -> completeHeavy(owner, () -> {
                Throwable outcome = finalFailure;
                try { deleteService.finish(task); } catch (Throwable finishFailure) { outcome = combine(outcome, finishFailure); }
                if (outcome != null) return WorldControlWireProtocol.error(outcome.getMessage());
                return encode(new WorldControlWireProtocol.WorldChanged("DELETE", deletedSummary));
            }));
        });
    }

    private void handleImport(Player player, WorldControlWireProtocol.ImportWorld request) {
        if (!beginHeavy(player)) return;
        UUID owner = player.getUniqueId();
        final WorldImportService.ImportTask task;
        try { task = importService.prepare(request.artifactName(), request.destinationFolder(), request.displayName()); }
        catch (RuntimeException exception) { heavyInFlight.remove(owner); send(player, WorldControlWireProtocol.error(exception.getMessage())); return; }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            WorldRecord result = null; Throwable failure = null;
            try { result = importService.executeFilePhase(task); } catch (Throwable exception) { failure = exception; }
            WorldRecord finalResult = result; Throwable finalFailure = failure;
            plugin.getServer().getScheduler().runTask(plugin, () -> completeHeavy(owner, () -> {
                Throwable outcome = finalFailure;
                try { importService.finish(task); } catch (Throwable finishFailure) { outcome = combine(outcome, finishFailure); }
                if (outcome != null) return WorldControlWireProtocol.error(outcome.getMessage());
                return encode(new WorldControlWireProtocol.WorldChanged("IMPORT", summary(finalResult)));
            }));
        });
    }

    private void handleExport(Player player, WorldControlWireProtocol.ExportWorld request) {
        if (!beginHeavy(player)) return;
        UUID owner = player.getUniqueId();
        final WorldExportService.ExportTask task;
        try { task = exportService.prepare(new WorldId(request.worldId()), request.targetFormat(), request.artifactName()); }
        catch (RuntimeException exception) { heavyInFlight.remove(owner); send(player, WorldControlWireProtocol.error(exception.getMessage())); return; }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Throwable captureFailure = null;
            try { exportService.captureSnapshot(task); } catch (Throwable exception) { captureFailure = exception; }
            Throwable finalCaptureFailure = captureFailure;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (finalCaptureFailure != null) {
                    completeHeavy(owner, () -> finishExportFailure(task, finalCaptureFailure));
                    return;
                }
                try { exportService.resumeSourceAfterSnapshot(task); }
                catch (Throwable resumeFailure) {
                    completeHeavy(owner, () -> finishExportFailure(task, resumeFailure));
                    return;
                }
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    WorldExportService.ExportResult result = null; Throwable failure = null;
                    try { result = exportService.processSnapshot(task); } catch (Throwable exception) { failure = exception; }
                    WorldExportService.ExportResult finalResult = result; Throwable finalFailure = failure;
                    plugin.getServer().getScheduler().runTask(plugin, () -> completeHeavy(owner, () -> {
                        Throwable outcome = finalFailure;
                        try { exportService.finish(task); } catch (Throwable finishFailure) { outcome = combine(outcome, finishFailure); }
                        if (outcome != null) return WorldControlWireProtocol.error(outcome.getMessage());
                        return encode(new WorldControlWireProtocol.ExportReady(
                                request.worldId(), finalResult.artifact().getFileName().toString(), finalResult.targetFormat()));
                    }));
                });
            });
        });
    }

    private byte[] finishExportFailure(WorldExportService.ExportTask task, Throwable failure) {
        Throwable outcome = failure;
        try { exportService.finish(task); } catch (Throwable finishFailure) { outcome = combine(outcome, finishFailure); }
        return WorldControlWireProtocol.error(outcome.getMessage());
    }

    private void completeHeavy(UUID owner, ResponseSupplier responseSupplier) {
        try {
            Player online = plugin.getServer().getPlayer(owner);
            byte[] response = responseSupplier.get();
            if (online != null && online.isOnline()) send(online, response);
        } finally {
            heavyInFlight.remove(owner);
        }
    }

    private static Throwable combine(Throwable first, Throwable next) {
        if (first == null) return next;
        first.addSuppressed(next);
        return first;
    }

    private WorldControlWireProtocol.WorldSummary summary(WorldRecord world) {
        return new WorldControlWireProtocol.WorldSummary(
                world.id().value(), world.folderName(), world.displayName(), world.kind().name(),
                world.lifecycle().name(), runtime.state(world.id()).name(), world.autoLoad(), world.defaultGameMode());
    }

    private static WorldControlWireProtocol.SettingsSnapshot settingsSummary(WorldSettingsSnapshot snapshot) {
        var runtime = snapshot.runtime();
        var spawn = runtime.spawn();
        return new WorldControlWireProtocol.SettingsSnapshot(
                snapshot.world().id().value(), snapshot.world().autoLoad(), snapshot.defaultGameMode().name(),
                runtime.difficulty().name(), runtime.pvpEnabled(), runtime.weather().name(), runtime.timeOfDayTicks(),
                spawn.x(), spawn.y(), spawn.z());
    }

    private void requireAnyWorldPermission(Player player) {
        if (!player.hasPermission(MANAGE_PERMISSION) && !player.hasPermission(TELEPORT_PERMISSION)) {
            throw new IllegalStateException("Missing LazyBuilder world permission");
        }
    }

    private static void requirePermission(Player player, String permission) {
        if (!player.hasPermission(permission)) throw new IllegalStateException("Missing permission: " + permission);
    }
    private static void requireManage(Player player) { requirePermission(player, MANAGE_PERMISSION); }
    private static void requireTeleport(Player player) { requirePermission(player, TELEPORT_PERMISSION); }

    private static byte[] encode(WorldControlWireProtocol.Response response) {
        try { return WorldControlWireProtocol.encodeResponse(response); }
        catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }

    private void send(Player player, byte[] payload) {
        if (payload.length > WorldControlWireProtocol.MAX_MESSAGE_BYTES) {
            plugin.getLogger().warning("Refusing oversized world-control response for " + player.getUniqueId());
            return;
        }
        player.sendPluginMessage(plugin, CHANNEL, payload);
    }

    @FunctionalInterface
    private interface ResponseSupplier { byte[] get(); }
}
