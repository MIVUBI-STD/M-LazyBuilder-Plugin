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
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Authoritative Paper compare-and-set backend for negotiated Builder extension types. */
final class PaperBuilderExtensionPayloadAdapter implements PluginMessageListener {
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
    }

    void stop() {
        reassemblers.clear();
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(
                plugin, BuilderExtensionWireProtocol.CHANNEL, this);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(
                plugin, BuilderExtensionWireProtocol.CHANNEL);
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

        int processed = 0;
        for (int i = 0; i < batch.entries().size(); i++) {
            BuilderExtensionWireProtocol.BiomeMutation mutation =
                    batch.entries().get(i);
            String actual = world.getBiome(
                    mutation.x(), mutation.y(), mutation.z())
                    .getKey().toString();

            if (actual.equals(mutation.afterBiome())) {
                processed++;
                continue;
            }
            if (!actual.equals(mutation.beforeBiome())) {
                send(player, BuilderExtensionWireProtocol.BatchResult.conflict(
                        batch.operationId(), processed, i, actual));
                return;
            }

            NamespacedKey key = NamespacedKey.fromString(mutation.afterBiome());
            if (key == null) {
                send(player, new BuilderExtensionWireProtocol.Error(
                        batch.operationId(),
                        "invalid biome id: " + mutation.afterBiome()));
                return;
            }
            Biome desired = Registry.BIOME.get(key);
            if (desired == null) {
                send(player, new BuilderExtensionWireProtocol.Error(
                        batch.operationId(),
                        "unknown biome: " + mutation.afterBiome()));
                return;
            }

            world.setBiome(
                    mutation.x(), mutation.y(), mutation.z(), desired);
            String verified = world.getBiome(
                    mutation.x(), mutation.y(), mutation.z())
                    .getKey().toString();
            if (!verified.equals(mutation.afterBiome())) {
                send(player, BuilderExtensionWireProtocol.BatchResult.conflict(
                        batch.operationId(), processed, i, verified));
                return;
            }
            processed++;
        }

        send(player, BuilderExtensionWireProtocol.BatchResult.completed(
                batch.operationId(), processed));
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

        int processed = 0;
        for (int i = 0; i < batch.entries().size(); i++) {
            BuilderExtensionWireProtocol.BlockEntityMutation mutation =
                    batch.entries().get(i);
            try {
                PaperBlockEntityNbtBridge.ApplyResult result =
                        blockEntities.applyCompareAndSet(world, mutation);
                if (result.state()
                        == PaperBlockEntityNbtBridge.ApplyState.CONFLICT) {
                    send(player,
                            BuilderExtensionWireProtocol.BlockEntityBatchResult.conflict(
                                    batch.operationId(),
                                    processed,
                                    i,
                                    result.detail()));
                    return;
                }
                processed++;
            } catch (Exception failure) {
                send(player, new BuilderExtensionWireProtocol.Error(
                        batch.operationId(),
                        "block entity mutation failed: " + concise(failure)));
                return;
            }
        }

        send(player, BuilderExtensionWireProtocol.BlockEntityBatchResult.completed(
                batch.operationId(), processed));
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

        int processed = 0;
        for (int i = 0; i < batch.entries().size(); i++) {
            BuilderExtensionWireProtocol.EntityMutation mutation =
                    batch.entries().get(i);
            Location location = new Location(
                    world,
                    mutation.x(),
                    mutation.y(),
                    mutation.z(),
                    mutation.yaw(),
                    mutation.pitch()
            );

            var nearby = world.getNearbyEntities(
                    location,
                    0.125,
                    0.125,
                    0.125,
                    entity -> !(entity instanceof Player));
            var marked = nearby.stream()
                    .filter(entity -> mutation.markerId().equals(
                            entity.getPersistentDataContainer().get(
                                    entityMarkerKey, PersistentDataType.STRING)))
                    .toList();

            if (mutation.afterPresent()) {
                if (marked.size() == 1) {
                    processed++;
                    continue;
                }
                if (marked.size() > 1 || !nearby.isEmpty()) {
                    send(player, BuilderExtensionWireProtocol.EntityBatchResult.conflict(
                            batch.operationId(), processed, i,
                            "entity target slot occupied"));
                    return;
                }

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

                Entity entity = snapshot.createEntity(world);
                entity.getPersistentDataContainer().set(
                        entityMarkerKey,
                        PersistentDataType.STRING,
                        mutation.markerId()
                );
                if (!entity.spawnAt(location)) {
                    send(player, new BuilderExtensionWireProtocol.Error(
                            batch.operationId(), "entity spawn was rejected"));
                    return;
                }

                long verified = world.getNearbyEntities(
                                location, 0.125, 0.125, 0.125,
                                candidate -> !(candidate instanceof Player))
                        .stream()
                        .filter(candidate -> mutation.markerId().equals(
                                candidate.getPersistentDataContainer().get(
                                        entityMarkerKey, PersistentDataType.STRING)))
                        .count();
                if (verified != 1) {
                    send(player, BuilderExtensionWireProtocol.EntityBatchResult.conflict(
                            batch.operationId(), processed, i,
                            "spawned entity marker verification failed"));
                    return;
                }
            } else {
                if (marked.isEmpty()) {
                    processed++;
                    continue;
                }
                if (marked.size() != 1) {
                    send(player, BuilderExtensionWireProtocol.EntityBatchResult.conflict(
                            batch.operationId(), processed, i,
                            "multiple Builder-owned entities occupy target slot"));
                    return;
                }
                marked.get(0).remove();
                boolean remains = world.getNearbyEntities(
                                location, 0.125, 0.125, 0.125,
                                candidate -> !(candidate instanceof Player))
                        .stream()
                        .anyMatch(candidate -> mutation.markerId().equals(
                                candidate.getPersistentDataContainer().get(
                                        entityMarkerKey, PersistentDataType.STRING)));
                if (remains) {
                    send(player, BuilderExtensionWireProtocol.EntityBatchResult.conflict(
                            batch.operationId(), processed, i,
                            "entity removal verification failed"));
                    return;
                }
            }
            processed++;
        }

        send(player, BuilderExtensionWireProtocol.EntityBatchResult.completed(
                batch.operationId(), processed));
    }

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
