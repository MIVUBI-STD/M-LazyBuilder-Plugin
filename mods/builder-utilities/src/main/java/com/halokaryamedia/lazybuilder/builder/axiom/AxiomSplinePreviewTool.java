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
import com.halokaryamedia.lazybuilder.builder.placement.PlacementVariation;
import com.halokaryamedia.lazybuilder.builder.region.BuilderRegion;
import com.halokaryamedia.lazybuilder.builder.region.DeterministicRegionPlanner;
import com.halokaryamedia.lazybuilder.builder.region.PointSetRegion;
import com.halokaryamedia.lazybuilder.builder.spline.*;
import com.halokaryamedia.lazybuilder.builder.symmetry.BuilderTransform;
import com.halokaryamedia.lazybuilder.builder.symmetry.SymmetryPlanner;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** First end-to-end Axiom-native LazyBuilder tool: preview, durable plan, bounded apply, reconcile and rollback. */
public final class AxiomSplinePreviewTool implements CustomTool {
    private final AxiomClientServices services;
    private final BuilderRuntime runtime;
    private final List<SplineControlPoint> controlPoints = new ArrayList<>();
    private final float[] spacing = {4.0f};
    private final float[] radius = {2.0f};
    private final int[] quality = {16};
    private final int[] seedValue = {424242};
    private final int[] rotationalCopies = {1};

    private AxiomSplinePreviewRegion preview;
    private List<SplinePlacementPlanEntry> lastPlan = List.of();
    private List<BuilderTransform> lastTransforms = List.of(BuilderTransform.identity());
    private AxiomPreparedMutationSession activeSession;
    private CancellationSource activeCancellation;
    private Phase phase = Phase.IDLE;
    private String operationStatus = "Ready";
    private long activeEstimateBytes;

