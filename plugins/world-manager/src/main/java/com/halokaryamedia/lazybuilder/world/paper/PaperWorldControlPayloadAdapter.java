package com.halokaryamedia.lazybuilder.world.paper;

import com.halokaryamedia.lazybuilder.world.application.WorldCreationService;
import com.halokaryamedia.lazybuilder.world.application.WorldDifficulty;
import com.halokaryamedia.lazybuilder.world.application.WorldExportOptions;
import com.halokaryamedia.lazybuilder.world.application.WorldExportService;
import com.halokaryamedia.lazybuilder.world.application.WorldGameMode;
import com.halokaryamedia.lazybuilder.world.application.WorldImportService;
import com.halokaryamedia.lazybuilder.world.application.WorldLifecycleService;
import com.halokaryamedia.lazybuilder.world.application.WorldSettingsService;
import com.halokaryamedia.lazybuilder.world.application.WorldSettingsSnapshot;
import com.halokaryamedia.lazybuilder.world.application.WorldTeleportService;
import com.halokaryamedia.lazybuilder.world.conversion.ConversionUpdateService;
import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;
import com.halokaryamedia.lazybuilder.world.files.WorldImportArtifactStore;
import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import com.halokaryamedia.lazybuilder.world.registry.WorldKind;
import com.halokaryamedia.lazybuilder.world.registry.WorldRecord;
import com.halokaryamedia.lazybuilder.world.registry.WorldRegistry;
import com.halokaryamedia.lazybuilder.world.transfer.TransferSessionService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Thin Paper adapter for the Fabric World Manager control surface. */
public final class PaperWorldControlPayloadAdapter implements PluginMessageListener, Listener {
    public static final String CHANNEL = "lazybuilder:world";
    public static final String MANAGE_PERMISSION = "lazybuilder.world.manage";
    public static final String TELEPORT_PERMISSION = "lazybuilder.world.teleport";

    private final JavaPlugin plugin;
    private final WorldRegistry registry;
    private final WorldCreationService creation;
    private final WorldTeleportService teleport;
    private final WorldLifecycleService lifecycle;
    private final WorldSettingsService settingsService;
    private final WorldExportService exportService;
    private final WorldImportService importService;
    private final ConversionUpdateService conversionUpdates;
    private final TransferSessionService transfers;
    private final WorldHeavyOperationOrchestrator heavyOperations;
    private final Set<UUID> heavyInFlight = ConcurrentHashMap.newKeySet();
    private final Map<UUID, byte[]> pendingHeavyCompletion = new ConcurrentHashMap<>();
    private final Map<UUID, String> reviewedImportArtifacts = new ConcurrentHashMap<>();
    private final Map<UUID, String> inspectionInFlight = new ConcurrentHashMap<>();
    private final Set<UUID> abandonedInspectionOwners = ConcurrentHashMap.newKeySet();
    private final Set<UUID> discardReviewedAfterHeavy = ConcurrentHashMap.newKeySet();
    private final Set<UUID> formatWaiters = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean formatRefreshInFlight = new AtomicBoolean();
    private volatile boolean started;
    private volatile boolean stopping;

    public PaperWorldControlPayloadAdapter(
            JavaPlugin plugin,
            WorldRegistry registry,
            WorldCreationService creation,
            WorldTeleportService teleport,
            WorldLifecycleService lifecycle,
            WorldSettingsService settingsService,
            WorldExportService exportService,
            WorldImportService importService,
            ConversionUpdateService conversionUpdates,
            TransferSessionService transfers,
            WorldHeavyOperationOrchestrator heavyOperations
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.creation = Objects.requireNonNull(creation, "creation");
        this.teleport = Objects.requireNonNull(teleport, "teleport");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.settingsService = Objects.requireNonNull(settingsService, "settingsService");
        this.exportService = Objects.requireNonNull(exportService, "exportService");
        this.importService = Objects.requireNonNull(importService, "importService");
        this.conversionUpdates = Objects.requireNonNull(conversionUpdates, "conversionUpdates");
        this.transfers = Objects.requireNonNull(transfers, "transfers");
        this.heavyOperations = Objects.requireNonNull(heavyOperations, "heavyOperations");
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

        abandonedInspectionOwners.addAll(inspectionInFlight.keySet());
        for (Map.Entry<UUID, String> entry : Map.copyOf(reviewedImportArtifacts).entrySet()) {
            if (heavyInFlight.contains(entry.getKey())) discardReviewedAfterHeavy.add(entry.getKey());
            else discardReviewedArtifact(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<UUID, String> entry : Map.copyOf(inspectionInFlight).entrySet()) {
            discardInspectionResult(entry.getKey(), entry.getValue());
        }

        started = false;
        pendingHeavyCompletion.clear();
        formatWaiters.clear();
        formatRefreshInFlight.set(false);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!started || stopping) return;
        UUID owner = event.getPlayer().getUniqueId();
        formatWaiters.remove(owner);
        if (inspectionInFlight.containsKey(owner)) {
            abandonedInspectionOwners.add(owner);
            return;
        }
        String reviewed = reviewedImportArtifacts.get(owner);
        if (reviewed == null) return;
        if (heavyInFlight.contains(owner)) {
            discardReviewedAfterHeavy.add(owner);
            return;
        }
        discardReviewedArtifact(owner, reviewed);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!started || stopping || !CHANNEL.equals(channel)) return;
        flushPendingCompletion(player);

