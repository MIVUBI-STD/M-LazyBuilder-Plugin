package com.halokaryamedia.lazybuilder.utilities;

import com.halokaryamedia.lazybuilder.builder.wire.BuilderExtensionWireProtocol;
import com.halokaryamedia.lazybuilder.builder.wire.BuilderExtensionTransport;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Authoritative Paper compare-and-set backend for negotiated Builder extension types. */
final class PaperBuilderExtensionPayloadAdapter implements PluginMessageListener, Listener {
    static final String PERMISSION = "lazybuilder.utilities.builder-extension";
    private static final int BASE_SERVER_CAPABILITIES =
            BuilderExtensionWireProtocol.CAPABILITY_BIOME
                    | BuilderExtensionWireProtocol.CAPABILITY_ENTITY;
    private static final int MAX_BATCH =
            BuilderExtensionWireProtocol.MAX_BATCH_ENTRIES;

    private final UtilitiesManagerPlugin plugin;
    private final NamespacedKey entityMarkerKey;
    private final PaperBlockEntityNbtBridge blockEntities;
    private final Map<UUID, BuilderExtensionTransport.Reassembler> reassemblers =
            new ConcurrentHashMap<>();

    PaperBuilderExtensionPayloadAdapter(UtilitiesManagerPlugin plugin) {
        this.plugin = plugin;
        this.entityMarkerKey = new NamespacedKey(plugin, "builder_entity_marker");
        this.blockEntities = PaperBlockEntityNbtBridge.tryCreate(plugin);
    }

    private int serverCapabilities() {
        return BASE_SERVER_CAPABILITIES
                | (blockEntities != null
                        ? BuilderExtensionWireProtocol.CAPABILITY_BLOCK_ENTITY
                        : 0);
    }

