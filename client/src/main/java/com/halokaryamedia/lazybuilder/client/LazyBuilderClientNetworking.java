package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.client.net.MapPayload;
import com.halokaryamedia.lazybuilder.client.net.TransferPayload;
import com.halokaryamedia.lazybuilder.world.map.MapActionWireProtocol;
import com.halokaryamedia.lazybuilder.world.transfer.TransferWireProtocol;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.io.IOException;
import java.util.Objects;

/** One client networking owner for both bounded LazyBuilder channels. */
public final class LazyBuilderClientNetworking {
    private final ClientMapController mapController;
    private final ClientTransferController transferController;

    public LazyBuilderClientNetworking(
            ClientMapController mapController,
            ClientTransferController transferController
    ) {
        this.mapController = Objects.requireNonNull(mapController, "mapController");
        this.transferController = Objects.requireNonNull(transferController, "transferController");
    }

    public void register() {
        PayloadTypeRegistry.playC2S().register(TransferPayload.ID, TransferPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TransferPayload.ID, TransferPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MapPayload.ID, MapPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MapPayload.ID, MapPayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(MapPayload.ID, (payload, context) ->
                context.client().execute(() -> handleMap(payload.bytes())));
        ClientPlayNetworking.registerGlobalReceiver(TransferPayload.ID, (payload, context) ->
                context.client().execute(() -> handleTransfer(payload.bytes())));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                client.execute(mapController::refreshCurrentWorld));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            mapController.reset();
            transferController.reset();
        }));
    }

    public static void sendMap(byte[] payload) {
        ClientPlayNetworking.send(new MapPayload(payload));
    }

    public static void sendTransfer(byte[] payload) {
        ClientPlayNetworking.send(new TransferPayload(payload));
    }

    private void handleMap(byte[] bytes) {
        try {
            mapController.accept(MapActionWireProtocol.decodeResponse(bytes));
        } catch (IOException | RuntimeException exception) {
            notifyPlayer("LazyBuilder map response rejected: " + exception.getMessage());
        }
    }

    private void handleTransfer(byte[] bytes) {
        try {
            transferController.accept(TransferWireProtocol.decodeResponse(bytes));
        } catch (IOException | RuntimeException exception) {
            notifyPlayer("LazyBuilder transfer response rejected: " + exception.getMessage());
        }
    }

    static void notifyPlayer(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) client.player.sendMessage(Text.literal(message), false);
    }
}