        final WorldControlWireProtocol.Request request;
        try {
            request = WorldControlWireProtocol.decodeRequest(message);
        } catch (IOException | RuntimeException exception) {
            send(player, WorldControlWireProtocol.error(exception.getMessage()));
            return;
        }

        if (request instanceof WorldControlWireProtocol.GetExportFormats) { handleExportFormats(player); return; }
        if (request instanceof WorldControlWireProtocol.InspectImport inspect) { handleInspectImport(player, inspect); return; }
        if (request instanceof WorldControlWireProtocol.DiscardImport discard) { handleDiscardImport(player, discard); return; }
        if (request instanceof WorldControlWireProtocol.DuplicateWorld duplicate) { handleDuplicate(player, duplicate); return; }
        if (request instanceof WorldControlWireProtocol.DeleteWorld delete) { handleDelete(player, delete); return; }
        if (request instanceof WorldControlWireProtocol.ExportWorld export) { handleExport(player, export); return; }
        if (request instanceof WorldControlWireProtocol.ImportWorld importWorld) { handleImport(player, importWorld); return; }

        try {
            send(player, WorldControlWireProtocol.encodeResponse(handle(player, request)));
        } catch (IOException | RuntimeException exception) {
            send(player, WorldControlWireProtocol.error(exception.getMessage()));
        }
    }

    private void flushPendingCompletion(Player player) {
        byte[] pending = pendingHeavyCompletion.remove(player.getUniqueId());
        if (pending != null) send(player, pending);
    }

    private WorldControlWireProtocol.Response handle(Player player, WorldControlWireProtocol.Request request) {
        return switch (request) {
            case WorldControlWireProtocol.ListWorlds ignored -> {
                requireAnyWorldPermission(player);
                List<WorldControlWireProtocol.WorldSummary> worlds = registry.all().stream().map(this::summary).toList();
                yield new WorldControlWireProtocol.WorldList(
                        worlds, player.hasPermission(MANAGE_PERMISSION), player.hasPermission(TELEPORT_PERMISSION));
            }
            case WorldControlWireProtocol.GetExportFormats ignored -> throw new IllegalStateException("Export formats must use async capability path");
            case WorldControlWireProtocol.InspectImport ignored -> throw new IllegalStateException("Import inspection must use async path");
            case WorldControlWireProtocol.DiscardImport ignored -> throw new IllegalStateException("Import discard must use review-cleanup path");
            case WorldControlWireProtocol.CreateWorld create -> {
                requireManage(player);
                WorldRecord world = creation.create(create.folderName(), create.displayName(),
                        WorldKind.valueOf(create.kind().strip().toUpperCase(Locale.ROOT)));
                yield new WorldControlWireProtocol.WorldChanged("CREATE", summary(world));
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
            case WorldControlWireProtocol.DuplicateWorld ignored -> throw new IllegalStateException("Duplicate must use async path");
            case WorldControlWireProtocol.DeleteWorld ignored -> throw new IllegalStateException("Delete must use async path");
            case WorldControlWireProtocol.ExportWorld ignored -> throw new IllegalStateException("Export must use async path");
            case WorldControlWireProtocol.ImportWorld ignored -> throw new IllegalStateException("Import must use async path");
        };
    }

    private void handleExportFormats(Player player) {
        try { requireManage(player); }
        catch (RuntimeException exception) {
            send(player, WorldControlWireProtocol.error(exception.getMessage()));
            return;
        }
        send(player, encode(new WorldControlWireProtocol.ExportFormats(exportService.supportedFormats())));
        formatWaiters.add(player.getUniqueId());
        if (!formatRefreshInFlight.compareAndSet(false, true)) return;
        try {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                Exception failure = null;
                try { conversionUpdates.checkIfDue(); }
                catch (Exception exception) { failure = exception; }
                if (stopping || !started) {
                    formatWaiters.clear();
                    formatRefreshInFlight.set(false);
                    return;
                }
                List<String> refreshed = exportService.supportedFormats();
                Exception finalFailure = failure;
                try {
                    plugin.getServer().getScheduler().runTask(plugin,
                            () -> completeExportFormatRefresh(refreshed, finalFailure));
                } catch (RuntimeException scheduleFailure) {
                    formatWaiters.clear();
                    formatRefreshInFlight.set(false);
                }
            });
        } catch (RuntimeException scheduleFailure) {
            formatWaiters.clear();
            formatRefreshInFlight.set(false);
        }
    }

    private void completeExportFormatRefresh(List<String> formats, Exception failure) {
        try {
            if (stopping || !started) return;
            byte[] payload = encode(new WorldControlWireProtocol.ExportFormats(formats));
            for (UUID owner : Set.copyOf(formatWaiters)) {
                Player online = plugin.getServer().getPlayer(owner);
                if (online != null && online.isOnline()) send(online, payload);
            }
            if (failure != null && formats.size() <= 1) {
                plugin.getLogger().fine("Optional export capability refresh unavailable: " + failure.getMessage());
            }
        } finally {
            formatWaiters.clear();
            formatRefreshInFlight.set(false);
        }
    }

    private void handleInspectImport(Player player, WorldControlWireProtocol.InspectImport request) {
        if (!beginHeavy(player)) return;
        UUID owner = player.getUniqueId();
        try {
            if (!transfers.claimCompletedUpload(owner, request.artifactName())) {
                heavyInFlight.remove(owner);
                send(player, WorldControlWireProtocol.error(
                        "Import review is only available for a world file uploaded by this client."));
                return;
            }
        } catch (RuntimeException exception) {
            heavyInFlight.remove(owner);
            send(player, WorldControlWireProtocol.error(exception.getMessage()));
            return;
        }
        String previous = reviewedImportArtifacts.get(owner);
        if (previous != null && !previous.equals(request.artifactName())) discardReviewedArtifact(owner, previous);
        abandonedInspectionOwners.remove(owner);
        inspectionInFlight.put(owner, request.artifactName());
        scheduleImportInspection(player, request.artifactName());
    }

    private void scheduleImportInspection(Player player, String artifactName) {
        UUID owner = player.getUniqueId();
        try {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                WorldImportArtifactStore.ImportInspection result = null;
                Exception failure = null;
                try { result = importService.inspect(artifactName); }
                catch (Exception exception) { failure = exception; }
                if (stopping || !started) {
                    if (result != null) discardInspectionResult(owner, result.artifactName());
                    else discardInspectionResult(owner, artifactName);
                    abandonedInspectionOwners.remove(owner);
                    inspectionInFlight.remove(owner, artifactName);
                    heavyInFlight.remove(owner);
                    return;
                }
                WorldImportArtifactStore.ImportInspection finalResult = result;
                Exception finalFailure = failure;
                try {
                    plugin.getServer().getScheduler().runTask(plugin,
                            () -> completeImportInspection(owner, artifactName, finalResult, finalFailure));
                } catch (RuntimeException scheduleFailure) {
                    if (finalResult != null) discardInspectionResult(owner, finalResult.artifactName());
                    else discardInspectionResult(owner, artifactName);
                    abandonedInspectionOwners.remove(owner);
                    inspectionInFlight.remove(owner, artifactName);
                    heavyInFlight.remove(owner);
                }
            });
        } catch (RuntimeException scheduleFailure) {
            discardInspectionResult(owner, artifactName);
            abandonedInspectionOwners.remove(owner);
            inspectionInFlight.remove(owner, artifactName);
            heavyInFlight.remove(owner);
            if (started && !stopping && player.isOnline()) {
                send(player, WorldControlWireProtocol.error("World Manager is shutting down"));
            }
        }
    }

    private void completeImportInspection(UUID owner, String artifactName,
                                          WorldImportArtifactStore.ImportInspection result, Exception failure) {
        try {
            boolean abandoned = abandonedInspectionOwners.remove(owner);
            if (stopping || !started) {
                if (result != null) discardInspectionResult(owner, result.artifactName());
                else discardInspectionResult(owner, artifactName);
                return;
            }
            Player online = plugin.getServer().getPlayer(owner);
            if (failure != null) {
                transfers.releaseCompletedUpload(owner, artifactName);
                if (!abandoned && online != null && online.isOnline()) {
                    send(online, WorldControlWireProtocol.error(failure.getMessage()));
                }
                return;
            }
            if (result == null) return;
            if (abandoned || online == null || !online.isOnline()) {
                discardInspectionResult(owner, result.artifactName());
                return;
            }
            reviewedImportArtifacts.put(owner, result.artifactName());
            send(online, encode(new WorldControlWireProtocol.ImportInspection(
                    result.artifactName(), result.edition().name(), result.sourceVersion(), result.suggestedName())));
        } finally {
            inspectionInFlight.remove(owner, artifactName);
            abandonedInspectionOwners.remove(owner);
            heavyInFlight.remove(owner);
        }
    }

    private void discardInspectionResult(UUID owner, String artifactName) {
        try { importService.discard(artifactName); }
        catch (RuntimeException exception) {
            plugin.getLogger().warning("Could not discard disconnected import inspection artifact for " + owner + ": "
                    + exception.getMessage());
        } finally {
            try { transfers.releaseCompletedUpload(owner, artifactName); }
            catch (RuntimeException ignored) { }
        }
    }

    private void handleDiscardImport(Player player, WorldControlWireProtocol.DiscardImport request) {
        UUID owner = player.getUniqueId();
        if (heavyInFlight.contains(owner)) return;
        try { requireManage(player); } catch (RuntimeException ignored) { return; }
        String reviewed = reviewedImportArtifacts.get(owner);
        if (!request.artifactName().equals(reviewed)) return;
        discardReviewedArtifact(owner, reviewed);
    }

    private void discardReviewedArtifact(UUID owner, String artifactName) {
        if (!reviewedImportArtifacts.remove(owner, artifactName)) return;
        try { importService.discard(artifactName); }
        catch (RuntimeException exception) {
            plugin.getLogger().warning("Could not discard abandoned import artifact for " + owner + ": "
                    + exception.getMessage());
        } finally {
            try { transfers.releaseCompletedUpload(owner, artifactName); }
            catch (RuntimeException ignored) { }
        }
    }

    private void discardDeferredReviewedArtifact(UUID owner) {
        if (!discardReviewedAfterHeavy.remove(owner)) return;
        String reviewed = reviewedImportArtifacts.get(owner);
        if (reviewed != null) discardReviewedArtifact(owner, reviewed);
    }

    private void handleDuplicate(Player player, WorldControlWireProtocol.DuplicateWorld request) {
        if (!beginHeavy(player)) return;
        scheduleHeavy(player,
                () -> heavyOperations.duplicateWorld(new WorldId(request.sourceWorldId()), request.destinationFolder(),
                        request.displayName(), WorldHeavyOperationOrchestrator.Progress.NONE),
                result -> encode(new WorldControlWireProtocol.WorldChanged("DUPLICATE", summary(result))));
    }

    private void handleDelete(Player player, WorldControlWireProtocol.DeleteWorld request) {
        if (!beginHeavy(player)) return;
        scheduleHeavy(player,
                () -> heavyOperations.deleteWorld(new WorldId(request.worldId()), request.typedDisplayName(),
                        WorldHeavyOperationOrchestrator.Progress.NONE),
                result -> encode(new WorldControlWireProtocol.WorldChanged("DELETE", summaryDeleted(result))));
    }

    private void handleImport(Player player, WorldControlWireProtocol.ImportWorld request) {
        if (!beginHeavy(player)) return;
        UUID owner = player.getUniqueId();
        String reviewed = reviewedImportArtifacts.get(owner);
        boolean ownsUpload;
        try { ownsUpload = transfers.ownsCompletedUpload(owner, request.artifactName()); }
        catch (RuntimeException exception) {
            heavyInFlight.remove(owner);
            send(player, WorldControlWireProtocol.error(exception.getMessage()));
            return;
        }
        if (!request.artifactName().equals(reviewed) || !ownsUpload) {
            heavyInFlight.remove(owner);
            send(player, WorldControlWireProtocol.error(
                    "Import requires the currently reviewed upload. Choose and review the world file again."));
            return;
        }
        scheduleHeavy(player,
                () -> heavyOperations.importWorld(request.artifactName(), request.destinationFolder(), request.displayName(),
                        WorldHeavyOperationOrchestrator.Progress.NONE),
                result -> {
                    reviewedImportArtifacts.remove(owner, request.artifactName());
                    transfers.releaseCompletedUpload(owner, request.artifactName());
                    return encode(new WorldControlWireProtocol.WorldChanged("IMPORT", summary(result)));
                });
    }

    private void handleExport(Player player, WorldControlWireProtocol.ExportWorld request) {
        if (!beginHeavy(player)) return;
        final WorldExportOptions options;
        try { options = ExportSettingsMapper.toOptions(request.settings()); }
        catch (RuntimeException exception) {
            heavyInFlight.remove(player.getUniqueId());
            send(player, WorldControlWireProtocol.error(exception.getMessage()));
            return;
        }
        scheduleHeavy(player,
                () -> heavyOperations.exportWorld(new WorldId(request.worldId()), request.targetFormat(),
                        request.artifactName(), options, WorldHeavyOperationOrchestrator.Progress.NONE),
                result -> encode(new WorldControlWireProtocol.ExportReady(
                        request.worldId(), result.artifact().getFileName().toString(), result.targetFormat())));
    }

    private boolean beginHeavy(Player player) {
        if (!started || stopping) return false;
        try { requireManage(player); }
        catch (RuntimeException exception) {
            send(player, WorldControlWireProtocol.error(exception.getMessage()));
            return false;
        }
        if (!heavyInFlight.add(player.getUniqueId())) {
            send(player, WorldControlWireProtocol.error("Another World Manager file operation is still processing"));
            return false;
        }
        return true;
    }

    private <T> void scheduleHeavy(Player player, HeavyWork<T> work, HeavyResponse<T> response) {
        UUID owner = player.getUniqueId();
        try {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                T result = null;
                Exception failure = null;
                try { result = work.run(); } catch (Exception exception) { failure = exception; }
                if (stopping || !started) {
                    discardDeferredReviewedArtifact(owner);
                    heavyInFlight.remove(owner);
                    return;
                }
                T finalResult = result;
                Exception finalFailure = failure;
                try {
                    plugin.getServer().getScheduler().runTask(plugin, () -> completeHeavy(owner, () -> {
                        if (finalFailure != null) return WorldControlWireProtocol.error(finalFailure.getMessage());
                        return response.encode(finalResult);
                    }));
                } catch (RuntimeException scheduleFailure) {
                    discardDeferredReviewedArtifact(owner);
                    heavyInFlight.remove(owner);
                }
            });
        } catch (RuntimeException scheduleFailure) {
            discardDeferredReviewedArtifact(owner);
            heavyInFlight.remove(owner);
            if (started && !stopping && player.isOnline()) {
                send(player, WorldControlWireProtocol.error("World Manager is shutting down"));
            }
        }
    }

    private void completeHeavy(UUID owner, ResponseSupplier responseSupplier) {
        try {
            if (stopping || !started) return;
            byte[] payload = responseSupplier.get();
            Player online = plugin.getServer().getPlayer(owner);
            if (online != null && online.isOnline()) send(online, payload);
            else pendingHeavyCompletion.put(owner, payload);
        } finally {
            discardDeferredReviewedArtifact(owner);
            heavyInFlight.remove(owner);
        }
    }

    private WorldControlWireProtocol.WorldSummary summary(WorldRecord world) {
        return new WorldControlWireProtocol.WorldSummary(world.id().value(), world.folderName(), world.displayName(),
                world.kind().name(), world.lifecycle().name(), world.defaultGameMode());
    }

    private static WorldControlWireProtocol.WorldSummary summaryDeleted(WorldRecord world) {
        return new WorldControlWireProtocol.WorldSummary(world.id().value(), world.folderName(), world.displayName(),
                world.kind().name(), world.lifecycle().name(), world.defaultGameMode());
    }

    private static WorldControlWireProtocol.SettingsSnapshot settingsSummary(WorldSettingsSnapshot snapshot) {
        var runtime = snapshot.runtime();
        var spawn = runtime.spawn();
        List<WorldControlWireProtocol.GameRuleValue> rules = runtime.gamerules().stream()
                .map(rule -> new WorldControlWireProtocol.GameRuleValue(rule.name(), rule.type().name(), rule.value()))
                .toList();
        return new WorldControlWireProtocol.SettingsSnapshot(
                snapshot.world().id().value(), snapshot.defaultGameMode().name(), runtime.difficulty().name(),
                runtime.pvpEnabled(), runtime.weather().name(), runtime.timeOfDayTicks(),
                spawn.x(), spawn.y(), spawn.z(), rules);
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

    @FunctionalInterface private interface HeavyWork<T> { T run() throws Exception; }
    @FunctionalInterface private interface HeavyResponse<T> { byte[] encode(T result); }
    @FunctionalInterface private interface ResponseSupplier { byte[] get(); }
}
