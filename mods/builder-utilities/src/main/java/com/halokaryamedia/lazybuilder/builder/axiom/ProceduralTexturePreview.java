package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.material.BlockStateSource;
import com.halokaryamedia.lazybuilder.builder.material.BuilderMaterial;
import com.halokaryamedia.lazybuilder.builder.material.FractalNoiseField;
import com.halokaryamedia.lazybuilder.builder.material.MaterialContext;
import com.halokaryamedia.lazybuilder.builder.material.MaterialMask;
import com.halokaryamedia.lazybuilder.builder.material.MaterialMasks;
import com.halokaryamedia.lazybuilder.builder.material.ScalarField;
import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementPoint;
import com.halokaryamedia.lazybuilder.builder.region.BlockBounds;

import java.util.ArrayList;
import java.util.List;

/** Deterministic preview sampler shared with the procedural texture tool's material field. */
public final class ProceduralTexturePreview {
    public static final int MAX_PREVIEW_CANDIDATES = 250_000;
    public static final int MAX_PREVIEW_VOXELS = 250_000;
    private static final long NOISE_CHANNEL = 0x544558545552454cL;

    private ProceduralTexturePreview() {}

    /**
     * Exact material preview: resolves the same BuilderMaterial used by commit and
     * returns only positions whose final block state differs from the current world.
     */
    public static List<PlacementPoint> sampleResolved(
            BlockBounds bounds,
            OperationSeed seed,
            BuilderMaterial material,
            BlockStateSource source,
            MaterialMask mask
    ) {
        if (material == null) throw new NullPointerException("material");
        if (source == null) throw new NullPointerException("source");
        if (mask == null) throw new NullPointerException("mask");

        Sampling sampling = Sampling.forBounds(bounds, MAX_PREVIEW_CANDIDATES);
        List<PlacementPoint> selected = new ArrayList<>();
        int ordinal = 0;
        for (long y = bounds.minY(); y <= (long) bounds.maxY(); y += sampling.stepY()) {
            for (long z = bounds.minZ(); z <= (long) bounds.maxZ(); z += sampling.stepZ()) {
                for (long x = bounds.minX(); x <= (long) bounds.maxX(); x += sampling.stepX()) {
                    int worldX = (int) x;
                    int worldY = (int) y;
                    int worldZ = (int) z;
                    String existing = source.stateAt(worldX, worldY, worldZ);
                    MaterialContext context = new MaterialContext(
                            worldX, worldY, worldZ, existing, seed);
                    if (!mask.test(context)) continue;
                    String resolved = material.resolve(context);
                    if (resolved == null || resolved.isBlank()) {
                        throw new IllegalArgumentException("resolved material state must be non-blank");
                    }
                    if (existing.equals(resolved)) continue;
                    if (selected.size() >= MAX_PREVIEW_VOXELS) {
                        throw new IllegalArgumentException(
                                "texture preview exceeds " + MAX_PREVIEW_VOXELS + " changed blocks");
                    }
                    selected.add(new PlacementPoint(worldX, worldY, worldZ, ordinal++));
                }
            }
        }
        return List.copyOf(selected);
    }

    public static FractalNoiseField field(double frequency, int octaves) {
        return new FractalNoiseField(frequency, octaves, 2.0, 0.5, NOISE_CHANNEL);
    }

    public static boolean isDecimated(BlockBounds bounds) {
        if (bounds == null) throw new NullPointerException("bounds");
        Sampling sampling = Sampling.forBounds(bounds, MAX_PREVIEW_CANDIDATES);
        return sampling.stepX() != 1 || sampling.stepY() != 1 || sampling.stepZ() != 1;
    }

    private record Sampling(long stepX, long stepY, long stepZ) {
        private static Sampling forBounds(BlockBounds bounds, long maxSamples) {
            if (maxSamples <= 0) throw new IllegalArgumentException("maxSamples must be > 0");
            long width = (long) bounds.maxX() - bounds.minX() + 1L;
            long height = (long) bounds.maxY() - bounds.minY() + 1L;
            long depth = (long) bounds.maxZ() - bounds.minZ() + 1L;

            double total = (double) width * (double) height * (double) depth;
            if (total <= maxSamples) return new Sampling(1, 1, 1);

            long base = Math.max(1L, (long) Math.ceil(Math.cbrt(total / maxSamples)));
            long sx = base, sy = base, sz = base;
            while (sampleCount(width, height, depth, sx, sy, sz) > maxSamples) {
                long nx = ceilDiv(width, sx);
                long ny = ceilDiv(height, sy);
                long nz = ceilDiv(depth, sz);
                if (nx >= ny && nx >= nz) sx++;
                else if (ny >= nz) sy++;
                else sz++;
            }
            return new Sampling(sx, sy, sz);
        }

        private static long sampleCount(
                long width, long height, long depth,
                long sx, long sy, long sz
        ) {
            long x = ceilDiv(width, sx);
            long y = ceilDiv(height, sy);
            long z = ceilDiv(depth, sz);
            if (x > Long.MAX_VALUE / y) return Long.MAX_VALUE;
            long xy = x * y;
            if (xy > Long.MAX_VALUE / z) return Long.MAX_VALUE;
            return xy * z;
        }

        private static long ceilDiv(long value, long divisor) {
            return 1L + (value - 1L) / divisor;
        }
    }
}
