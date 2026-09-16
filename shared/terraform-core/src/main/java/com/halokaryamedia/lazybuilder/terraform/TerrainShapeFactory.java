package com.halokaryamedia.lazybuilder.terraform;

import java.util.List;

/** Single neutral reconstruction path used by client preview and Paper execution. */
public final class TerrainShapeFactory {
    private TerrainShapeFactory() {}

    public static BoundedShapeField create(
            TerrainTool tool, List<Vec3d> points, Vec3d front, double size, double height,
            TerrainVariation variation, long seed) {
        if (tool == TerrainTool.MOUNTAIN) {
            if (points.isEmpty()) throw new IllegalArgumentException("mountain requires an origin");
            return new MountainSpec(points.getFirst(), front, size, height, variation, seed).createField();
        }
        TerrainPath path = TerrainPath.prepare(points, Math.max(1.0, size * 0.14));
        if (tool == TerrainTool.CLIFF) return new CliffPathSpec(path, front, size, height, variation, seed).createField();
        if (tool == TerrainTool.RIDGE) return new RidgeSpec(path, size, height, variation, seed).createField();
        throw new IllegalArgumentException("unsupported terrain tool: " + tool);
    }
}
