package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.BuilderRuntime;
import com.halokaryamedia.lazybuilder.builder.history.HistoryRequirement;
import com.halokaryamedia.lazybuilder.builder.material.BlockMaterial;
import com.halokaryamedia.lazybuilder.builder.material.BuilderMaterial;
import com.halokaryamedia.lazybuilder.builder.material.MaterialMask;
import com.halokaryamedia.lazybuilder.builder.material.MaterialOperation;
import com.halokaryamedia.lazybuilder.builder.material.MaterialOperationPreparer;
import com.halokaryamedia.lazybuilder.builder.material.PreparedMaterialMutation;
import com.halokaryamedia.lazybuilder.builder.operation.CancellationDisposition;
import com.halokaryamedia.lazybuilder.builder.operation.CancellationSource;
import com.halokaryamedia.lazybuilder.builder.operation.CancellationToken;
import com.halokaryamedia.lazybuilder.builder.operation.DefaultOperationPlanner;
import com.halokaryamedia.lazybuilder.builder.operation.ExecutionBudget;
import com.halokaryamedia.lazybuilder.builder.operation.MutationReadMode;
import com.halokaryamedia.lazybuilder.builder.operation.OperationLifecycle;
import com.halokaryamedia.lazybuilder.builder.operation.OperationPlan;
import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import com.halokaryamedia.lazybuilder.builder.operation.OperationState;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementVariation;
import com.halokaryamedia.lazybuilder.builder.region.BuilderRegion;
import com.halokaryamedia.lazybuilder.builder.region.DeterministicRegionPlanner;
import com.halokaryamedia.lazybuilder.builder.region.PointSetRegion;
import com.halokaryamedia.lazybuilder.builder.spline.BuilderVec3;
import com.halokaryamedia.lazybuilder.builder.spline.CatmullRomSpline;
import com.halokaryamedia.lazybuilder.builder.spline.SplineControlPoint;
import com.halokaryamedia.lazybuilder.builder.spline.SplinePlacementPlanEntry;
import com.halokaryamedia.lazybuilder.builder.spline.SplineModifierPipeline;
import com.halokaryamedia.lazybuilder.builder.spline.SplineModifiers;
import com.halokaryamedia.lazybuilder.builder.spline.SplineSample;
import com.halokaryamedia.lazybuilder.builder.spline.SplineSampler;
import com.halokaryamedia.lazybuilder.builder.spline.StructureChainSplinePayload;
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

/** End-to-end spline tool backed by the shared durable Axiom mutation controller. */
public final class AxiomSplinePreviewTool implements CustomTool {
    private final AxiomClientServices services;
    private final BuilderRuntime runtime;
    private final AxiomMutationController mutation;
    private final List<SplineControlPoint> controlPoints = new ArrayList<>();
    private final float[] spacing = {4.0f};
    private final float[] radius = {2.0f};
    private final int[] quality = {16};
    private final int[] seedValue = {424242};
    private final int[] rotationalCopies = {1};
    private final int[] parameterization = {1};
    private final float[] taperEndScale = {1.0f};
    private final float[] twistDegrees = {0.0f};
    private final float[] jitter = {0.0f};

    private AxiomSplinePreviewRegion preview;
    private List<SplinePlacementPlanEntry> lastPlan = List.of();
    private List<BuilderTransform> lastTransforms = List.of(BuilderTransform.identity());
    private String idleStatus = "Ready";

    public AxiomSplinePreviewTool(AxiomClientServices services, BuilderRuntime runtime) {
        this.services = Objects.requireNonNull(services, "services");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.mutation = new AxiomMutationController(services, runtime);
    }

    @Override
    public String name() {
        return AxiomSplineToolContract.TOOL_NAME;
    }

    @Override
    public boolean callUseTool() {
        if (mutation.isActive()) return false;
        BlockHitResult hit = services.toolService().raycastBlock();
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return false;
        BlockPos pointPos = hit.getBlockPos().offset(hit.getSide());
        controlPoints.add(new SplineControlPoint(
                new BuilderVec3(pointPos.getX() + 0.5, pointPos.getY() + 0.5, pointPos.getZ() + 0.5),
                radius[0], 0.0));
        rebuildPreview();
        return true;
    }

