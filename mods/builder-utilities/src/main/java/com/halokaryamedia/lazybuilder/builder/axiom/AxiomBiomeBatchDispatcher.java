package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionFrame;
import com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionTypes;
import com.halokaryamedia.lazybuilder.builder.history.LocalBlockPosition;
import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;
import com.halokaryamedia.lazybuilder.builder.history.StoredExtensionCursor;
import com.halokaryamedia.lazybuilder.builder.net.BuilderExtensionClientNetworking;
import com.halokaryamedia.lazybuilder.builder.net.BuilderExtensionRequestTimeout;
import com.halokaryamedia.lazybuilder.builder.operation.CancellationToken;
import com.halokaryamedia.lazybuilder.builder.wire.BuilderExtensionWireProtocol;
import net.minecraft.client.world.ClientWorld;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One-request-at-a-time async BIOME extension dispatcher.
 * The client thread never blocks waiting for Paper responses.
 */
public final class AxiomBiomeBatchDispatcher implements AutoCloseable {
    private final StoredExtensionCursor cursor;
    private final CancellationToken cancellation;
    private final String dimensionId;
    private final int maxBatchEntries;
    private final boolean undo;
    private final AtomicReference<BuilderExtensionWireProtocol.Response> response =
            new AtomicReference<>();

    private boolean requestPending;
    private boolean terminal;
    private BiomeBatchDispatchState terminalState;
    private String terminalDetail;
    private long processedExtensions;
    private int batchOrdinal;
    private String pendingOperationId;
    private long pendingStartedNanos;

    public AxiomBiomeBatchDispatcher(
            StoredChangeSet stored,
            ClientWorld world,
            CancellationToken cancellation,
            boolean undo
    ) {
        Objects.requireNonNull(stored, "stored");
        this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
        this.dimensionId = Objects.requireNonNull(world, "world")
                .getRegistryKey().getValue().toString();
        var capabilities = BuilderExtensionClientNetworking.capabilities();
        if (!capabilities.supportsBiome()) {
            throw new IllegalStateException(
                    "Server does not advertise Builder BIOME authority");
        }
        this.maxBatchEntries = capabilities.maxBatchEntries();
        this.undo = undo;
        this.cursor = StoredExtensionCursor.open(stored);
    }

    public synchronized BiomeBatchDispatchProgress pump() throws IOException {
        if (terminal) {
            return new BiomeBatchDispatchProgress(
                    terminalState, processedExtensions, terminalDetail);
        }
        if (cancellation.isCancellationRequested() && !undo) {
            return terminal(BiomeBatchDispatchState.CANCELLED, "cancellation requested");
        }

        if (requestPending) {
            BuilderExtensionWireProtocol.Response received = response.getAndSet(null);
            if (received == null) {
                if (BuilderExtensionRequestTimeout.expired(
                        pendingStartedNanos, System.nanoTime())) {
                    BuilderExtensionClientNetworking.cancelPending(pendingOperationId);
                    pendingOperationId = null;
                    requestPending = false;
                    return terminal(
                            BiomeBatchDispatchState.FAILED,
                            "Builder extension request timed out");
                }
                return new BiomeBatchDispatchProgress(
                        BiomeBatchDispatchState.WAITING, processedExtensions, null);
            }
            requestPending = false;
            pendingOperationId = null;
            if (received instanceof BuilderExtensionWireProtocol.Error error) {
                return terminal(BiomeBatchDispatchState.FAILED, error.message());
            }
            BuilderExtensionWireProtocol.BatchResult result =
                    (BuilderExtensionWireProtocol.BatchResult) received;
            processedExtensions = Math.addExact(
                    processedExtensions, result.processedEntries());
            if (result.state() == BuilderExtensionWireProtocol.BatchState.CONFLICT) {
                return terminal(
                        BiomeBatchDispatchState.CONFLICT,
                        "biome conflict at batch index " + result.conflictIndex()
                                + " actual=" + result.actualBiome());
            }
        }

        List<BuilderExtensionWireProtocol.BiomeMutation> entries =
                new ArrayList<>(maxBatchEntries);
        while (entries.size() < maxBatchEntries) {
            HistoryExtensionFrame frame = cursor.nextExtension();
            if (frame == null) {
                if (entries.isEmpty()) {
                    return terminal(BiomeBatchDispatchState.EXHAUSTED, null);
                }
                break;
            }
            if (!HistoryExtensionTypes.BIOME.equals(frame.typeId())) {
                continue;
            }
            entries.add(toMutation(frame, undo));
        }

        String operationId = "lb-biome-" + UUID.randomUUID() + "-" + batchOrdinal++;
        BuilderExtensionWireProtocol.ApplyBiomeBatch batch =
                new BuilderExtensionWireProtocol.ApplyBiomeBatch(
                        operationId, dimensionId, entries);
        response.set(null);
        requestPending = true;
        pendingOperationId = operationId;
        pendingStartedNanos = System.nanoTime();
        BuilderExtensionClientNetworking.sendBiomeBatch(batch, response::set);
        return new BiomeBatchDispatchProgress(
                BiomeBatchDispatchState.YIELDED, processedExtensions, null);
    }

    @Override
    public synchronized void close() {
        terminal = true;
        if (pendingOperationId != null) {
            BuilderExtensionClientNetworking.cancelPending(pendingOperationId);
            pendingOperationId = null;
        }
        requestPending = false;
        cursor.close();
        response.set(null);
    }

    private BiomeBatchDispatchProgress terminal(
            BiomeBatchDispatchState state,
            String detail
    ) {
        terminal = true;
        terminalState = state;
        terminalDetail = detail;
        return new BiomeBatchDispatchProgress(state, processedExtensions, detail);
    }

    private static BuilderExtensionWireProtocol.BiomeMutation toMutation(
            HistoryExtensionFrame frame,
            boolean undo
    ) {
        int x = Math.addExact(
                Math.multiplyExact(frame.chunkX(), 16),
                LocalBlockPosition.localX(frame.localKey()));
        int z = Math.addExact(
                Math.multiplyExact(frame.chunkZ(), 16),
                LocalBlockPosition.localZ(frame.localKey()));
        int y = LocalBlockPosition.y(frame.localKey());
        String before = text(undo ? frame.afterPayload() : frame.beforePayload());
        String after = text(undo ? frame.beforePayload() : frame.afterPayload());
        return new BuilderExtensionWireProtocol.BiomeMutation(x, y, z, before, after);
    }

    private static String text(byte[] payload) {
        String value = new String(payload, StandardCharsets.UTF_8);
        if (value.isBlank()) throw new IllegalArgumentException("Biome payload is blank");
        return value;
    }
}
