package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.BuilderRuntime;
import com.halokaryamedia.lazybuilder.builder.BuilderRetirementReadiness;
import com.halokaryamedia.lazybuilder.builder.history.HistoryRecoveryManager;
import com.halokaryamedia.lazybuilder.builder.history.ScopedOperationIds;
import com.halokaryamedia.lazybuilder.builder.net.BuilderExtensionClientNetworking;
import com.moulberry.axiomclientapi.CustomTool;
import imgui.moulberry92.ImGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

import java.util.Objects;

/** Read-only operational status surface for Builder runtime and durable journals. */
public final class AxiomOperationCenterTool implements CustomTool {
    private static final String TOOL_NAME = "LazyBuilder Operation Center";

    private final AxiomClientServices services;
    private final BuilderRuntime runtime;
    private AxiomMixedHistoryReplayController replay;
    private String status = "Open a world, then refresh Builder status";
    private int scopedCommitted;
    private int scopedIncomplete;
    private int legacyUnscoped;
    private int unreadableIncomplete;
    private long scopedBlockHistoryEntries;
    private long scopedExtensions;
    private String scopePreview = "<none>";

    public AxiomOperationCenterTool(
            AxiomClientServices services,
            BuilderRuntime runtime
    ) {
        this.services = Objects.requireNonNull(services, "services");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    @Override public String name() { return TOOL_NAME; }

    @Override
    public void displayImguiOptions() {
        ImGui.textWrapped("Builder status and durable-history repair. Native Axiom owns normal block-only undo/redo. LazyBuilder timeline replay owns mixed payload history and block-only journals explicitly restored after restart.");
        ImGui.separator();

        if (ImGui.button("Refresh Builder Status")) refresh();
        if (ImGui.button("Save Runtime Proof Snapshot")) saveProof();

        if (replay != null && !replay.finished()) {
            ImGui.textWrapped("Builder replay: " + replay.status());
        } else {
            var undoSummary = runtime.timeline().nextUndoSummary();
            if (undoSummary.isPresent()) {
                String label = undoSummary.get().extensionCount() > 0
                        ? "Undo Last Mixed Builder Operation"
                        : "Undo Recovered Block Operation";
                if (ImGui.button(label)) startUndo();
            }
            var redoSummary = runtime.timeline().nextRedoSummary();
            if (redoSummary.isPresent()) {
                String label = redoSummary.get().extensionCount() > 0
                        ? "Redo Last Mixed Builder Operation"
                        : "Redo Recovered Block Operation";
                if (ImGui.button(label)) startRedo();
            }
        }

        ImGui.textWrapped(status);
        try {
            ImGui.textWrapped("Compatibility: " + AxiomCompatibility.current().summary());
        } catch (Exception e) {
            ImGui.textWrapped("Compatibility identity unavailable: " + concise(e));
        }
        var recoveryNotice = runtime.recoveryNotice().snapshot();
        if (recoveryNotice.status()
                == com.halokaryamedia.lazybuilder.builder.BuilderRecoveryNotice.Status.SCANNED) {
            ImGui.textWrapped("Join recovery notice: committed="
                    + recoveryNotice.committedJournals()
                    + " incomplete=" + recoveryNotice.incompleteJournals()
                    + (recoveryNotice.hasRecoveryWork()
                            ? " | ACTION REQUIRED"
                            : " | clean"));
        } else if (recoveryNotice.status()
                == com.halokaryamedia.lazybuilder.builder.BuilderRecoveryNotice.Status.FAILED) {
            ImGui.textWrapped("Join recovery notice failed: " + recoveryNotice.failure());
        }
        ImGui.textWrapped("World scope: " + scopePreview);
        ImGui.textWrapped("Internal durable timeline: undo=" + runtime.timeline().undoSize()
                + " redo=" + runtime.timeline().redoSize());
        ImGui.textWrapped("Recoverable journals for this world: " + scopedCommitted
                + " | blockHistoryEntries=" + scopedBlockHistoryEntries
                + " | extensions=" + scopedExtensions);
        ImGui.textWrapped("Incomplete journals for this world: " + scopedIncomplete);
        if (legacyUnscoped > 0) {
            ImGui.textWrapped("Legacy unscoped journals quarantined: " + legacyUnscoped
                    + " (not auto-resumed because their original world cannot be proven)");
        }
        if (unreadableIncomplete > 0) {
            ImGui.textWrapped("Unreadable incomplete journals quarantined: "
                    + unreadableIncomplete
                    + " (world ownership cannot be proven)");
        }

        try {
            var persisted = runtime.proofStore().aggregateEvidence();
            ImGui.textWrapped("Persisted proof evidence: snapshots="
                    + persisted.snapshotCount()
                    + " clean=" + persisted.cleanSnapshotCount()
                    + " rejected=" + persisted.rejectedSnapshotCount()
                    + " maxCompletedBlocks=" + persisted.maxCompletedPlannedBlocks()
                    + " rollbackWork=" + persisted.maxRollbackWork()
                    + " BLOCK_ENTITY=" + persisted.maxForwardBlockEntityExtensions()
                    + " BIOME=" + persisted.maxForwardBiomeExtensions()
                    + " ENTITY=" + persisted.maxForwardEntityExtensions());
        } catch (Exception e) {
            ImGui.textWrapped("Persisted proof evidence unavailable: " + concise(e));
        }

        var proof = runtime.metrics().snapshot();
        ImGui.textWrapped("Proof directory: " + runtime.proofStore().directory());
        ImGui.textWrapped("Runtime proof: started=" + proof.operationsStarted()
                + " completed=" + proof.operationsCompleted()
                + " cancelled=" + proof.operationsCancelled()
                + " failed=" + proof.operationsFailed());
        ImGui.textWrapped("Forward dispatch: chunks=" + proof.forwardChunksVisited()
                + " blocks=" + proof.forwardBlocksDispatched()
                + " | rollback chunks=" + proof.rollbackChunksVisited()
                + " blocks=" + proof.rollbackBlocksDispatched());
        ImGui.textWrapped("Extensions forward: BLOCK_ENTITY="
                + proof.forwardBlockEntityExtensions()
                + " BIOME=" + proof.forwardBiomeExtensions()
                + " ENTITY=" + proof.forwardEntityExtensions()
                + " | rollback: BLOCK_ENTITY="
                + proof.rollbackBlockEntityExtensions()
                + " BIOME=" + proof.rollbackBiomeExtensions()
                + " ENTITY=" + proof.rollbackEntityExtensions());
        ImGui.textWrapped("Extension conflicts=" + proof.extensionConflicts()
                + " failures=" + proof.extensionFailures());
        ImGui.textWrapped("Builder timeline replay blocks: undo=" + proof.historyUndoBlocks()
                + " redo=" + proof.historyRedoBlocks());
        ImGui.textWrapped("Mixed history replay BLOCK_ENTITY: undo="
                + proof.historyUndoBlockEntityExtensions()
                + " redo=" + proof.historyRedoBlockEntityExtensions()
                + " | BIOME: undo=" + proof.historyUndoBiomeExtensions()
                + " redo=" + proof.historyRedoBiomeExtensions()
                + " | ENTITY: undo=" + proof.historyUndoEntityExtensions()
                + " redo=" + proof.historyRedoEntityExtensions()
                + " | replayFailures=" + proof.historyReplayFailures());
        ImGui.textWrapped("Last operation: id=" + proof.lastOperationId()
                + " plannedBlocks=" + proof.lastOperationPlannedBlocks()
                + " plannedExtensions=" + proof.lastOperationPlannedExtensions()
                + " elapsedMs=" + String.format("%.3f", proof.lastOperationNanos() / 1_000_000.0));
        ImGui.textWrapped("Operation timing: totalMs="
                + String.format("%.3f", proof.totalOperationNanos() / 1_000_000.0)
                + " maxMs=" + String.format("%.3f", proof.maxOperationNanos() / 1_000_000.0));
        ImGui.textWrapped("Conflicts=" + proof.conflicts()
                + " budgetExceeded=" + proof.budgetExceeded()
                + " forwardYields=" + proof.forwardDispatchYields()
                + " rollbackYields=" + proof.rollbackDispatchYields()
                + " maxObservedSliceMs="
                + String.format("%.3f", proof.maxSliceNanos() / 1_000_000.0)
                + " lastOutcome=" + proof.lastOutcome());

        var structureCapabilities = AxiomStructureCapabilityMatrix.global();
        ImGui.textWrapped("Structure payload matrix: BLOCKS="
                + structureCapabilities.blocks()
                + " BIOME=" + structureCapabilities.biomes()
                + " ENTITY=" + structureCapabilities.entities()
                + " BLOCK_ENTITY=" + structureCapabilities.blockEntities());

        var extension = BuilderExtensionClientNetworking.capabilities();
        ImGui.textWrapped("Server extension authority: " + extension.status()
                + " | BIOME=" + extension.supportsBiome()
                + " BLOCK_ENTITY=" + extension.supportsBlockEntity()
                + " ENTITY=" + extension.supportsEntity()
                + " maxBatch=" + extension.maxBatchEntries());

        try {
            var retirement = BuilderRetirementReadiness.evaluate(
                    runtime,
                    extension.supportsBiome(),
                    extension.supportsBlockEntity(),
                    extension.supportsEntity(),
                    scopedCommitted,
                    scopedIncomplete,
                    legacyUnscoped,
                    unreadableIncomplete
            );
            ImGui.textWrapped("FAWE retirement gate: " + retirement.status()
                    + " | persistedProofs=" + retirement.proofSnapshots()
                    + " | completedOps=" + retirement.completedOperations());
            if (!retirement.blockers().isEmpty()) {
                ImGui.textWrapped("Remaining blockers: "
                        + String.join(" | ", retirement.blockers()));
            }
        } catch (Exception e) {
            ImGui.textWrapped("FAWE retirement gate unavailable: " + concise(e));
        }

        var budget = runtime.dispatchBudget();
        ImGui.textWrapped("Dispatch budget: "
                + budget.maxSliceDuration().toMillis() + " ms/slice, "
                + budget.maxChunksInFlight() + " chunks, "
                + budget.maxPendingMutations() + " pending blocks, "
                + budget.maxWorkingMemoryBytes() + " bytes working set");
    }

    @Override
    public void render(
            Camera camera,
            float tickDelta,
            long time,
            MatrixStack poseStack,
            Matrix4f projection
    ) {
        if (replay == null) return;
        replay.pump();
        if (replay.finished()) {
            status = replay.status();
            replay = null;
            refresh();
        }
    }

    @Override
    public void reset() {
        // Replay intentionally survives tool deselection.
    }

    private void startUndo() {
        try {
            replay = AxiomMixedHistoryReplayController.beginUndo(
                    services, runtime, requireWorld());
            status = "Builder undo started";
        } catch (Exception e) {
            status = "Builder undo failed to start: " + concise(e);
        }
    }

    private void startRedo() {
        try {
            replay = AxiomMixedHistoryReplayController.beginRedo(
                    services, runtime, requireWorld());
            status = "Builder redo started";
        } catch (Exception e) {
            status = "Builder redo failed to start: " + concise(e);
        }
    }

    private net.minecraft.client.world.ClientWorld requireWorld() {
        return Objects.requireNonNull(
                MinecraftClient.getInstance().world,
                "Minecraft client world is unavailable");
    }

    private static String concise(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? failure.getClass().getSimpleName()
                : message;
    }

    private void saveProof() {
        try {
            var path = runtime.saveRuntimeProof("manual");
            status = "Saved runtime proof: " + path.getFileName();
        } catch (Exception e) {
            status = "Runtime proof save failed: " + concise(e);
        }
    }

    private void refresh() {
        try {
            if (MinecraftClient.getInstance().world == null) {
                status = "No active world";
                return;
            }
            String scope = AxiomWorldScope.currentScopeId();
            scopePreview = scope.substring(0, Math.min(12, scope.length()));
            HistoryRecoveryManager recovery = new HistoryRecoveryManager(runtime.diskHistory());

            var scoped = recovery.committedSummaries(
                    operationId -> ScopedOperationIds.belongsTo(operationId, scope));
            scopedCommitted = scoped.size();
            scopedBlockHistoryEntries =
                    scoped.stream().mapToLong(s -> s.changeCount()).sum();
            scopedExtensions = scoped.stream().mapToLong(s -> s.extensionCount()).sum();
            scopedIncomplete = recovery.incompleteFiles(
                    operationId -> ScopedOperationIds.belongsTo(operationId, scope)).size();

            legacyUnscoped = recovery.committedSummaries(
                    operationId -> ScopedOperationIds.scopeOf(operationId).isEmpty()).size()
                    + recovery.unscopedIncompleteFiles().size();
            unreadableIncomplete = recovery.unreadableIncompleteFiles().size();
            status = "Builder runtime status refreshed";
        } catch (Exception e) {
            status = "Status refresh failed: "
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }
}