    @Override
    public boolean callDelete() {
        if (mutation.isActive() || controlPoints.isEmpty()) return false;
        controlPoints.remove(controlPoints.size() - 1);
        rebuildPreview();
        return true;
    }

    @Override
    public boolean callConfirm() {
        if (!AxiomSplineToolContract.WORLD_MUTATION_ENABLED || mutation.isActive() || lastPlan.isEmpty()) {
            return false;
        }
        try {
            startMutation();
            return true;
        } catch (Exception e) {
            idleStatus = "Apply failed: " + safeMessage(e);
            return false;
        }
    }

    @Override
    public void displayImguiOptions() {
        ImGui.textWrapped("Right-click block faces to add spline points. Confirm applies the preview with Axiom's active block through durable History v2 and bounded dispatch.");
        ImGui.separator();

        if (mutation.isActive()) {
            OperationLifecycle lifecycle = mutation.lifecycle();
            ImGui.textWrapped("Operation: " + lifecycle.state() + " | " + mutation.status() + " | "
                    + String.format("%.1f%%", lifecycle.progressFraction() * 100.0));
            if (!lifecycle.state().isTerminal() && ImGui.button("Cancel and Roll Back")) {
                mutation.requestRollbackCancellation();
            }
            return;
        }

        ImGui.textWrapped(idleStatus);
        boolean changed = false;
        changed |= ImGui.sliderFloat("Spacing", spacing, 0.5f, 32.0f);
        boolean radiusChanged = ImGui.sliderFloat("Radius", radius, 0.5f, 16.0f);
        changed |= radiusChanged;
        changed |= ImGui.sliderInt("Preview Quality", quality, 4, 64);
        changed |= ImGui.sliderInt("Seed", seedValue, 0, 999_999);
        changed |= ImGui.sliderInt("Rotational Copies", rotationalCopies, 1, 16);
        changed |= ImGui.sliderInt("Curve Mode (0 Uniform / 1 Centripetal)", parameterization, 0, 1);
        changed |= ImGui.sliderFloat("End Radius Scale", taperEndScale, 0.0f, 4.0f);
        changed |= ImGui.sliderFloat("Twist Degrees", twistDegrees, -720.0f, 720.0f);
        changed |= ImGui.sliderFloat("Jitter", jitter, 0.0f, 4.0f);
        if (ImGui.button("Clear Spline")) {
            reset();
            return;
        }
        if (radiusChanged) applyRadiusToControlPoints();
        if (changed) rebuildPreview();
    }

    @Override
    public void reset() {
        if (!mutation.isActive()) clearGeometry();
    }

    @Override
    public void render(Camera camera, float tickDelta, long time, MatrixStack poseStack, Matrix4f projection) {
        mutation.pump();
        OperationState outcome = mutation.pollOutcome();
        if (outcome != null) {
            idleStatus = "Last operation: " + outcome;
            if (outcome == OperationState.COMPLETED) clearGeometry();
        }
        if (preview != null && !lastPlan.isEmpty()) {
            preview.render(camera, time, poseStack, projection);
        }
    }

    public List<SplineControlPoint> controlPoints() {
        return List.copyOf(controlPoints);
    }

    public List<SplinePlacementPlanEntry> lastPlan() {
        return lastPlan;
    }

    public List<BuilderTransform> lastTransforms() {
        return lastTransforms;
    }

