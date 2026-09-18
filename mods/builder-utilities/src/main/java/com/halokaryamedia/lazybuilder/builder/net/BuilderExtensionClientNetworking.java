package com.halokaryamedia.lazybuilder.builder.net;

import com.halokaryamedia.lazybuilder.builder.wire.BuilderExtensionWireProtocol;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Capability-negotiated client transport for authoritative non-block Builder mutations. */
public final class BuilderExtensionClientNetworking {
    private static final AtomicReference<BuilderExtensionCapabilities> CAPABILITIES =
            new AtomicReference<>(
                    BuilderExtensionCapabilities.unavailable("not connected"));
    private static final ConcurrentHashMap<
            String,
            Consumer<BuilderExtensionWireProtocol.Response>
            > PENDING = new ConcurrentHashMap<>();

    private static volatile String pendingCapabilityRequest;
    private static boolean registered;

    private BuilderExtensionClientNetworking() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;

        PayloadTypeRegistry.playC2S().register(
                BuilderExtensionPayload.ID, BuilderExtensionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(
                BuilderExtensionPayload.ID, BuilderExtensionPayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(
                BuilderExtensionPayload.ID,
                (payload, context) -> context.client().execute(
                        () -> accept(payload.bytes()))
        );

        ClientPlayConnectionEvents.JOIN.register(
                (handler, sender, client) -> client.execute(
                        BuilderExtensionClientNetworking::negotiate));
        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> reset("disconnected"));
    }

    public static BuilderExtensionCapabilities capabilities() {
        return CAPABILITIES.get();
    }

    public static boolean canApplyBiomes() {
        return capabilities().supportsBiome();
    }

    public static boolean canApplyBlockEntities() {
        return capabilities().supportsBlockEntity();
    }

    public static void requestRenegotiation() {
        negotiate();
    }

    /** Releases a pending callback when a local dispatcher is abandoned. */
    public static void cancelPending(String operationId) {
        if (operationId == null || operationId.isBlank()) return;
        PENDING.remove(operationId);
    }

    public static void sendBlockEntityBatch(
            BuilderExtensionWireProtocol.ApplyBlockEntityBatch batch,
            Consumer<BuilderExtensionWireProtocol.Response> callback
    ) throws IOException {
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(callback, "callback");
        BuilderExtensionCapabilities capabilities = CAPABILITIES.get();
        if (!capabilities.supportsBlockEntity()) {
            throw new IllegalStateException(
                    "Server does not advertise Builder BLOCK_ENTITY authority");
        }
        if (batch.entries().size() > capabilities.maxBatchEntries()) {
            throw new IllegalArgumentException(
                    "Block entity batch exceeds negotiated server limit "
                            + capabilities.maxBatchEntries());
        }
        sendOperationRequest(batch.operationId(), batch, callback);
    }

    public static void sendEntityBatch(
            BuilderExtensionWireProtocol.ApplyEntityBatch batch,
            Consumer<BuilderExtensionWireProtocol.Response> callback
    ) throws IOException {
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(callback, "callback");
        BuilderExtensionCapabilities capabilities = CAPABILITIES.get();
        if (!capabilities.supportsEntity()) {
            throw new IllegalStateException(
                    "Server does not advertise Builder ENTITY authority");
        }
        if (batch.entries().size() > capabilities.maxBatchEntries()) {
            throw new IllegalArgumentException(
                    "Entity batch exceeds negotiated server limit "
                            + capabilities.maxBatchEntries());
        }
        sendOperationRequest(batch.operationId(), batch, callback);
    }

    public static void sendBiomeBatch(
            BuilderExtensionWireProtocol.ApplyBiomeBatch batch,
            Consumer<BuilderExtensionWireProtocol.Response> callback
    ) throws IOException {
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(callback, "callback");
        BuilderExtensionCapabilities capabilities = CAPABILITIES.get();
        if (!capabilities.supportsBiome()) {
            throw new IllegalStateException(
                    "Server does not advertise Builder BIOME authority");
        }
        if (batch.entries().size() > capabilities.maxBatchEntries()) {
            throw new IllegalArgumentException(
                    "Biome batch exceeds negotiated server limit "
                            + capabilities.maxBatchEntries());
        }
        sendOperationRequest(batch.operationId(), batch, callback);
    }

    private static void sendOperationRequest(
            String operationId,
            BuilderExtensionWireProtocol.Request request,
            Consumer<BuilderExtensionWireProtocol.Response> callback
    ) throws IOException {
        if (PENDING.putIfAbsent(operationId, callback) != null) {
            throw new IllegalStateException(
                    "Operation already has a pending extension request: " + operationId);
        }
        try {
            ClientPlayNetworking.send(new BuilderExtensionPayload(
                    BuilderExtensionWireProtocol.encodeRequest(request)));
        } catch (RuntimeException | IOException failure) {
            PENDING.remove(operationId);
            throw failure;
        }
    }

    private static void negotiate() {
        reset("negotiating");
        if (!ClientPlayNetworking.canSend(BuilderExtensionPayload.ID)) {
            CAPABILITIES.set(
                    BuilderExtensionCapabilities.unavailable(
                            "server extension channel unavailable"));
            return;
        }
        String requestId = "cap-" + UUID.randomUUID();
        pendingCapabilityRequest = requestId;
        try {
            ClientPlayNetworking.send(new BuilderExtensionPayload(
                    BuilderExtensionWireProtocol.encodeRequest(
                            new BuilderExtensionWireProtocol.CapabilitiesRequest(
                                    requestId))));
        } catch (IOException | RuntimeException failure) {
            pendingCapabilityRequest = null;
            CAPABILITIES.set(
                    BuilderExtensionCapabilities.unavailable(
                            "capability request failed: "
                                    + concise(failure)));
        }
    }

    private static void accept(byte[] bytes) {
        try {
            BuilderExtensionWireProtocol.Response response =
                    BuilderExtensionWireProtocol.decodeResponse(bytes);

            if (response instanceof BuilderExtensionWireProtocol.Capabilities capabilities) {
                if (!Objects.equals(
                        pendingCapabilityRequest, capabilities.requestId())) {
                    return;
                }
                pendingCapabilityRequest = null;
                CAPABILITIES.set(new BuilderExtensionCapabilities(
                        true,
                        capabilities.capabilityMask(),
                        capabilities.maxBatchEntries(),
                        capabilities.capabilityMask() == 0
                                ? "server connected; no permitted extension capabilities"
                                : "server extension authority negotiated"
                ));
                return;
            }

            String operationId;
            if (response instanceof BuilderExtensionWireProtocol.BatchResult result) {
                operationId = result.operationId();
            } else if (response instanceof BuilderExtensionWireProtocol.EntityBatchResult result) {
                operationId = result.operationId();
            } else if (response instanceof BuilderExtensionWireProtocol.BlockEntityBatchResult result) {
                operationId = result.operationId();
            } else {
                operationId =
                        ((BuilderExtensionWireProtocol.Error) response).operationId();
            }
            Consumer<BuilderExtensionWireProtocol.Response> callback =
                    PENDING.remove(operationId);
            if (callback != null) callback.accept(response);
        } catch (IOException | RuntimeException failure) {
            CAPABILITIES.set(
                    BuilderExtensionCapabilities.unavailable(
                            "server extension response rejected: "
                                    + concise(failure)));
        }
    }

    private static void reset(String status) {
        pendingCapabilityRequest = null;
        PENDING.clear();
        CAPABILITIES.set(BuilderExtensionCapabilities.unavailable(status));
    }

    private static String concise(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? failure.getClass().getSimpleName()
                : message;
    }
}
