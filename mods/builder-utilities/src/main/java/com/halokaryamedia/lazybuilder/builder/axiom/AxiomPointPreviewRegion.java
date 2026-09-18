package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.placement.PlacementPoint;
import com.moulberry.axiomclientapi.Effects;
import com.moulberry.axiomclientapi.regions.BooleanRegion;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Objects;

/** Lightweight BooleanRegion preview for point-based placement tools. */
public final class AxiomPointPreviewRegion implements AutoCloseable {
    private final BooleanRegion region;
    private boolean closed;

    public AxiomPointPreviewRegion(BooleanRegion region) {
        this.region = Objects.requireNonNull(region, "region");
    }

    public void update(List<PlacementPoint> points) {
        ensureOpen();
        region.clear();
        for (PlacementPoint point : points) {
            region.add(point.x(), point.y(), point.z());
        }
    }

    public void render(Camera camera, long time, MatrixStack matrices, Matrix4f projectionMatrix) {
        ensureOpen();
        region.render(Objects.requireNonNull(camera, "camera"), Vec3d.ZERO,
                Objects.requireNonNull(matrices, "matrices"),
                Objects.requireNonNull(projectionMatrix, "projectionMatrix"), time, Effects.OUTLINE);
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
        if (closed) throw new IllegalStateException("Axiom point preview region is closed");
    }
}
