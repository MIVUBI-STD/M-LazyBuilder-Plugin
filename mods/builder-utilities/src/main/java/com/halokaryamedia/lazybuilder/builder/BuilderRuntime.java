package com.halokaryamedia.lazybuilder.builder;

import com.halokaryamedia.lazybuilder.builder.history.CompressedMemoryChangeSetStorage;
import com.halokaryamedia.lazybuilder.builder.history.DiskChangeSetStorage;
import com.halokaryamedia.lazybuilder.builder.history.HistorySizingPolicy;
import com.halokaryamedia.lazybuilder.builder.history.HistoryStorageRouter;
import com.halokaryamedia.lazybuilder.builder.history.HistoryTimeline;
import com.halokaryamedia.lazybuilder.builder.history.MemoryChangeSetStorage;
import com.halokaryamedia.lazybuilder.builder.history.ScopedChangeSetStorage;
import com.halokaryamedia.lazybuilder.builder.axiom.AxiomWorldScope;
import com.halokaryamedia.lazybuilder.builder.operation.ExecutionBudget;
import com.halokaryamedia.lazybuilder.builder.operation.RecoverableActiveOperation;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

/** Shared Builder runtime ownership for history and bounded dispatch policy. */
public final class BuilderRuntime implements AutoCloseable {
    private static final long MIB = 1024L * 1024L;
    private HistoryTimeline timeline;
    private final HistoryStorageRouter history;
    private final ExecutionBudget dispatchBudget;
    private final Path schematicDirectory;
    private final DiskChangeSetStorage diskHistory;
    private final BuilderRuntimeMetrics metrics;
    private final BuilderRuntimeProofStore proofStore;
    private final BuilderRecoveryNotice recoveryNotice = new BuilderRecoveryNotice();
    private final java.util.Set<RecoverableActiveOperation> activeOperations =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private boolean closed;

    private BuilderRuntime(
            HistoryTimeline timeline,
            HistoryStorageRouter history,
            ExecutionBudget dispatchBudget,
            Path schematicDirectory,
            DiskChangeSetStorage diskHistory,
            BuilderRuntimeMetrics metrics,
            BuilderRuntimeProofStore proofStore
    ) {
        this.timeline = timeline;
        this.history = history;
        this.dispatchBudget = dispatchBudget;
        this.schematicDirectory = schematicDirectory;
        this.diskHistory = diskHistory;
        this.metrics = metrics;
        this.proofStore = proofStore;
    }

    public static BuilderRuntime createDefault() {
        Path builderDir = FabricLoader.getInstance().getConfigDir().resolve("lazybuilder");
        Path historyDir = builderDir.resolve("builder-history");
        Path schematicDir = builderDir.resolve("schematics");
        Path proofDir = builderDir.resolve("runtime-proofs");
        DiskChangeSetStorage diskHistory = new DiskChangeSetStorage(historyDir);
        HistoryStorageRouter router = new HistoryStorageRouter(
                new HistorySizingPolicy(8 * MIB, 64 * MIB),
                new MemoryChangeSetStorage(),
                new CompressedMemoryChangeSetStorage(),
                new ScopedChangeSetStorage(diskHistory, AxiomWorldScope::currentScopeId));
        return new BuilderRuntime(
                new HistoryTimeline(64),
                router,
                new ExecutionBudget(Duration.ofMillis(4), 4, 65_536, 16 * MIB),
                schematicDir,
                diskHistory,
                new BuilderRuntimeMetrics(),
                new BuilderRuntimeProofStore(proofDir)
        );
    }

    public synchronized HistoryTimeline timeline() { return timeline; }

    public synchronized void registerActiveOperation(RecoverableActiveOperation operation) {
        if (closed) throw new IllegalStateException("Builder runtime is closed");
        if (!activeOperations.add(java.util.Objects.requireNonNull(operation, "operation"))) {
            throw new IllegalStateException("Builder operation is already registered");
        }
    }

    public synchronized void unregisterActiveOperation(RecoverableActiveOperation operation) {
        activeOperations.remove(operation);
    }

    public synchronized int activeOperationCount() {
        return activeOperations.size();
    }

    public synchronized void preserveActiveOperations() throws IOException {
        IOException failure = null;
        var snapshot = java.util.List.copyOf(activeOperations);
        for (RecoverableActiveOperation operation : snapshot) {
            try {
                operation.preserveForWorldExit();
            } catch (IOException e) {
                if (failure == null) failure = e;
                else failure.addSuppressed(e);
            }
        }
        if (failure != null) throw failure;
    }

    public synchronized void resetWorldTimeline() throws IOException {
        HistoryTimeline previous = timeline;
        timeline = new HistoryTimeline(64);
        previous.close();
        recoveryNotice.clear();
    }
    public HistoryStorageRouter history() { return history; }
    public ExecutionBudget dispatchBudget() { return dispatchBudget; }
    public Path schematicDirectory() { return schematicDirectory; }
    public DiskChangeSetStorage diskHistory() { return diskHistory; }
    public BuilderRuntimeMetrics metrics() { return metrics; }
    public BuilderRuntimeProofStore proofStore() { return proofStore; }
    public BuilderRecoveryNotice recoveryNotice() { return recoveryNotice; }

    public Path saveRuntimeProof(String label) throws IOException {
        return proofStore.writeSnapshot(metrics.snapshot(), label);
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        IOException failure = null;
        try {
            proofStore.writeSnapshot(metrics.snapshot(), "shutdown");
        } catch (IOException e) {
            failure = e;
        }
        try {
            preserveActiveOperations();
        } catch (IOException e) {
            if (failure == null) failure = e;
            else failure.addSuppressed(e);
        }
        try {
            timeline.close();
        } catch (IOException e) {
            if (failure == null) failure = e;
            else failure.addSuppressed(e);
        }
        if (failure != null) throw failure;
    }
}
