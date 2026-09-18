package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.BuilderRuntime;
import com.halokaryamedia.lazybuilder.builder.history.HistoryRecoveryManager;
import com.halokaryamedia.lazybuilder.builder.history.RecoveredHistoryEntry;
import com.halokaryamedia.lazybuilder.builder.history.HistoryExtensionTypes;
import com.halokaryamedia.lazybuilder.builder.mutation.HistoryExtensionTargetRegistry;
import com.halokaryamedia.lazybuilder.builder.mutation.ReconciliationState;
import com.halokaryamedia.lazybuilder.builder.operation.CancellationSource;
import com.halokaryamedia.lazybuilder.builder.operation.OperationLifecycle;
import com.halokaryamedia.lazybuilder.builder.operation.OperationState;
import com.moulberry.axiomclientapi.CustomTool;
import imgui.moulberry92.ImGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import org.joml.Matrix4f;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.LinkedHashSet;
import java.util.Set;

/** User-facing recovery surface for durable block and authoritative mixed operations left after restart. */
public final class AxiomRecoveryTool implements CustomTool {
    private static final String TOOL_NAME = "LazyBuilder Recovery";

    private final BuilderRuntime runtime;
    private final AxiomMutationController mutation;
    private final int[] selectedIndex = {0};

    private List<RecoveredHistoryEntry> entries = List.of();
    private String status = "Scan for durable Builder recovery plans";

    public AxiomRecoveryTool(AxiomClientServices services, BuilderRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.mutation = new AxiomMutationController(
                Objects.requireNonNull(services, "services"), runtime);
    }

    @Override public String name() { return TOOL_NAME; }

    @Override
    public void displayImguiOptions() {
        ImGui.textWrapped("Recovers committed History v2 plans left by an interrupted Builder session. "
                + "Blocks/BLOCK_ENTITY/BIOME use world-aware classification when readable; "
                + "ENTITY and other negotiated authorities resume through compare-and-set replay.");
        ImGui.separator();

        if (mutation.isActive()) {
            OperationLifecycle lifecycle = mutation.lifecycle();
            ImGui.textWrapped("Recovery operation: " + lifecycle.state() + " | "
                    + mutation.status() + " | "
                    + String.format("%.1f%%", lifecycle.progressFraction() * 100.0));
            if (!lifecycle.state().isTerminal() && ImGui.button("Cancel Recovery and Roll Back")) {
                mutation.requestRollbackCancellation();
            }
            return;
        }

        ImGui.textWrapped(status);
        var joinNotice = runtime.recoveryNotice().snapshot();
        if (joinNotice.hasRecoveryWork()) {
            ImGui.textWrapped("Detected on world join: committed="
                    + joinNotice.committedJournals()
                    + " incomplete=" + joinNotice.incompleteJournals());
        }
        if (ImGui.button("Scan Recovery Plans")) {
            scan();
        }

        if (!entries.isEmpty()) {
            int max = entries.size() - 1;
            if (selectedIndex[0] > max) selectedIndex[0] = max;
            if (entries.size() > 1) {
                ImGui.sliderInt("Recovery Entry", selectedIndex, 0, max);
            }
            RecoveredHistoryEntry entry = entries.get(selectedIndex[0]);
            String state = entry.state()
                    .map(Enum::name)
                    .orElseGet(() -> "UNSUPPORTED: "
                            + entry.unsupportedReason().orElse("unknown extension"));
            ImGui.textWrapped("Operation: " + entry.operationId()
                    + " | blocks=" + entry.changeCount()
                    + " | extensions=" + entry.extensionCount()
                    + " | state=" + state);

            if (canResume(entry) && ImGui.button("Resume Selected")) {
                resumeSelected(entry);
            }
            if (canRollback(entry) && ImGui.button("Roll Back Selected Applied Work")) {
                rollbackSelected(entry);
            }

            if (entry.state().orElse(null) == ReconciliationState.FULLY_APPLIED
                    && ImGui.button("Publish Selected as Undo")) {
                publishSelected(entry);
            }

            if (ImGui.button("Discard Selected Recovery")) {
                discardSelected(entry);
            }
        }

        try {
            ClientWorld currentWorld = requireWorld();
            String currentScope = AxiomWorldScope.currentScopeId(currentWorld);
            HistoryRecoveryManager recoveryManager =
                    new HistoryRecoveryManager(runtime.diskHistory());
            int legacyCommitted = recoveryManager.unscopedCommittedSummaries().size();
            int legacyIncomplete = recoveryManager.unscopedIncompleteFiles().size();
            int unreadableIncomplete = recoveryManager.unreadableIncompleteFiles().size();
            if (legacyCommitted > 0 || legacyIncomplete > 0) {
                ImGui.textWrapped("Legacy unscoped journals are quarantined: committed="
                        + legacyCommitted + " incomplete=" + legacyIncomplete
                        + ". They are not auto-associated with this world.");
            }
            if (unreadableIncomplete > 0) {
                ImGui.textWrapped("Unreadable incomplete journals quarantined: "
                        + unreadableIncomplete
                        + ". Their original world cannot be proven.");
                if (ImGui.button("Discard Unreadable Quarantined Journals")) {
                    int deleted = recoveryManager.discardUnreadableIncompleteFiles();
                    status = "Discarded " + deleted
                            + " unreadable quarantined journals";
                }
            }
            int incomplete = recoveryManager
                    .incompleteFiles(operationId ->
                            com.halokaryamedia.lazybuilder.builder.history.ScopedOperationIds
                                    .belongsTo(operationId, currentScope))
                    .size();
            if (incomplete > 0) {
                ImGui.textWrapped("Incomplete uncommitted journals: " + incomplete);
                if (ImGui.button("Discard Incomplete Journals")) {
                    int deleted = new HistoryRecoveryManager(runtime.diskHistory())
                            .discardIncompleteFiles(operationId ->
                                    com.halokaryamedia.lazybuilder.builder.history.ScopedOperationIds
                                            .belongsTo(operationId, currentScope));
                    status = "Discarded " + deleted + " incomplete journals";
                }
            }
        } catch (IOException e) {
            status = "Recovery scan failed: " + safeMessage(e);
        }
    }

