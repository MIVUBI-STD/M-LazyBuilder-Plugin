package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.material.FractalNoiseField;
import com.halokaryamedia.lazybuilder.builder.material.MaterialContext;
import com.halokaryamedia.lazybuilder.builder.material.ScalarField;
import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import com.halokaryamedia.lazybuilder.builder.placement.PlacementPoint;
import com.halokaryamedia.lazybuilder.builder.region.BlockBounds;

import java.util.ArrayList;
import java.util.List;

/** Deterministic preview sampler shared with the procedural texture tool's material field. */
public final class ProceduralTexturePreview {
    public static final int MAX_CANDIDATE_VOXELS = 1_000_000;
    public static final int MAX_PREVIEW_VOXELS = 250_000;
    private static final long NOISE_CHANNEL = 0x544558545552454cL;

    private ProceduralTexturePreview() {}

    public static List<PlacementPoint> sample(
            BlockBounds bounds,
            OperationSeed seed,
            double frequency,
            int octaves,
            double threshold
    ) {
        if (bounds.blockCount() > MAX_CANDIDATE_VOXELS) {
            throw new IllegalArgumentException(
                    "texture region exceeds " + MAX_CANDIDATE_VOXELS + " candidate blocks");
        }
        if (!Double.isFinite(threshold) || threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("threshold must be in [0,1]");
        }

        return sample(bounds, seed, field(frequency, octaves), threshold);
    }

    public static List<PlacementPoint> sample(
            BlockBounds bounds,
            OperationSeed seed,
            ScalarField field,
            double threshold
    ) {
        if (bounds.blockCount() > MAX_CANDIDATE_VOXELS) {
            throw new IllegalArgumentException(
                    "texture region exceeds " + MAX_CANDIDATE_VOXELS + " candidate blocks");
        }
        if (!Double.isFinite(threshold) || threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("threshold must be in [0,1]");
        }
        if (field == null) throw new NullPointerException("field");

        List<PlacementPoint> selected = new ArrayList<>();
        int ordinal = 0;

        for (long y = bounds.minY(); y <= (long) bounds.maxY(); y++) {
            for (long z = bounds.minZ(); z <= (long) bounds.maxZ(); z++) {
                for (long x = bounds.minX(); x <= (long) bounds.maxX(); x++) {
                    MaterialContext context = new MaterialContext(
                            (int) x, (int) y, (int) z, "minecraft:air", seed);
                    if (field.sample(context) < threshold) continue;
                    if (selected.size() >= MAX_PREVIEW_VOXELS) {
                        throw new IllegalArgumentException(
                                "texture preview exceeds " + MAX_PREVIEW_VOXELS + " selected blocks");
                    }
                    selected.add(new PlacementPoint((int) x, (int) y, (int) z, ordinal++));
                }
            }
        }
        return List.copyOf(selected);
    }

    public static FractalNoiseField field(double frequency, int octaves) {
        return new FractalNoiseField(frequency, octaves, 2.0, 0.5, NOISE_CHANNEL);
    }
}