    private void startMutation() throws IOException {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = Objects.requireNonNull(client.world, "Minecraft client world is unavailable");
        List<SplinePreviewVoxelizer.Voxel> voxels =
                SplinePreviewVoxelizer.voxelize(lastPlan, lastTransforms);
        if (voxels.isEmpty()) throw new IllegalStateException("Spline preview contains no blocks");
        if (voxels.size() > AxiomSplineToolContract.MAX_MUTATION_VOXELS) {
            throw new IllegalStateException("Spline exceeds mutation voxel limit");
        }

        PointSetRegion region = new PointSetRegion(voxels.stream()
                .map(v -> new PointSetRegion.Point(v.x(), v.y(), v.z()))
                .toList());
        AxiomBlockStateCodec codec = new AxiomBlockStateCodec(world);
        BuilderMaterial material =
                new BlockMaterial(codec.encode(services.toolService().getActiveBlock()));
        CancellationSource cancellation = new CancellationSource();

        SplineMaterialOperation operation = new SplineMaterialOperation(
                UUID.randomUUID(),
                region,
                new OperationSeed(seedValue[0]),
                runtime.dispatchBudget(),
                cancellation.token(),
                material
        );

        OperationPlan plan =
                new DefaultOperationPlanner(new DeterministicRegionPlanner()).plan(operation);
        long estimateBytes = Math.max(1L, Math.multiplyExact((long) region.size(), 96L));
        Optional<PreparedMaterialMutation> prepared = MaterialOperationPreparer.prepare(
                plan,
                new AxiomClientWorldStateSource(world),
                runtime.history(),
                estimateBytes
        );

        if (prepared.isEmpty()) {
            throw new IllegalStateException("Spline preparation was cancelled");
        }

        mutation.start(world, prepared.get(), cancellation, estimateBytes);
        idleStatus = "Mutation started";
    }

    private void applyRadiusToControlPoints() {
        for (int i = 0; i < controlPoints.size(); i++) {
            SplineControlPoint point = controlPoints.get(i);
            controlPoints.set(i, new SplineControlPoint(
                    point.position(), radius[0], point.rollDegrees()));
        }
    }

    private void rebuildPreview() {
        if (controlPoints.size() < 2) {
            clearGeometryPreviewOnly();
            return;
        }

        CatmullRomSpline spline = new CatmullRomSpline(
                controlPoints,
                parameterization[0] == 0
                        ? SplineParameterization.UNIFORM
                        : SplineParameterization.CENTRIPETAL
        );
        OperationSeed operationSeed = new OperationSeed(seedValue[0]);
        List<SplineSample> samples = SplineSampler.sample(spline, quality[0]);
        samples = SplineModifierPipeline.apply(
                samples,
                SplineModifiers.compose(
                        SplineModifiers.taper(1.0, taperEndScale[0]),
                        SplineModifiers.twist(0.0, twistDegrees[0]),
                        SplineModifiers.jitter(jitter[0], jitter[0], jitter[0] * 0.25, 0x4a49545445524cL)
                ),
                operationSeed
        );
        StructureChainSplinePayload payload = new StructureChainSplinePayload(
                spacing[0],
                (point, seed) -> "lazybuilder:preview-segment",
                new PlacementVariation(0.0, 0.0, 1.0, 1.0, 0.0, 0L)
        );

        lastPlan = payload.plan(samples, operationSeed);
        lastTransforms = SymmetryPlanner.rotational(
                controlPoints.get(0).position(),
                new BuilderVec3(0, 1, 0),
                rotationalCopies[0]
        );
        ensurePreview().update(lastPlan, lastTransforms);
        idleStatus = "Preview ready";
    }

    private void clearGeometryPreviewOnly() {
        lastPlan = List.of();
        lastTransforms = List.of(BuilderTransform.identity());
        if (preview != null) preview.clear();
    }

    private void clearGeometry() {
        controlPoints.clear();
        clearGeometryPreviewOnly();
    }

    private AxiomSplinePreviewRegion ensurePreview() {
        if (preview == null) preview = services.createSplinePreviewRegion();
        return preview;
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private record SplineMaterialOperation(
            UUID id,
            BuilderRegion region,
            OperationSeed seed,
            ExecutionBudget executionBudget,
            CancellationToken cancellationToken,
            BuilderMaterial material
    ) implements MaterialOperation {
        @Override public String type() { return "lazybuilder:spline"; }
        @Override public MutationReadMode readMode() { return MutationReadMode.SNAPSHOT_READ; }
        @Override public HistoryRequirement historyRequirement() { return HistoryRequirement.REQUIRED; }
        @Override public CancellationDisposition cancellationDisposition() { return CancellationDisposition.ROLLBACK; }
        @Override public MaterialMask materialMask() { return MaterialMask.all(); }
    }
}