    @Override
    public void render(Camera camera, float tickDelta, long time, MatrixStack poseStack, Matrix4f projection) {
        mutation.pump();
        OperationState outcome = mutation.pollOutcome();
        if (outcome != null) {
            status = "Recovery operation finished: " + outcome;
            if (entries.isEmpty()) {
                scan();
            }
        }
    }

    @Override
    public void reset() {
        // Recovery entries intentionally survive tool deselection. Closing them means
        // deleting the durable file, which must happen only through an explicit action.
    }

    private void scan() {
        if (mutation.isActive()) return;
        try {
            releaseWrappersWithoutDeleting();
            ClientWorld world = requireWorld();
            String scope = AxiomWorldScope.currentScopeId(world);
            java.util.LinkedHashMap<String,
                    com.halokaryamedia.lazybuilder.builder.mutation.HistoryExtensionMutationTarget>
                    readable = new java.util.LinkedHashMap<>();
            // Classification is read-only. BIOME and BLOCK_ENTITY state can be
            // inspected from the client world even when the current server does
            // not advertise write authority. Resume/rollback authority is checked
            // separately before ownership transfer.
            readable.put(
                    HistoryExtensionTypes.BLOCK_ENTITY,
                    new AxiomBlockEntityExtensionReadTarget(world));
            readable.put(
                    HistoryExtensionTypes.BIOME,
                    new AxiomBiomeExtensionReadTarget(world));
            HistoryExtensionTargetRegistry extensions =
                    new HistoryExtensionTargetRegistry(readable);
            entries = new HistoryRecoveryManager(runtime.diskHistory())
                    .discover(
                            new AxiomClientWorldStateSource(world),
                            extensions,
                            operationId -> com.halokaryamedia.lazybuilder.builder.history.ScopedOperationIds
                                    .belongsTo(operationId, scope));
            selectedIndex[0] = 0;
            status = entries.isEmpty()
                    ? "No committed recovery plans found"
                    : "Found " + entries.size() + " committed recovery plans";
        } catch (Exception e) {
            entries = List.of();
            status = "Recovery scan failed: " + safeMessage(e);
        }
    }