    void start() {
        plugin.getServer().getMessenger().registerIncomingPluginChannel(
                plugin, BuilderExtensionWireProtocol.CHANNEL, this);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(
                plugin, BuilderExtensionWireProtocol.CHANNEL);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    void stop() {
        HandlerList.unregisterAll(this);
        reassemblers.clear();
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(
                plugin, BuilderExtensionWireProtocol.CHANNEL, this);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(
                plugin, BuilderExtensionWireProtocol.CHANNEL);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        reassemblers.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void onPluginMessageReceived(
            String channel,
            Player player,
            byte[] message
    ) {
        if (!BuilderExtensionWireProtocol.CHANNEL.equals(channel)) return;

        BuilderExtensionWireProtocol.Request request;
        try {
            BuilderExtensionTransport.Reassembler reassembler =
                    reassemblers.computeIfAbsent(
                            player.getUniqueId(),
                            ignored -> new BuilderExtensionTransport.Reassembler());
            var assembled = reassembler.accept(message);
            if (assembled.isEmpty()) return;
            if (reassembler.isIdle()) {
                reassemblers.remove(player.getUniqueId(), reassembler);
            }
            request = BuilderExtensionWireProtocol.decodeRequest(assembled.get());
        } catch (IOException | RuntimeException failure) {
            send(player, new BuilderExtensionWireProtocol.Error(
                    "unknown", "invalid Builder extension request: "
                            + concise(failure)));
            return;
        }

        if (request instanceof BuilderExtensionWireProtocol.CapabilitiesRequest capabilities) {
            int mask = player.hasPermission(PERMISSION)
                    ? serverCapabilities()
                    : 0;
            send(player, new BuilderExtensionWireProtocol.Capabilities(
                    capabilities.requestId(), mask, MAX_BATCH));
            return;
        }

        if (!player.hasPermission(PERMISSION)) {
            String operationId;
            if (request instanceof BuilderExtensionWireProtocol.ApplyBiomeBatch biome) {
                operationId = biome.operationId();
            } else if (request instanceof BuilderExtensionWireProtocol.ApplyEntityBatch entity) {
                operationId = entity.operationId();
            } else {
                operationId = ((BuilderExtensionWireProtocol.ApplyBlockEntityBatch) request)
                        .operationId();
            }
            send(player, new BuilderExtensionWireProtocol.Error(
                    operationId, "permission denied"));
            return;
        }

        Runnable apply;
        if (request instanceof BuilderExtensionWireProtocol.ApplyBiomeBatch batch) {
            apply = () -> applyBiomeBatch(player, batch);
        } else if (request instanceof BuilderExtensionWireProtocol.ApplyEntityBatch batch) {
            apply = () -> applyEntityBatch(player, batch);
        } else {
            BuilderExtensionWireProtocol.ApplyBlockEntityBatch batch =
                    (BuilderExtensionWireProtocol.ApplyBlockEntityBatch) request;
            apply = () -> applyBlockEntityBatch(player, batch);
        }
        if (Bukkit.isPrimaryThread()) apply.run();
        else plugin.getServer().getScheduler().runTask(plugin, apply);
    }

    private void applyBiomeBatch(
            Player player,
            BuilderExtensionWireProtocol.ApplyBiomeBatch batch
    ) {
        World world = player.getWorld();
        if (!world.getKey().toString().equals(batch.dimensionId())) {
            send(player, new BuilderExtensionWireProtocol.Error(
                    batch.operationId(),
                    "player changed dimension before biome batch execution"));
            return;
        }

        List<BiomeStep> steps = new ArrayList<>(batch.entries().size());

        // Preflight the entire batch before mutating any biome. Ordinary conflicts
        // therefore cannot leave a partially-applied batch.
        for (int i = 0; i < batch.entries().size(); i++) {
            BuilderExtensionWireProtocol.BiomeMutation mutation =
                    batch.entries().get(i);
            Biome actualBiome = world.getBiome(
                    mutation.x(), mutation.y(), mutation.z());
            String actual = actualBiome.getKey().toString();

            if (actual.equals(mutation.afterBiome())) {
                steps.add(new BiomeStep(mutation, actualBiome, actualBiome, true));
                continue;
            }
            if (!actual.equals(mutation.beforeBiome())) {
                send(player, BuilderExtensionWireProtocol.BatchResult.conflict(
                        batch.operationId(), 0, i, actual));
                return;
            }

            NamespacedKey desiredKey = NamespacedKey.fromString(mutation.afterBiome());
            if (desiredKey == null) {
                send(player, new BuilderExtensionWireProtocol.Error(
                        batch.operationId(),
                        "invalid biome id: " + mutation.afterBiome()));
                return;
            }
            Biome desired = Registry.BIOME.get(desiredKey);
            if (desired == null) {
                send(player, new BuilderExtensionWireProtocol.Error(
                        batch.operationId(),
                        "unknown biome: " + mutation.afterBiome()));
                return;
            }
            steps.add(new BiomeStep(mutation, actualBiome, desired, false));
        }

        List<BiomeStep> applied = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            BiomeStep step = steps.get(i);
            if (step.noop()) continue;

            BuilderExtensionWireProtocol.BiomeMutation mutation = step.mutation();
            world.setBiome(
                    mutation.x(), mutation.y(), mutation.z(), step.after());
            applied.add(step);

            String verified = world.getBiome(
                    mutation.x(), mutation.y(), mutation.z())
                    .getKey().toString();
            if (!verified.equals(mutation.afterBiome())) {
                String rollbackFailure = rollbackBiomeSteps(world, applied);
                String detail = "biome write verification failed at batch index " + i
                        + (rollbackFailure == null ? "; batch rolled back"
                        : "; rollback failed: " + rollbackFailure);
                send(player, new BuilderExtensionWireProtocol.Error(
                        batch.operationId(), detail));
                return;
            }
        }

        send(player, BuilderExtensionWireProtocol.BatchResult.completed(
                batch.operationId(), batch.entries().size()));
    }

    private static String rollbackBiomeSteps(
            World world,
            List<BiomeStep> applied
    ) {
        String failure = null;
        for (int i = applied.size() - 1; i >= 0; i--) {
            BiomeStep step = applied.get(i);
            BuilderExtensionWireProtocol.BiomeMutation mutation = step.mutation();
            try {
                world.setBiome(
                        mutation.x(), mutation.y(), mutation.z(), step.before());
                String verified = world.getBiome(
                        mutation.x(), mutation.y(), mutation.z())
                        .getKey().toString();
                if (!verified.equals(mutation.beforeBiome())) {
                    failure = appendFailure(
                            failure,
                            "biome rollback verification failed at "
                                    + mutation.x() + "," + mutation.y() + "," + mutation.z());
                }
            } catch (RuntimeException e) {
                failure = appendFailure(failure, concise(e));
            }
        }
        return failure;
    }

    private void applyBlockEntityBatch(
            Player player,
            BuilderExtensionWireProtocol.ApplyBlockEntityBatch batch
    ) {
        if (blockEntities == null) {
            send(player, new BuilderExtensionWireProtocol.Error(
                    batch.operationId(),
                    "BLOCK_ENTITY authority unavailable on this Paper runtime"));
            return;
        }

        World world = player.getWorld();
        if (!world.getKey().toString().equals(batch.dimensionId())) {
            send(player, new BuilderExtensionWireProtocol.Error(
                    batch.operationId(),
                    "player changed dimension before block-entity batch execution"));
            return;
        }

        // Match BIOME/ENTITY semantics: ordinary conflicts are detected before any
        // mutation. A re-entrant/plugin-induced failure during apply rolls back only
        // entries that this batch actually changed, in reverse order.
        for (int i = 0; i < batch.entries().size(); i++) {
            BuilderExtensionWireProtocol.BlockEntityMutation mutation =
                    batch.entries().get(i);
            try {
                PaperBlockEntityNbtBridge.PreflightResult result =
                        blockEntities.preflightCompareAndSet(world, mutation);
                if (result.state() == PaperBlockEntityNbtBridge.PreflightState.CONFLICT) {
                    send(player,
                            BuilderExtensionWireProtocol.BlockEntityBatchResult.conflict(
                                    batch.operationId(), 0, i, result.detail()));
                    return;
                }
            } catch (Exception failure) {
                send(player, new BuilderExtensionWireProtocol.Error(
                        batch.operationId(),
                        "block entity preflight failed: " + concise(failure)));
                return;
            }
        }

        List<BuilderExtensionWireProtocol.BlockEntityMutation> changed =
                new ArrayList<>();
        for (int i = 0; i < batch.entries().size(); i++) {
            BuilderExtensionWireProtocol.BlockEntityMutation mutation =
                    batch.entries().get(i);
            try {
                PaperBlockEntityNbtBridge.ApplyResult result =
                        blockEntities.applyCompareAndSet(world, mutation);
                if (result.state() == PaperBlockEntityNbtBridge.ApplyState.CONFLICT) {
                    String rollbackFailure = rollbackBlockEntitySteps(world, changed);
                    if (rollbackFailure != null) {
                        send(player, new BuilderExtensionWireProtocol.Error(
                                batch.operationId(),
                                "block entity conflict at batch index " + i
                                        + "; rollback failed: " + rollbackFailure));
                    } else {
                        send(player,
                                BuilderExtensionWireProtocol.BlockEntityBatchResult.conflict(
                                        batch.operationId(), 0, i, result.detail()));
                    }
                    return;
                }
                if (result.changed()) changed.add(mutation);
            } catch (Exception failure) {
                String rollbackFailure = rollbackBlockEntitySteps(world, changed);
                String detail = "block entity mutation failed: " + concise(failure)
                        + (rollbackFailure == null
                                ? "; batch rolled back"
                                : "; rollback failed: " + rollbackFailure);
                send(player, new BuilderExtensionWireProtocol.Error(
                        batch.operationId(), detail));
                return;
            }
        }

        send(player, BuilderExtensionWireProtocol.BlockEntityBatchResult.completed(
                batch.operationId(), batch.entries().size()));
    }

    private String rollbackBlockEntitySteps(
            World world,
            List<BuilderExtensionWireProtocol.BlockEntityMutation> changed
    ) {
        String failure = null;
        for (int i = changed.size() - 1; i >= 0; i--) {
            BuilderExtensionWireProtocol.BlockEntityMutation reverse =
                    reverseBlockEntityMutation(changed.get(i));
            try {
                PaperBlockEntityNbtBridge.ApplyResult result =
                        blockEntities.applyCompareAndSet(world, reverse);
                if (result.state() == PaperBlockEntityNbtBridge.ApplyState.CONFLICT) {
                    failure = appendFailure(failure, result.detail());
                }
            } catch (Exception rollbackFailure) {
                failure = appendFailure(failure, concise(rollbackFailure));
            }
        }
        return failure;
    }

    static BuilderExtensionWireProtocol.BlockEntityMutation reverseBlockEntityMutation(
            BuilderExtensionWireProtocol.BlockEntityMutation mutation
    ) {
        return new BuilderExtensionWireProtocol.BlockEntityMutation(
                mutation.x(),
                mutation.y(),
                mutation.z(),
                mutation.afterBlockState(),
                mutation.beforeBlockState(),
                mutation.afterNbt(),
                mutation.beforeNbt()
        );
    }

    private void applyEntityBatch(
            Player player,
            BuilderExtensionWireProtocol.ApplyEntityBatch batch
    ) {
        World world = player.getWorld();
        if (!world.getKey().toString().equals(batch.dimensionId())) {
            send(player, new BuilderExtensionWireProtocol.Error(
                    batch.operationId(),
                    "player changed dimension before entity batch execution"));
            return;
        }

        List<EntityStep> steps = new ArrayList<>(batch.entries().size());

        // Validate every target slot and every entity snapshot before mutating.
        // A normal conflict therefore leaves this authoritative batch unchanged.
        for (int i = 0; i < batch.entries().size(); i++) {
            BuilderExtensionWireProtocol.EntityMutation mutation =
                    batch.entries().get(i);
            Location location = entityLocation(world, mutation);
            var nearby = nearbyNonPlayers(world, location);
            var marked = markedEntitiesInWorld(world, mutation.markerId());
            var foreignNearby = nearby.stream()
                    .filter(entity -> !mutation.markerId().equals(entityMarker(entity)))
                    .toList();

            final org.bukkit.entity.EntitySnapshot snapshot;
            try {
                snapshot = Bukkit.getEntityFactory()
                        .createEntitySnapshot(mutation.templateSnbt());
            } catch (IllegalArgumentException e) {
                send(player, new BuilderExtensionWireProtocol.Error(
                        batch.operationId(),
                        "invalid entity snapshot: " + concise(e)));
                return;
            }
            if (snapshot.getEntityType() == EntityType.PLAYER) {
                send(player, new BuilderExtensionWireProtocol.Error(
                        batch.operationId(), "player entities cannot be spawned"));
                return;
            }

            if (mutation.afterPresent()) {
                if (marked.size() == 1) {
                    Entity existing = marked.get(0);
                    if (!isAtTargetSlot(existing, location)) {
                        send(player, BuilderExtensionWireProtocol.EntityBatchResult.conflict(
                                batch.operationId(), 0, i,
                                "Builder-owned entity moved away from target slot"));
                        return;
                    }
                    if (!foreignNearby.isEmpty()) {
                        send(player, BuilderExtensionWireProtocol.EntityBatchResult.conflict(
                                batch.operationId(), 0, i,
                                "entity target slot occupied by another entity"));
                        return;
                    }
                    steps.add(new EntityStep(
                            mutation, location, snapshot, existing, true));
                    continue;
                }
                if (marked.size() > 1) {
                    send(player, BuilderExtensionWireProtocol.EntityBatchResult.conflict(
                            batch.operationId(), 0, i,
                            "multiple Builder-owned entities share one marker"));
                    return;
                }
                if (!foreignNearby.isEmpty()) {
                    send(player, BuilderExtensionWireProtocol.EntityBatchResult.conflict(
                            batch.operationId(), 0, i,
                            "entity target slot occupied"));
                    return;
                }
                steps.add(new EntityStep(
                        mutation, location, snapshot, null, false));
            } else {
                if (marked.isEmpty()) {
                    steps.add(new EntityStep(
                            mutation, location, snapshot, null, true));
                    continue;
                }
                if (marked.size() != 1) {
                    send(player, BuilderExtensionWireProtocol.EntityBatchResult.conflict(
                            batch.operationId(), 0, i,
                            "multiple Builder-owned entities share one marker"));
                    return;
                }
                Entity existing = marked.get(0);
                if (!isAtTargetSlot(existing, location)) {
                    send(player, BuilderExtensionWireProtocol.EntityBatchResult.conflict(
                            batch.operationId(), 0, i,
                            "Builder-owned entity moved away from target slot"));
                    return;
                }
                if (!foreignNearby.isEmpty()) {
                    send(player, BuilderExtensionWireProtocol.EntityBatchResult.conflict(
                            batch.operationId(), 0, i,
                            "entity target slot occupied by another entity"));
                    return;
                }
                steps.add(new EntityStep(
                        mutation, location, snapshot, existing, false));
            }
        }

        List<AppliedEntityStep> applied = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            EntityStep step = steps.get(i);
            if (step.noop()) continue;

            try {
                if (step.mutation().afterPresent()) {
                    Entity entity = step.snapshot().createEntity(world);
                    entity.getPersistentDataContainer().set(
                            entityMarkerKey,
                            PersistentDataType.STRING,
                            step.mutation().markerId()
                    );
                    if (!entity.spawnAt(step.location())) {
                        throw new IllegalStateException("entity spawn was rejected");
                    }
                    applied.add(new AppliedEntityStep(step, entity));
                } else {
                    Entity entity = step.existingMarkedEntity();
                    entity.remove();
                    applied.add(new AppliedEntityStep(step, null));
                }

                if (!entityStateMatchesDesired(world, step)) {
                    throw new IllegalStateException(
                            "entity state verification failed at batch index " + i);
                }
            } catch (RuntimeException failure) {
                String rollbackFailure = rollbackEntitySteps(world, applied);
                String detail = "entity mutation failed at batch index " + i
                        + ": " + concise(failure)
                        + (rollbackFailure == null ? "; batch rolled back"
                        : "; rollback failed: " + rollbackFailure);
                send(player, new BuilderExtensionWireProtocol.Error(
                        batch.operationId(), detail));
                return;
            }
        }

        send(player, BuilderExtensionWireProtocol.EntityBatchResult.completed(
                batch.operationId(), batch.entries().size()));
    }

