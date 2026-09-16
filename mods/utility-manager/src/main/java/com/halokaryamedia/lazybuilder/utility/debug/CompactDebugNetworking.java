package com.halokaryamedia.lazybuilder.utility.debug;

import com.halokaryamedia.lazybuilder.utility.net.UtilityTelemetryPayload;
import com.halokaryamedia.lazybuilder.utility.telemetry.UtilityTelemetryWireProtocol;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import java.io.IOException;

/** Fabric transport adapter for the Compact Debug telemetry contract. */
public final class CompactDebugNetworking {
    private static boolean registered;

    private CompactDebugNetworking() {
    }

    public static void register() {
        if (registered) return;
        PayloadTypeRegistry.playC2S().register(UtilityTelemetryPayload.ID, UtilityTelemetryPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(UtilityTelemetryPayload.ID, UtilityTelemetryPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(UtilityTelemetryPayload.ID, (payload, context) ->
                context.client().execute(() -> accept(payload.bytes())));
        registered = true;
    }

    public static void requestSnapshot() {
        if (!registered || !ClientPlayNetworking.canSend(UtilityTelemetryPayload.ID)) return;
        ClientPlayNetworking.send(new UtilityTelemetryPayload(UtilityTelemetryWireProtocol.subscribe()));
    }

    private static void accept(byte[] bytes) {
        try {
            UtilityTelemetryWireProtocol.Response response = UtilityTelemetryWireProtocol.decodeResponse(bytes);
            if (response instanceof UtilityTelemetryWireProtocol.Snapshot snapshot) {
                CompactDebugServerState.update(
                        snapshot.worldName(),
                        snapshot.cpuPercent(),
                        snapshot.usedMemoryBytes(),
                        snapshot.maxMemoryBytes()
                );
            }
        } catch (IOException | RuntimeException ignored) {
            // Malformed/unexpected telemetry must fail closed to the UI's unavailable state.
            CompactDebugServerState.clear();
        }
    }
}