    private void resumeSelected(RecoveredHistoryEntry entry) {
        boolean transferred = false;
        try {
            ClientWorld world = requireWorld();
            var prepared = entry.blockOnly()
                    ? entry.transferForResume()
                    : entry.transferForAuthoritativeResume(authoritativeResumeTypes());
            transferred = true;
            CancellationSource cancellation = new CancellationSource();
            long estimate = entry.estimatedHistoryBytes();
            mutation.start(world, prepared, cancellation, estimate);
            removeEntry(entry);
            status = "Recovery resume started";
        } catch (Exception e) {
            if (transferred) {
                try {
                    entry.reclaimAfterFailedStart();
                } catch (RuntimeException reclaimFailure) {
                    e.addSuppressed(reclaimFailure);
                }
            }
            status = "Recovery resume failed: " + safeMessage(e);
        }
    }

    private boolean canResume(RecoveredHistoryEntry entry) {
        return canAuthoritativelyTransfer(entry);
    }

    private boolean canRollback(RecoveredHistoryEntry entry) {
        return canAuthoritativelyTransfer(entry);
    }

    private boolean canAuthoritativelyTransfer(RecoveredHistoryEntry entry) {
        try {
            if (entry.blockReconciliation().state() == ReconciliationState.CONFLICT) {
                return false;
            }
            if (entry.state().orElse(null) == ReconciliationState.CONFLICT) {
                return false;
            }
            if (entry.blockOnly()) return true;
            return authoritativeResumeTypes().containsAll(entry.extensionTypeIds());
        } catch (IOException e) {
            status = "Recovery inspection failed: " + safeMessage(e);
            return false;
        }
    }

    private void rollbackSelected(RecoveredHistoryEntry entry) {
        boolean transferred = false;
        try {
            ClientWorld world = requireWorld();
            var prepared = entry.blockOnly()
                    ? entry.transferForResume()
                    : entry.transferForAuthoritativeResume(authoritativeResumeTypes());
            transferred = true;
            CancellationSource cancellation = new CancellationSource();
            long estimate = entry.estimatedHistoryBytes();
            mutation.start(world, prepared, cancellation, estimate);
            mutation.requestRollbackCancellation();
            removeEntry(entry);
            status = "Recovery rollback started";
        } catch (Exception e) {
            if (transferred) {
                try {
                    entry.reclaimAfterFailedStart();
                } catch (RuntimeException reclaimFailure) {
                    e.addSuppressed(reclaimFailure);
                }
            }
            status = "Recovery rollback failed to start: " + safeMessage(e);
        }
    }

    private static Set<String> authoritativeResumeTypes() {
        var capabilities =
                com.halokaryamedia.lazybuilder.builder.net.BuilderExtensionClientNetworking
                        .capabilities();
        LinkedHashSet<String> types = new LinkedHashSet<>();
        if (capabilities.supportsBlockEntity()) {
            types.add(HistoryExtensionTypes.BLOCK_ENTITY);
        }
        if (capabilities.supportsBiome()) types.add(HistoryExtensionTypes.BIOME);
        if (capabilities.supportsEntity()) types.add(HistoryExtensionTypes.ENTITY);
        return Set.copyOf(types);
    }

    private void publishSelected(RecoveredHistoryEntry entry) {
        try {
            entry.transferFullyAppliedTo(runtime.timeline());
            removeEntry(entry);
            status = "Recovered operation published to undo timeline";
        } catch (Exception e) {
            status = "Publish recovery failed: " + safeMessage(e);
        }
    }

    private void discardSelected(RecoveredHistoryEntry entry) {
        try {
            entry.close();
            removeEntry(entry);
            status = "Recovery plan discarded";
        } catch (IOException e) {
            status = "Discard recovery failed: " + safeMessage(e);
        }
    }

    private void removeEntry(RecoveredHistoryEntry entry) {
        ArrayList<RecoveredHistoryEntry> mutable = new ArrayList<>(entries);
        mutable.remove(entry);
        entries = List.copyOf(mutable);
        if (selectedIndex[0] >= entries.size()) {
            selectedIndex[0] = Math.max(0, entries.size() - 1);
        }
    }

    private void releaseWrappersWithoutDeleting() {
        // A scan owns wrappers around the same durable files. We must not call close()
        // here because close() intentionally means discard/delete. A subsequent scan is
        // only allowed after actions have removed all existing wrappers.
        if (!entries.isEmpty()) {
            throw new IllegalStateException(
                    "Resolve or discard current recovery entries before rescanning");
        }
    }

    private static ClientWorld requireWorld() {
        return Objects.requireNonNull(
                MinecraftClient.getInstance().world,
                "Minecraft client world is unavailable");
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