    private Location entityLocation(
            World world,
            BuilderExtensionWireProtocol.EntityMutation mutation
    ) {
        return new Location(
                world,
                mutation.x(),
                mutation.y(),
                mutation.z(),
                mutation.yaw(),
                mutation.pitch()
        );
    }

    private java.util.Collection<Entity> nearbyNonPlayers(
            World world,
            Location location
    ) {
        return world.getNearbyEntities(
                location,
                0.125,
                0.125,
                0.125,
                entity -> !(entity instanceof Player));
    }

    private List<Entity> markedEntitiesInWorld(
            World world,
            String markerId
    ) {
        return world.getEntities().stream()
                .filter(entity -> !(entity instanceof Player))
                .filter(entity -> markerId.equals(entityMarker(entity)))
                .toList();
    }

    private String entityMarker(Entity entity) {
        return entity.getPersistentDataContainer().get(
                entityMarkerKey, PersistentDataType.STRING);
    }

    private static boolean isAtTargetSlot(Entity entity, Location target) {
        Location actual = entity.getLocation();
        if (actual.getWorld() != target.getWorld()) return false;
        return Math.abs(actual.getX() - target.getX()) <= 0.125
                && Math.abs(actual.getY() - target.getY()) <= 0.125
                && Math.abs(actual.getZ() - target.getZ()) <= 0.125;
    }

