package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.spline.SplinePlacementPlanEntry;
import com.halokaryamedia.lazybuilder.builder.symmetry.BuilderTransform;
import com.moulberry.axiomclientapi.Effects;
import com.moulberry.axiomclientapi.regions.BooleanRegion;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Objects;

/**
 * Preview-only adapter backed by Axiom's public BooleanRegion API.
 */
public final class AxiomSplinePreviewRegion implements AutoCloseable {
    private final BooleanRegion region;
    private boolean closed;

    public AxiomSplinePreviewRegion(BooleanRegion region) {
        this.region = Objects.requireNonNull(region, "region");
    }

    public void update(List<SplinePlacementPlanEntry> plan) {
        update(plan, List.of(BuilderTransform.identity()));
    }

    public void update(List<SplinePlacementPlanEntry> plan, List<BuilderTransform> transforms) {
        ensureOpen();
        region.clear();
        for (SplinePreviewVoxelizer.Voxel voxel : SplinePreviewVoxelizer.voxelize(plan, transforms)) {
            region.add(voxel.x(), voxel.y(), voxel.z());
        }
    }

    public void render(Camera camera, long time, MatrixStack matrices, Matrix4f projectionMatrix) {
        ensureOpen();
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(matrices, "matrices");
        Objects.requireNonNull(projectionMatrix, "projectionMatrix");
        region.render(camera, Vec3d.ZERO, matrices, projectionMatrix, time, Effects.OUTLINE);
    }

    public void clear() {
        ensureOpen();
        region.clear();
    }

    @Override
    public void close() {
        if (closed) return;
        region.close();
        closed = true;
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Axiom spline preview region is closed");
    }
}
