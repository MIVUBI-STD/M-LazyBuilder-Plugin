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
import com.moulberry.axiomclientapi.CustomTool;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * First Axiom-native Builder Utilities tool. It deliberately previews only and
 * owns no world-mutation path while operation recovery semantics are unfinished.
 */
public final class AxiomSplinePreviewTool implements CustomTool {
    private static final int SAMPLES_PER_SEGMENT = 16;
    private static final double DEFAULT_RADIUS = 2.0;
    private static final double DEFAULT_SPACING = 4.0;

    private final AxiomClientServices services;
    private final List<SplineControlPoint> controlPoints = new ArrayList<>();
    private final StructureChainSplinePayload payload = new StructureChainSplinePayload(
            DEFAULT_SPACING,
            (point, seed) -> "lazybuilder:preview-segment",
            new PlacementVariation(0.0, 0.0, 1.0, 1.0, 0.0, 0L)
    );
    private final OperationSeed seed = new OperationSeed(0x4c4253504c494e45L);

    private AxiomSplinePreviewRegion preview;
    private List<SplinePlacementPlanEntry> lastPlan = List.of();

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
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        BlockPos hitPos = hit.getBlockPos();
        BlockPos pointPos = hitPos.offset(hit.getSide());
        controlPoints.add(new SplineControlPoint(
                new BuilderVec3(pointPos.getX() + 0.5, pointPos.getY() + 0.5, pointPos.getZ() + 0.5),
                DEFAULT_RADIUS,
                0.0
        ));
        rebuildPreview();
        return true;
    }

    @Override
    public boolean callDelete() {
        if (controlPoints.isEmpty()) {
            return false;
        }
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
    public void reset() {
        controlPoints.clear();
        lastPlan = List.of();
        if (preview != null) {
            preview.clear();
        }
    }

    @Override
    public void render(Camera camera, float tickDelta, long time, MatrixStack poseStack, Matrix4f projection) {
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

    private void rebuildPreview() {
        if (controlPoints.size() < 2) {
            lastPlan = List.of();
            if (preview != null) {
                preview.clear();
            }
            return;
        }
        CatmullRomSpline spline = new CatmullRomSpline(controlPoints);
        List<SplineSample> samples = SplineSampler.sample(spline, SAMPLES_PER_SEGMENT);
        lastPlan = payload.plan(samples, seed);
        ensurePreview().update(lastPlan);
    }

    private AxiomSplinePreviewRegion ensurePreview() {
        if (preview == null) {
            preview = services.createSplinePreviewRegion();
        }
        return preview;
    }
}
