package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.BuilderRuntime;
import com.halokaryamedia.lazybuilder.builder.history.HistoryRequirement;
import com.halokaryamedia.lazybuilder.builder.material.*;
import com.halokaryamedia.lazybuilder.builder.mutation.BudgetedDispatchSlice;
import com.halokaryamedia.lazybuilder.builder.mutation.BudgetedDispatchState;
import com.halokaryamedia.lazybuilder.builder.mutation.PreparedReconciliationReport;
import com.halokaryamedia.lazybuilder.builder.mutation.RollbackPreparationResult;
import com.halokaryamedia.lazybuilder.builder.mutation.RollbackPreparationState;
import com.halokaryamedia.lazybuilder.builder.operation.*;
import com.halokaryamedia.lazybuilder.builder.placement.MinimumSpacingScatterDistribution;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementPoint;
import com.halokaryamedia.lazybuilder.builder.region.BlockBounds;
import com.halokaryamedia.lazybuilder.builder.region.BuilderRegion;
import com.halokaryamedia.lazybuilder.builder.region.DeterministicRegionPlanner;
import com.halokaryamedia.lazybuilder.builder.region.PointSetRegion;
import com.moulberry.axiomclientapi.CustomTool;
import imgui.moulberry92.ImGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Deterministic minimum-spacing block scatter using the shared durable mutation pipeline. */
public final class AxiomScatterTool implements CustomTool {
    private static final String TOOL_NAME = "LazyBuilder Scatter";
    private static final int MAX_POINTS = 20_000;
    private static final long SCATTER_CHANNEL = 0x534341545445524cL;

    private final AxiomClientServices services;
    private final BuilderRuntime runtime;
    private final int[] radius = {24};
    private final int[] requestedCount = {128};
    private final float[] minimumSpacing = {3.0f};
    private final int[] seedValue = {424242};

    private BlockPos center;
    private List<PlacementPoint> points = List.of();
    private AxiomPointPreviewRegion preview;
    private AxiomPreparedMutationSession activeSession;
    private CancellationSource activeCancellation;
    private Phase phase = Phase.IDLE;
    private String operationStatus = "Right-click a block face to set the scatter center";
    private long activeEstimateBytes;

