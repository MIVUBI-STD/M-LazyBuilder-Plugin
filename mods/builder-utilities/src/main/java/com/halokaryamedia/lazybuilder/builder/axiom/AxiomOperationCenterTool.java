package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.BuilderRuntime;
import com.halokaryamedia.lazybuilder.builder.history.HistoryRecoveryManager;
import com.halokaryamedia.lazybuilder.builder.history.ScopedOperationIds;
import com.halokaryamedia.lazybuilder.builder.net.BuilderExtensionClientNetworking;
import com.moulberry.axiomclientapi.CustomTool;
import imgui.moulberry92.ImGui;
import net.minecraft.client.MinecraftClient;

import java.util.Objects;

/** Read-only operational status surface for Builder runtime and durable journals. */
public final class AxiomOperationCenterTool implements CustomTool {
    private static final String TOOL_NAME = "LazyBuilder Operation Center";

    private final BuilderRuntime runtime;
    private String status = "Open a world, then refresh Builder status";
    private int scopedCommitted;
    private int scopedIncomplete;
    private int legacyUnscoped;
    private long scopedBlocks;
    private long scopedExtensions;
    private String scopePreview = "<none>";

    public AxiomOperationCenterTool(BuilderRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    @Override public String name() { return TOOL_NAME; }

    @Override
    public void displayImguiOptions() {
        ImGui.textWrapped("Read-only Builder status. Native Axiom remains the user-facing edit/history owner; "
                + "these counters describe LazyBuilder's durable safety layer.");
        ImGui.separator();

        if (ImGui.button("Refresh Builder Status")) refresh();

        ImGui.textWrapped(status);
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

        var proof = runtime.metrics().snapshot();
        ImGui.textWrapped("Runtime proof: started=" + proof.operationsStarted()
                + " completed=" + proof.operationsCompleted()
                + " cancelled=" + proof.operationsCancelled()
                + " failed=" + proof.operationsFailed());
        ImGui.textWrapped("Forward dispatch: chunks=" + proof.forwardChunksVisited()
                + " blocks=" + proof.forwardBlocksDispatched()
                + " | rollback chunks=" + proof.rollbackChunksVisited()
                + " blocks=" + proof.rollbackBlocksDispatched());
        ImGui.textWrapped("Conflicts=" + proof.conflicts()
                + " budgetExceeded=" + proof.budgetExceeded()
                + " maxObservedSliceMs="
                + String.format("%.3f", proof.maxSliceNanos() / 1_000_000.0)
                + " lastOutcome=" + proof.lastOutcome());

        var extension = BuilderExtensionClientNetworking.capabilities();
        ImGui.textWrapped("Server extension authority: " + extension.status()
                + " | BIOME=" + extension.supportsBiome()
                + " BLOCK_ENTITY=" + extension.supportsBlockEntity()
                + " ENTITY=" + extension.supportsEntity()
                + " maxBatch=" + extension.maxBatchEntries());

        var budget = runtime.dispatchBudget();
        ImGui.textWrapped("Dispatch budget: "
                + budget.maxSliceDuration().toMillis() + " ms/slice, "
                + budget.maxChunksInFlight() + " chunks, "
                + budget.maxPendingMutations() + " pending blocks, "
                + budget.maxWorkingMemoryBytes() + " bytes working set");
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
