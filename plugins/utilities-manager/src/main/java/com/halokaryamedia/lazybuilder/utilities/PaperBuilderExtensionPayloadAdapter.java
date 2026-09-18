package com.halokaryamedia.lazybuilder.utilities;

import com.halokaryamedia.lazybuilder.builder.wire.BuilderExtensionWireProtocol;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.IOException;

/** Authoritative Paper compare-and-set backend for negotiated Builder extension types. */
final class PaperBuilderExtensionPayloadAdapter implements PluginMessageListener {
    static final String PERMISSION = "lazybuilder.utilities.builder-extension";
    private static final int SERVER_CAPABILITIES =
            BuilderExtensionWireProtocol.CAPABILITY_BIOME;
    private static final int MAX_BATCH =
            BuilderExtensionWireProtocol.MAX_BATCH_ENTRIES;

    private final UtilitiesManagerPlugin plugin;

    PaperBuilderExtensionPayloadAdapter(UtilitiesManagerPlugin plugin) {
        this.plugin = plugin;
    }

    void start() {
        plugin.getServer().getMessenger().registerIncomingPluginChannel(
                plugin, BuilderExtensionWireProtocol.CHANNEL, this);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(
                plugin, BuilderExtensionWireProtocol.CHANNEL);
    }

    void stop() {
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
            request = BuilderExtensionWireProtocol.decodeRequest(message);
        } catch (IOException | RuntimeException failure) {
            send(player, new BuilderExtensionWireProtocol.Error(
                    "unknown", "invalid Builder extension request: "
                            + concise(failure)));
            return;
        }

        if (request instanceof BuilderExtensionWireProtocol.CapabilitiesRequest capabilities) {
            int mask = player.hasPermission(PERMISSION)
                    ? SERVER_CAPABILITIES
                    : 0;
            send(player, new BuilderExtensionWireProtocol.Capabilities(
                    capabilities.requestId(), mask, MAX_BATCH));
            return;
        }

        BuilderExtensionWireProtocol.ApplyBiomeBatch batch =
                (BuilderExtensionWireProtocol.ApplyBiomeBatch) request;
        if (!player.hasPermission(PERMISSION)) {
            send(player, new BuilderExtensionWireProtocol.Error(
                    batch.operationId(), "permission denied"));
            return;
        }

        Runnable apply = () -> applyBiomeBatch(player, batch);
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