    public AxiomScatterTool(AxiomClientServices services, BuilderRuntime runtime) {
        this.services = Objects.requireNonNull(services, "services");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    @Override public String name() { return TOOL_NAME; }

    @Override
    public boolean callUseTool() {
        if (activeSession != null) return false;
        BlockHitResult hit = services.toolService().raycastBlock();
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return false;
        center = hit.getBlockPos().offset(hit.getSide());
        rebuildPreview();
        return true;
    }

    @Override
    public boolean callDelete() {
        if (activeSession != null || center == null) return false;
        clearGeometry();
        operationStatus = "Scatter center cleared";
        return true;
    }

    @Override
    public boolean callConfirm() {
        if (activeSession != null || points.isEmpty()) return false;
        try {
            startMutation();
            return true;
        } catch (Exception e) {
            operationStatus = "Apply failed: " + e.getMessage();
            closeActiveQuietly();
            return false;
        }
    }

    @Override
    public void displayImguiOptions() {
        ImGui.textWrapped("Right-click a block face to place the scatter center. Confirm applies Axiom's active block on the generated points using durable History v2.");
        ImGui.separator();
        if (activeSession != null) {
            OperationLifecycle lifecycle = activeSession.lifecycle();
            ImGui.textWrapped("Operation: " + lifecycle.state() + " | " + operationStatus + " | "
                    + String.format("%.1f%%", lifecycle.progressFraction() * 100.0));
            if (!lifecycle.state().isTerminal() && ImGui.button("Cancel and Roll Back")) {
                activeCancellation.requestCancellation();
                operationStatus = "Cancellation requested";
            }
            return;
        }

        ImGui.textWrapped(operationStatus);
        boolean changed = false;
        changed |= ImGui.sliderInt("Radius", radius, 2, 128);
        changed |= ImGui.sliderInt("Count", requestedCount, 1, 5000);
        changed |= ImGui.sliderFloat("Minimum Spacing", minimumSpacing, 0.0f, 32.0f);
        changed |= ImGui.sliderInt("Seed", seedValue, 0, 999_999);
        if (ImGui.button("Clear Scatter")) {
            clearGeometry();
            return;
        }
        if (changed && center != null) rebuildPreview();
        if (center != null) ImGui.textWrapped("Preview points: " + points.size());
    }

    @Override public void reset() {
        if (activeSession == null) clearGeometry();
    }

    @Override
    public void render(Camera camera, float tickDelta, long time, MatrixStack poseStack, Matrix4f projection) {
        pumpOperation();
        if (preview != null && !points.isEmpty()) preview.render(camera, time, poseStack, projection);
    }

    private void rebuildPreview() {
        if (center == null) {
            points = List.of();
            if (preview != null) preview.clear();
            return;
        }
        try {
            int r = radius[0];
            BlockBounds bounds = new BlockBounds(
                    Math.subtractExact(center.getX(), r), center.getY(), Math.subtractExact(center.getZ(), r),
                    Math.addExact(center.getX(), r), center.getY(), Math.addExact(center.getZ(), r));
            MinimumSpacingScatterDistribution distribution = new MinimumSpacingScatterDistribution(
                    requestedCount[0], minimumSpacing[0], 24, SCATTER_CHANNEL);
            points = distribution.generate(bounds, (x, z) -> center.getY(), new OperationSeed(seedValue[0]));
            if (points.size() > MAX_POINTS) throw new IllegalStateException("Scatter point limit exceeded");
            ensurePreview().update(points);
            operationStatus = "Preview ready: " + points.size() + " points";
        } catch (RuntimeException e) {
            points = List.of();
            if (preview != null) preview.clear();
            operationStatus = "Preview failed: " + e.getMessage();
        }
    }

    private void startMutation() throws IOException {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = Objects.requireNonNull(client.world, "Minecraft client world is unavailable");
        if (points.isEmpty()) throw new IllegalStateException("Scatter preview contains no blocks");

        PointSetRegion region = new PointSetRegion(points.stream()
                .map(p -> new PointSetRegion.Point(p.x(), p.y(), p.z())).toList());
        AxiomBlockStateCodec codec = new AxiomBlockStateCodec(world);
        BuilderMaterial material = new BlockMaterial(codec.encode(services.toolService().getActiveBlock()));
        activeCancellation = new CancellationSource();
        ScatterMaterialOperation operation = new ScatterMaterialOperation(
                UUID.randomUUID(), region, new OperationSeed(seedValue[0]), runtime.dispatchBudget(),
                activeCancellation.token(), material);
        OperationPlan plan = new DefaultOperationPlanner(new DeterministicRegionPlanner()).plan(operation);
        activeEstimateBytes = Math.max(1L, Math.multiplyExact((long) region.size(), 96L));
        Optional<PreparedMaterialMutation> prepared = MaterialOperationPreparer.prepare(
                plan, new AxiomClientWorldStateSource(world), runtime.history(), activeEstimateBytes);
        if (prepared.isEmpty()) throw new IllegalStateException("Scatter preparation was cancelled");
        activeSession = new AxiomPreparedMutationSession(
                services, world, prepared.get(), runtime.timeline(), activeCancellation.token());
        phase = Phase.DISPATCHING;
        operationStatus = "Prepared " + prepared.get().plannedChanges() + " block changes";
    }

    private void pumpOperation() {
        if (activeSession == null) return;
        try {
            if (activeCancellation.token().isCancellationRequested()
                    && phase != Phase.ROLLBACK_DISPATCH && phase != Phase.ROLLBACK_RECONCILE) {
                beginRollback();
                return;
            }
            switch (phase) {
                case DISPATCHING -> pumpForwardDispatch();
                case RECONCILING -> pumpForwardReconcile();
                case ROLLBACK_DISPATCH -> pumpRollbackDispatch();
                case ROLLBACK_RECONCILE -> pumpRollbackReconcile();
                case IDLE -> { }
            }
        } catch (Exception e) {
            operationStatus = "Operation failed: " + e.getMessage();
            closeActiveQuietly();
        }
    }

    private void pumpForwardDispatch() throws IOException {
        BudgetedDispatchSlice slice = activeSession.dispatchSlice(runtime.dispatchBudget());
        operationStatus = "Dispatch " + slice.totalVisitedChunks() + " chunks / "
                + slice.totalDispatchedBlocks() + " blocks";
        if (slice.state() == BudgetedDispatchState.EXHAUSTED) phase = Phase.RECONCILING;
        else if (slice.state() == BudgetedDispatchState.CANCELLED) beginRollback();
        else if (slice.state() == BudgetedDispatchState.CONFLICT || slice.state() == BudgetedDispatchState.BUDGET_EXCEEDED) finishIfTerminal();
    }

    private void pumpForwardReconcile() throws IOException {
        PreparedReconciliationReport report = activeSession.reconcile();
        operationStatus = "Reconcile: " + report.state();
        finishIfTerminal();
    }

    private void beginRollback() throws IOException {
        RollbackPreparationResult result = activeSession.prepareRollback(runtime.history(), activeEstimateBytes);
        operationStatus = "Rollback preparation: " + result.state();
        if (result.state() == RollbackPreparationState.READY) phase = Phase.ROLLBACK_DISPATCH;
        else finishIfTerminal();
    }

    private void pumpRollbackDispatch() throws IOException {
        BudgetedDispatchSlice slice = activeSession.dispatchRollbackSlice(runtime.dispatchBudget());
        operationStatus = "Rollback dispatch: " + slice.state();
        if (slice.state() == BudgetedDispatchState.EXHAUSTED) phase = Phase.ROLLBACK_RECONCILE;
        else if (slice.state() == BudgetedDispatchState.CONFLICT || slice.state() == BudgetedDispatchState.BUDGET_EXCEEDED) finishIfTerminal();
    }

    private void pumpRollbackReconcile() throws IOException {
        PreparedReconciliationReport report = activeSession.reconcileRollback();
        operationStatus = "Rollback reconcile: " + report.state();
        finishIfTerminal();
    }

    private void finishIfTerminal() throws IOException {
        if (!activeSession.lifecycle().state().isTerminal()) return;
        OperationState finalState = activeSession.lifecycle().state();
        activeSession.close();
        activeSession = null;
        activeCancellation = null;
        phase = Phase.IDLE;
        operationStatus = "Last operation: " + finalState;
        if (finalState == OperationState.COMPLETED) clearGeometry();
    }

    private void closeActiveQuietly() {
        if (activeSession != null) {
            try { activeSession.close(); } catch (IOException ignored) { }
        }
        activeSession = null;
        activeCancellation = null;
        phase = Phase.IDLE;
    }

    private void clearGeometry() {
        center = null;
        points = List.of();
        if (preview != null) preview.clear();
    }

    private AxiomPointPreviewRegion ensurePreview() {
        if (preview == null) preview = new AxiomPointPreviewRegion(services.regionProvider().createBoolean());
        return preview;
    }

    private enum Phase { IDLE, DISPATCHING, RECONCILING, ROLLBACK_DISPATCH, ROLLBACK_RECONCILE }

    private record ScatterMaterialOperation(
            UUID id, BuilderRegion region, OperationSeed seed, ExecutionBudget executionBudget,
            CancellationToken cancellationToken, BuilderMaterial material
    ) implements MaterialOperation {
        @Override public String type() { return "lazybuilder:scatter"; }
        @Override public MutationReadMode readMode() { return MutationReadMode.SNAPSHOT_READ; }
        @Override public HistoryRequirement historyRequirement() { return HistoryRequirement.REQUIRED; }
        @Override public CancellationDisposition cancellationDisposition() { return CancellationDisposition.ROLLBACK; }
        @Override public MaterialMask materialMask() { return MaterialMask.all(); }
    }
}