    public AxiomSplinePreviewTool(AxiomClientServices services, BuilderRuntime runtime) {
        this.services = Objects.requireNonNull(services, "services");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    @Override public String name() { return AxiomSplineToolContract.TOOL_NAME; }

    @Override
    public boolean callUseTool() {
        if (activeSession != null) return false;
        BlockHitResult hit = services.toolService().raycastBlock();
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return false;
        BlockPos pointPos = hit.getBlockPos().offset(hit.getSide());
        controlPoints.add(new SplineControlPoint(
                new BuilderVec3(pointPos.getX() + 0.5, pointPos.getY() + 0.5, pointPos.getZ() + 0.5), radius[0], 0.0));
        rebuildPreview();
        return true;
    }

    @Override
    public boolean callDelete() {
        if (activeSession != null || controlPoints.isEmpty()) return false;
        controlPoints.remove(controlPoints.size() - 1); rebuildPreview(); return true;
    }

    @Override
    public boolean callConfirm() {
        if (!AxiomSplineToolContract.WORLD_MUTATION_ENABLED || activeSession != null || lastPlan.isEmpty()) return false;
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
        ImGui.textWrapped("Right-click block faces to add spline points. Confirm applies the preview with Axiom's active block through durable History v2 and bounded dispatch.");
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
        changed |= ImGui.sliderFloat("Spacing", spacing, 0.5f, 32.0f);
        boolean radiusChanged = ImGui.sliderFloat("Radius", radius, 0.5f, 16.0f);
        changed |= radiusChanged;
        changed |= ImGui.sliderInt("Preview Quality", quality, 4, 64);
        changed |= ImGui.sliderInt("Seed", seedValue, 0, 999_999);
        changed |= ImGui.sliderInt("Rotational Copies", rotationalCopies, 1, 16);
        if (ImGui.button("Clear Spline")) { reset(); return; }
        if (radiusChanged) applyRadiusToControlPoints();
        if (changed) rebuildPreview();
    }

    @Override public void reset() { clearGeometry(); }

    @Override
    public void render(Camera camera, float tickDelta, long time, MatrixStack poseStack, Matrix4f projection) {
        pumpOperation();
        if (preview != null && !lastPlan.isEmpty()) preview.render(camera, time, poseStack, projection);
    }

    public List<SplineControlPoint> controlPoints() { return List.copyOf(controlPoints); }
    public List<SplinePlacementPlanEntry> lastPlan() { return lastPlan; }
    public List<BuilderTransform> lastTransforms() { return lastTransforms; }

    private void startMutation() throws IOException {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = Objects.requireNonNull(client.world, "Minecraft client world is unavailable");
        List<SplinePreviewVoxelizer.Voxel> voxels = SplinePreviewVoxelizer.voxelize(lastPlan, lastTransforms);
        if (voxels.isEmpty()) throw new IllegalStateException("Spline preview contains no blocks");
        if (voxels.size() > AxiomSplineToolContract.MAX_MUTATION_VOXELS) throw new IllegalStateException("Spline exceeds mutation voxel limit");

        PointSetRegion region = new PointSetRegion(voxels.stream()
                .map(v -> new PointSetRegion.Point(v.x(), v.y(), v.z())).toList());
        AxiomBlockStateCodec codec = new AxiomBlockStateCodec(world);
        BuilderMaterial material = new BlockMaterial(codec.encode(services.toolService().getActiveBlock()));
        activeCancellation = new CancellationSource();
        SplineMaterialOperation operation = new SplineMaterialOperation(
                UUID.randomUUID(), region, new OperationSeed(seedValue[0]), runtime.dispatchBudget(),
                activeCancellation.token(), material);
        OperationPlan plan = new DefaultOperationPlanner(new DeterministicRegionPlanner()).plan(operation);
        AxiomClientWorldStateSource worldSource = new AxiomClientWorldStateSource(world);
        activeEstimateBytes = Math.max(1L, Math.multiplyExact((long) region.size(), 96L));
        Optional<PreparedMaterialMutation> prepared = MaterialOperationPreparer.prepare(
                plan, worldSource, runtime.history(), activeEstimateBytes);
        if (prepared.isEmpty()) throw new IllegalStateException("Spline preparation was cancelled");
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
        else if (slice.state() == BudgetedDispatchState.CONFLICT || slice.state() == BudgetedDispatchState.BUDGET_EXCEEDED) {
            finishIfTerminal();
        }
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
        else if (slice.state() == BudgetedDispatchState.CONFLICT || slice.state() == BudgetedDispatchState.BUDGET_EXCEEDED) {
            finishIfTerminal();
        }
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
        activeSession = null; activeCancellation = null; phase = Phase.IDLE;
        operationStatus = "Last operation: " + finalState;
        if (finalState == OperationState.COMPLETED) clearGeometry();
    }

    private void closeActiveQuietly() {
        if (activeSession != null) {
            try { activeSession.close(); } catch (IOException ignored) { }
        }
        activeSession = null; activeCancellation = null; phase = Phase.IDLE;
    }

    private void clearGeometry() {
        controlPoints.clear(); lastPlan = List.of(); lastTransforms = List.of(BuilderTransform.identity());
        if (preview != null) preview.clear();
    }

    private void applyRadiusToControlPoints() {
        for (int i = 0; i < controlPoints.size(); i++) {
            SplineControlPoint point = controlPoints.get(i);
            controlPoints.set(i, new SplineControlPoint(point.position(), radius[0], point.rollDegrees()));
        }
    }

    private void rebuildPreview() {
        if (controlPoints.size() < 2) { clearGeometryPreviewOnly(); return; }
        CatmullRomSpline spline = new CatmullRomSpline(controlPoints);
        List<SplineSample> samples = SplineSampler.sample(spline, quality[0]);
        StructureChainSplinePayload payload = new StructureChainSplinePayload(
                spacing[0], (point, seed) -> "lazybuilder:preview-segment",
                new PlacementVariation(0.0, 0.0, 1.0, 1.0, 0.0, 0L));
        lastPlan = payload.plan(samples, new OperationSeed(seedValue[0]));
        lastTransforms = SymmetryPlanner.rotational(
                controlPoints.get(0).position(), new BuilderVec3(0, 1, 0), rotationalCopies[0]);
        ensurePreview().update(lastPlan, lastTransforms);
    }

    private void clearGeometryPreviewOnly() {
        lastPlan = List.of(); lastTransforms = List.of(BuilderTransform.identity());
        if (preview != null) preview.clear();
    }

    private AxiomSplinePreviewRegion ensurePreview() {
        if (preview == null) preview = services.createSplinePreviewRegion();
        return preview;
    }

    private enum Phase { IDLE, DISPATCHING, RECONCILING, ROLLBACK_DISPATCH, ROLLBACK_RECONCILE }

    private record SplineMaterialOperation(
            UUID id, BuilderRegion region, OperationSeed seed, ExecutionBudget executionBudget,
            CancellationToken cancellationToken, BuilderMaterial material
    ) implements MaterialOperation {
        @Override public String type() { return "lazybuilder:spline"; }
        @Override public MutationReadMode readMode() { return MutationReadMode.SNAPSHOT_READ; }
        @Override public HistoryRequirement historyRequirement() { return HistoryRequirement.REQUIRED; }
        @Override public CancellationDisposition cancellationDisposition() { return CancellationDisposition.ROLLBACK; }
        @Override public MaterialMask materialMask() { return MaterialMask.all(); }
    }
}
