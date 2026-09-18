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
    private long scopedBlocks;
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
        ImGui.textWrapped("Builder status and mixed-history repair. Native Axiom remains the primary block history owner; LazyBuilder replay is exposed only when a timeline entry also contains non-block payloads.");
        ImGui.separator();

        if (ImGui.button("Refresh Builder Status")) refresh();
        if (ImGui.button("Save Runtime Proof Snapshot")) saveProof();

        if (replay != null && !replay.finished()) {
            ImGui.textWrapped("Mixed replay: " + replay.status());
        } else {
            var undoSummary = runtime.timeline().nextUndoSummary();
            if (undoSummary.isPresent() && undoSummary.get().extensionCount() > 0
                    && ImGui.button("Undo Last Mixed Builder Operation")) {
                startUndo();
            }
            var redoSummary = runtime.timeline().nextRedoSummary();
            if (redoSummary.isPresent() && redoSummary.get().extensionCount() > 0
                    && ImGui.button("Redo Last Mixed Builder Operation")) {
                startRedo();
            }
        }

        ImGui.textWrapped(status);
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
                + " | blocks=" + scopedBlocks
                + " | extensions=" + scopedExtensions);
        ImGui.textWrapped("Incomplete journals for this world: " + scopedIncomplete);
        if (legacyUnscoped > 0) {
            ImGui.textWrapped("Legacy unscoped journals quarantined: " + legacyUnscoped
                    + " (not auto-resumed because their original world cannot be proven)");
        }

        try {
            var persisted = runtime.proofStore().aggregateEvidence();
            ImGui.textWrapped("Persisted proof evidence: snapshots="
                    + persisted.snapshotCount()
                    + " clean=" + persisted.cleanSnapshotCount()
                    + " rejected=" + persisted.rejectedSnapshotCount()
                    + " maxCompletedBlocks=" + persisted.maxCompletedPlannedBlocks()
                    + " rollbackWork=" + persisted.maxRollbackWork()
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
        ImGui.textWrapped("Extensions forward: BIOME=" + proof.forwardBiomeExtensions()
                + " ENTITY=" + proof.forwardEntityExtensions()
                + " | rollback: BIOME=" + proof.rollbackBiomeExtensions()
                + " ENTITY=" + proof.rollbackEntityExtensions());
        ImGui.textWrapped("Extension conflicts=" + proof.extensionConflicts()
                + " failures=" + proof.extensionFailures());
        ImGui.textWrapped("Mixed history replay blocks: undo=" + proof.historyUndoBlocks()
                + " redo=" + proof.historyRedoBlocks());
        ImGui.textWrapped("Mixed history replay BIOME: undo="
                + proof.historyUndoBiomeExtensions()
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
                    scopedIncomplete
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
            status = "Mixed undo started";
        } catch (Exception e) {
            status = "Mixed undo failed to start: " + concise(e);
        }
    }

    private void startRedo() {
        try {
            replay = AxiomMixedHistoryReplayController.beginRedo(
                    services, runtime, requireWorld());
            status = "Mixed redo started";
        } catch (Exception e) {
            status = "Mixed redo failed to start: " + concise(e);
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
            scopedBlocks = scoped.stream().mapToLong(s -> s.changeCount()).sum();
            scopedExtensions = scoped.stream().mapToLong(s -> s.extensionCount()).sum();
            scopedIncomplete = recovery.incompleteFiles(
                    operationId -> ScopedOperationIds.belongsTo(operationId, scope)).size();

            legacyUnscoped = recovery.committedSummaries(
                    operationId -> ScopedOperationIds.scopeOf(operationId).isEmpty()).size();
            status = "Builder runtime status refreshed";
        } catch (Exception e) {
            status = "Status refresh failed: "
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }
}
