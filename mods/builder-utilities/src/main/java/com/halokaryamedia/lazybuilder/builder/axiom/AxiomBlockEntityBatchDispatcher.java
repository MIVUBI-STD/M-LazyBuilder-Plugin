package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionFrame;
import com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionTypes;
import com.halokaryamedia.lazybuilder.builder.history.LocalBlockPosition;
import com.halokaryamedia.lazybuilder.builder.history.StoredChangeSet;
import com.halokaryamedia.lazybuilder.builder.history.StoredExtensionCursor;
import com.halokaryamedia.lazybuilder.builder.net.BuilderExtensionClientNetworking;
import com.halokaryamedia.lazybuilder.builder.operation.CancellationToken;
import com.halokaryamedia.lazybuilder.builder.wire.BuilderExtensionWireProtocol;
import net.minecraft.client.world.ClientWorld;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Async authoritative BLOCK_ENTITY dispatcher; server owns block+NBT CAS for these coordinates. */
public final class AxiomBlockEntityBatchDispatcher implements AutoCloseable {
    private final StoredExtensionCursor cursor;
    private final CancellationToken cancellation;
    private final ClientWorld world;
    private final String dimensionId;
    private final int maxBatchEntries;
    private final boolean undo;
    private final Map<Position, StatePair> blockStates;
    private final AtomicReference<BuilderExtensionWireProtocol.Response> response =
            new AtomicReference<>();

    private boolean requestPending;
    private boolean terminal;
    private BlockEntityBatchDispatchState terminalState;
    private String terminalDetail;
    private long processedExtensions;
    private int batchOrdinal;
    private String pendingOperationId;

    public AxiomBlockEntityBatchDispatcher(
            StoredChangeSet stored,
            ClientWorld world,
            CancellationToken cancellation,
            boolean undo
    ) throws IOException {
        Objects.requireNonNull(stored, "stored");
        this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
        this.world = Objects.requireNonNull(world, "world");
        this.dimensionId = world.getRegistryKey().getValue().toString();
        var capabilities = BuilderExtensionClientNetworking.capabilities();
        if (!capabilities.supportsBlockEntity()) {
            throw new IllegalStateException(
                    "Server does not advertise Builder BLOCK_ENTITY authority");
        }
        // One entry may contain two 16 KiB NBT payloads plus block-state strings.
        // Keep one mutation per packet so the protocol's 48 KiB hard limit is
        // guaranteed without buffering/pushback complexity in the streaming cursor.
        this.maxBatchEntries = 1;
        this.undo = undo;
        this.cursor = StoredExtensionCursor.open(stored);
        try {
            this.blockStates = buildBlockStateIndex(stored);
        } catch (IOException | RuntimeException failure) {
            cursor.close();
            throw failure;
        }
    }

