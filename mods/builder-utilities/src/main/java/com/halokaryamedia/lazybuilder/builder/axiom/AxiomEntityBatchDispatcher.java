package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionFrame;
import com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionTypes;
import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;
import com.halokaryamedia.lazybuilder.builder.history.StoredExtensionCursor;
import com.halokaryamedia.lazybuilder.builder.net.BuilderExtensionClientNetworking;
import com.halokaryamedia.lazybuilder.builder.operation.CancellationToken;
import com.halokaryamedia.lazybuilder.builder.structure.EntityExtensionPayload;
import com.halokaryamedia.lazybuilder.builder.wire.BuilderExtensionWireProtocol;
import net.minecraft.client.world.ClientWorld;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Async authoritative ENTITY extension dispatcher with stable Builder-owned markers. */
public final class AxiomEntityBatchDispatcher implements AutoCloseable {
    private static final int MAX_SAFE_ENTITY_BATCH_ENTRIES = 12;
    private final StoredExtensionCursor cursor;
    private final CancellationToken cancellation;
    private final String dimensionId;
    private final String historyOperationId;
    private final int maxBatchEntries;
    private final boolean undo;
    private final AtomicReference<BuilderExtensionWireProtocol.Response> response =
            new AtomicReference<>();

    private boolean requestPending;
    private boolean terminal;
    private EntityBatchDispatchState terminalState;
    private String terminalDetail;
    private long processedExtensions;
    private int batchOrdinal;
    private String pendingOperationId;

    public AxiomEntityBatchDispatcher(
            StoredChangeSet stored,
            ClientWorld world,
            CancellationToken cancellation,
            boolean undo
    ) {
        Objects.requireNonNull(stored, "stored");
        this.cursor = StoredExtensionCursor.open(stored);
        this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
        this.dimensionId = Objects.requireNonNull(world, "world")
                .getRegistryKey().getValue().toString();
        this.historyOperationId = stored.operationId();
        var capabilities = BuilderExtensionClientNetworking.capabilities();
        if (!capabilities.supportsEntity()) {
            cursor.close();
            throw new IllegalStateException(
                    "Server does not advertise Builder ENTITY authority");
        }
        // Entity SNBT may approach the shared wire-message ceiling by itself.
        // One entry per request keeps encoding bounded; transport fragmentation
        // handles the plugin-message ceiling independently.
        this.maxBatchEntries = 1;
        this.undo = undo;
    }

    public synchronized EntityBatchDispatchProgress pump() throws IOException {
        if (terminal) {
            return new EntityBatchDispatchProgress(
                    terminalState, processedExtensions, terminalDetail);
        }
        if (cancellation.isCancellationRequested() && !undo) {
            return terminal(EntityBatchDispatchState.CANCELLED, "cancellation requested");
        }

        if (requestPending) {
            BuilderExtensionWireProtocol.Response received = response.getAndSet(null);
            if (received == null) {
                return new EntityBatchDispatchProgress(
                        EntityBatchDispatchState.WAITING, processedExtensions, null);
            }
            requestPending = false;
            pendingOperationId = null;
            if (received instanceof BuilderExtensionWireProtocol.Error error) {
                return terminal(EntityBatchDispatchState.FAILED, error.message());
            }
            BuilderExtensionWireProtocol.EntityBatchResult result =
                    (BuilderExtensionWireProtocol.EntityBatchResult) received;
            processedExtensions = Math.addExact(
                    processedExtensions, result.processedEntries());
            if (result.state() == BuilderExtensionWireProtocol.BatchState.CONFLICT) {
                return terminal(
                        EntityBatchDispatchState.CONFLICT,
                        "entity conflict at batch index " + result.conflictIndex()
                                + ": " + result.detail());
            }
        }

        List<BuilderExtensionWireProtocol.EntityMutation> entries =
                new ArrayList<>(maxBatchEntries);
        while (entries.size() < maxBatchEntries) {
            HistoryExtensionFrame frame = cursor.nextExtension();
            if (frame == null) {
                if (entries.isEmpty()) {
                    return terminal(EntityBatchDispatchState.EXHAUSTED, null);
                }
                break;
            }
            if (!HistoryExtensionTypes.ENTITY.equals(frame.typeId())) {
                continue;
            }
            entries.add(toMutation(frame));
        }

        String operationId = "lb-entity-" + UUID.randomUUID() + "-" + batchOrdinal++;
        BuilderExtensionWireProtocol.ApplyEntityBatch batch =
                new BuilderExtensionWireProtocol.ApplyEntityBatch(
                        operationId, dimensionId, entries);
        response.set(null);
        requestPending = true;
        pendingOperationId = operationId;
        BuilderExtensionClientNetworking.sendEntityBatch(batch, response::set);
        return new EntityBatchDispatchProgress(
                EntityBatchDispatchState.YIELDED, processedExtensions, null);
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

    private BuilderExtensionWireProtocol.EntityMutation toMutation(
            HistoryExtensionFrame frame
    ) throws IOException {
        EntityExtensionPayload before =
                EntityExtensionPayload.decode(frame.beforePayload());
        EntityExtensionPayload after =
                EntityExtensionPayload.decode(frame.afterPayload());
        if (!before.sameSlot(after) || before.present() == after.present()) {
            throw new IOException("ENTITY history frame is not an absence/presence toggle");
        }

        EntityExtensionPayload expected = undo ? after : before;
        EntityExtensionPayload desired = undo ? before : after;
        EntityExtensionPayload present = before.present() ? before : after;
        AxiomEntityTemplate template = AxiomEntityTemplate.decode(present.templateNbt());
        String marker = stableMarker(frame.localKey());

        return new BuilderExtensionWireProtocol.EntityMutation(
                marker,
                desired.x(),
                desired.y(),
                desired.z(),
                template.yaw(),
                template.pitch(),
                expected.present(),
                desired.present(),
                template.snbt()
        );
    }

    private String stableMarker(long localKey) {
        String source = historyOperationId + "#" + localKey;
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private EntityBatchDispatchProgress terminal(
            EntityBatchDispatchState state,
            String detail
    ) {
        terminal = true;
        terminalState = state;
        terminalDetail = detail;
        return new EntityBatchDispatchProgress(state, processedExtensions, detail);
    }
}