    private boolean entityStateMatchesDesired(
            World world,
            EntityStep step
    ) {
        var marked = markedEntitiesInWorld(
                world, step.mutation().markerId());
        return step.mutation().afterPresent()
                ? marked.size() == 1
                : marked.isEmpty();
    }

    private String rollbackEntitySteps(
            World world,
            List<AppliedEntityStep> applied
    ) {
        String failure = null;
        for (int i = applied.size() - 1; i >= 0; i--) {
            AppliedEntityStep appliedStep = applied.get(i);
            EntityStep step = appliedStep.step();
            try {
                if (step.mutation().afterPresent()) {
                    Entity spawned = appliedStep.spawnedEntity();
                    if (spawned != null) spawned.remove();
                } else {
                    Entity restored = step.snapshot().createEntity(world);
                    restored.getPersistentDataContainer().set(
                            entityMarkerKey,
                            PersistentDataType.STRING,
                            step.mutation().markerId()
                    );
                    if (!restored.spawnAt(step.location())) {
                        throw new IllegalStateException(
                                "entity rollback spawn was rejected");
                    }
                }

                var marked = markedEntitiesInWorld(
                        world, step.mutation().markerId());
                boolean restored = step.mutation().afterPresent()
                        ? marked.isEmpty()
                        : marked.size() == 1;
                if (!restored) {
                    failure = appendFailure(
                            failure,
                            "entity rollback verification failed for marker "
                                    + step.mutation().markerId());
                }
            } catch (RuntimeException e) {
                failure = appendFailure(failure, concise(e));
            }
        }
        return failure;
    }

    private static String appendFailure(String current, String next) {
        if (next == null || next.isBlank()) return current;
        return current == null ? next : current + " | " + next;
    }

    private record BiomeStep(
            BuilderExtensionWireProtocol.BiomeMutation mutation,
            Biome before,
            Biome after,
            boolean noop
    ) {}

    private record EntityStep(
            BuilderExtensionWireProtocol.EntityMutation mutation,
            Location location,
            org.bukkit.entity.EntitySnapshot snapshot,
            Entity existingMarkedEntity,
            boolean noop
    ) {}

    private record AppliedEntityStep(
            EntityStep step,
            Entity spawnedEntity
    ) {}

    private void send(
            Player player,
            BuilderExtensionWireProtocol.Response response
    ) {
        if (!player.isOnline()) return;
        try {
            player.sendPluginMessage(
                    plugin,
                    BuilderExtensionWireProtocol.CHANNEL,
                    BuilderExtensionWireProtocol.encodeResponse(response));
        } catch (IOException | RuntimeException failure) {
            plugin.getLogger().warning(
                    "Failed to send Builder extension response: "
                            + concise(failure));
        }
    }

    private static String concise(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? failure.getClass().getSimpleName()
                : message;
    }
}