    public synchronized BlockEntityBatchDispatchProgress pump() throws IOException {
        if (terminal) {
            return new BlockEntityBatchDispatchProgress(
                    terminalState, processedExtensions, terminalDetail);
        }
        if (cancellation.isCancellationRequested() && !undo) {
            return terminal(BlockEntityBatchDispatchState.CANCELLED, "cancellation requested");
        }

        if (requestPending) {
            BuilderExtensionWireProtocol.Response received = response.getAndSet(null);
            if (received == null) {
                return new BlockEntityBatchDispatchProgress(
                        BlockEntityBatchDispatchState.WAITING, processedExtensions, null);
            }
            requestPending = false;
            pendingOperationId = null;
            if (received instanceof BuilderExtensionWireProtocol.Error error) {
                return terminal(BlockEntityBatchDispatchState.FAILED, error.message());
            }
            BuilderExtensionWireProtocol.BlockEntityBatchResult result =
                    (BuilderExtensionWireProtocol.BlockEntityBatchResult) received;
            processedExtensions = Math.addExact(
                    processedExtensions, result.processedEntries());
            if (result.state() == BuilderExtensionWireProtocol.BatchState.CONFLICT) {
                return terminal(
                        BlockEntityBatchDispatchState.CONFLICT,
                        "block entity conflict at batch index "
                                + result.conflictIndex() + ": " + result.detail());
            }
        }

        List<BuilderExtensionWireProtocol.BlockEntityMutation> entries =
                new ArrayList<>(maxBatchEntries);
        while (entries.size() < maxBatchEntries) {
            HistoryExtensionFrame frame = cursor.nextExtension();
            if (frame == null) {
                if (entries.isEmpty()) {
                    return terminal(BlockEntityBatchDispatchState.EXHAUSTED, null);
                }
                break;
            }
            if (!HistoryExtensionTypes.BLOCK_ENTITY.equals(frame.typeId())) continue;
            entries.add(toMutation(frame));
        }

        String operationId = "lb-be-" + UUID.randomUUID() + "-" + batchOrdinal++;
        BuilderExtensionWireProtocol.ApplyBlockEntityBatch batch =
                new BuilderExtensionWireProtocol.ApplyBlockEntityBatch(
                        operationId, dimensionId, entries);
        response.set(null);
        requestPending = true;
        pendingOperationId = operationId;
        BuilderExtensionClientNetworking.sendBlockEntityBatch(batch, response::set);
        return new BlockEntityBatchDispatchProgress(
                BlockEntityBatchDispatchState.YIELDED, processedExtensions, null);
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

    private BuilderExtensionWireProtocol.BlockEntityMutation toMutation(
            HistoryExtensionFrame frame
    ) throws IOException {
        int x = Math.addExact(
                Math.multiplyExact(frame.chunkX(), 16),
                LocalBlockPosition.localX(frame.localKey()));
        int z = Math.addExact(
                Math.multiplyExact(frame.chunkZ(), 16),
                LocalBlockPosition.localZ(frame.localKey()));
        int y = LocalBlockPosition.y(frame.localKey());

        Position key = new Position(x, y, z);
        StatePair pair = blockStates.get(key);
        if (pair == null) {
            throw new IOException(
                    "BLOCK_ENTITY history is missing a durable block-state guard at "
                            + x + "," + y + "," + z);
        }

        return undo
                ? new BuilderExtensionWireProtocol.BlockEntityMutation(
                        x, y, z,
                        pair.after(), pair.before(),
                        frame.afterPayload(), frame.beforePayload())
                : new BuilderExtensionWireProtocol.BlockEntityMutation(
                        x, y, z,
                        pair.before(), pair.after(),
                        frame.beforePayload(), frame.afterPayload());
    }

    private static Map<Position, StatePair> buildBlockStateIndex(
            StoredChangeSet stored
    ) throws IOException {
        java.util.Set<Position> wanted = new java.util.HashSet<>();
        stored.visitExtensions(frame -> {
            if (HistoryExtensionTypes.BLOCK_ENTITY.equals(frame.typeId())) {
                int x = Math.addExact(
                        Math.multiplyExact(frame.chunkX(), 16),
                        LocalBlockPosition.localX(frame.localKey()));
                int z = Math.addExact(
                        Math.multiplyExact(frame.chunkZ(), 16),
                        LocalBlockPosition.localZ(frame.localKey()));
                wanted.add(new Position(x, LocalBlockPosition.y(frame.localKey()), z));
            }
            return true;
        });

        Map<Position, StatePair> result = new HashMap<>();
        stored.visitChunks(chunk -> {
            long[] positions = chunk.positions();
            for (int i = 0; i < positions.length; i++) {
                int x = Math.addExact(
                        Math.multiplyExact(chunk.chunkX(), 16),
                        LocalBlockPosition.localX(positions[i]));
                int z = Math.addExact(
                        Math.multiplyExact(chunk.chunkZ(), 16),
                        LocalBlockPosition.localZ(positions[i]));
                Position key = new Position(x, LocalBlockPosition.y(positions[i]), z);
                if (wanted.contains(key)) {
                    result.put(key, new StatePair(
                            chunk.beforeState(i), chunk.afterState(i)));
                }
            }
            return true;
        });
        return Map.copyOf(result);
    }

    private BlockEntityBatchDispatchProgress terminal(
            BlockEntityBatchDispatchState state,
            String detail
    ) {
        terminal = true;
        terminalState = state;
        terminalDetail = detail;
        return new BlockEntityBatchDispatchProgress(state, processedExtensions, detail);
    }

    private record Position(int x, int y, int z) {}
    private record StatePair(String before, String after) {}
}
