package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementVariation;
import com.halokaryamedia.lazybuilder.builder.spline.BuilderVec3;
import com.halokaryamedia.lazybuilder.builder.spline.CatmullRomSpline;
import com.halokaryamedia.lazybuilder.builder.spline.SplineControlPoint;
import com.halokaryamedia.lazybuilder.builder.spline.SplinePlacementPlanEntry;
import com.halokaryamedia.lazybuilder.builder.spline.SplineSample;
import com.halokaryamedia.lazybuilder.builder.spline.SplineSampler;
import com.halokaryamedia.lazybuilder.builder.spline.StructureChainSplinePayload;
import com.halokaryamedia.lazybuilder.builder.symmetry.BuilderTransform;
import com.halokaryamedia.lazybuilder.builder.symmetry.SymmetryPlanner;
import com.moulberry.axiomclientapi.CustomTool;
import imgui.moulberry92.ImGui;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Preview-only Axiom tool used to exercise LazyBuilder spline/placement/symmetry planning. */
public final class AxiomSplinePreviewTool implements CustomTool {
    private final AxiomClientServices services;
    private final List<SplineControlPoint> controlPoints = new ArrayList<>();
    private final float[] spacing = {4.0f};
    private final float[] radius = {2.0f};
    private final int[] quality = {16};
    private final int[] seedValue = {424242};
    private final int[] rotationalCopies = {1};

    private AxiomSplinePreviewRegion preview;
    private List<SplinePlacementPlanEntry> lastPlan = List.of();
    private List<BuilderTransform> lastTransforms = List.of(BuilderTransform.identity());

    public AxiomSplinePreviewTool(AxiomClientServices services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    @Override
    public String name() {
        return AxiomSplineToolContract.TOOL_NAME;
    }

    @Override
    public boolean callUseTool() {
        BlockHitResult hit = services.toolService().raycastBlock();
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return false;
        BlockPos hitPos = hit.getBlockPos();
        BlockPos pointPos = hitPos.offset(hit.getSide());
        controlPoints.add(new SplineControlPoint(
                new BuilderVec3(pointPos.getX() + 0.5, pointPos.getY() + 0.5, pointPos.getZ() + 0.5),
                radius[0], 0.0));
        rebuildPreview();
        return true;
    }

    @Override
    public boolean callDelete() {
        if (controlPoints.isEmpty()) return false;
        controlPoints.remove(controlPoints.size() - 1);
        rebuildPreview();
        return true;
    }

    @Override
    public boolean callConfirm() {
        if (AxiomSplineToolContract.WORLD_MUTATION_ENABLED) {
            throw new IllegalStateException("Spline preview tool mutation contract was enabled without an executor");
        }
        return false;
    }

    @Override
    public void displayImguiOptions() {
        ImGui.textWrapped("Right-click block faces to add spline control points. Delete removes the latest point. Rotational copies pivot around the first point on the world Y axis. Build confirmation remains disabled until recovery-safe execution is available.");
        ImGui.separator();
        boolean changed = false;
        changed |= ImGui.sliderFloat("Spacing", spacing, 0.5f, 32.0f);
        boolean radiusChanged = ImGui.sliderFloat("Radius", radius, 0.5f, 16.0f);
        changed |= radiusChanged;
        changed |= ImGui.sliderInt("Preview Quality", quality, 4, 64);
        changed |= ImGui.sliderInt("Seed", seedValue, 0, 999_999);
        changed |= ImGui.sliderInt("Rotational Copies", rotationalCopies, 1, 16);
        if (ImGui.button("Clear Spline")) {
            reset();
            return;
        }
        if (radiusChanged) applyRadiusToControlPoints();
        if (changed) rebuildPreview();
    }

    @Override
    public void reset() {
        controlPoints.clear();
        lastPlan = List.of();
        lastTransforms = List.of(BuilderTransform.identity());
        if (preview != null) preview.clear();
    }

    @Override
    public void render(Camera camera, float tickDelta, long time, MatrixStack poseStack, Matrix4f projection) {
        if (preview != null && !lastPlan.isEmpty()) preview.render(camera, time, poseStack, projection);
    }

    public List<SplineControlPoint> controlPoints() { return List.copyOf(controlPoints); }
    public List<SplinePlacementPlanEntry> lastPlan() { return lastPlan; }
    public List<BuilderTransform> lastTransforms() { return lastTransforms; }

    private void applyRadiusToControlPoints() {
        for (int i = 0; i < controlPoints.size(); i++) {
            SplineControlPoint point = controlPoints.get(i);
            controlPoints.set(i, new SplineControlPoint(point.position(), radius[0], point.rollDegrees()));
        }
    }

    private void rebuildPreview() {
        if (controlPoints.size() < 2) {
            lastPlan = List.of();
            lastTransforms = List.of(BuilderTransform.identity());
            if (preview != null) preview.clear();
            return;
        }
        CatmullRomSpline spline = new CatmullRomSpline(controlPoints);
        List<SplineSample> samples = SplineSampler.sample(spline, quality[0]);
        StructureChainSplinePayload payload = new StructureChainSplinePayload(
                spacing[0],
                (point, seed) -> "lazybuilder:preview-segment",
                new PlacementVariation(0.0, 0.0, 1.0, 1.0, 0.0, 0L));
        lastPlan = payload.plan(samples, new OperationSeed(seedValue[0]));
        lastTransforms = SymmetryPlanner.rotational(
                controlPoints.get(0).position(), new BuilderVec3(0, 1, 0), rotationalCopies[0]);
        ensurePreview().update(lastPlan, lastTransforms);
    }

    private AxiomSplinePreviewRegion ensurePreview() {
        if (preview == null) preview = services.createSplinePreviewRegion();
        return preview;
    }
}
