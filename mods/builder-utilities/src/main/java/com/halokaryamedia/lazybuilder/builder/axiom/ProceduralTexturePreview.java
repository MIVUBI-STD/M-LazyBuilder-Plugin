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
        return sample(
                bounds,
                seed,
                field,
                threshold,
                (x, y, z) -> "minecraft:air",
                MaterialMasks.all()
        );
    }

    public static List<PlacementPoint> sample(
            BlockBounds bounds,
            OperationSeed seed,
            ScalarField field,
            double threshold,
            BlockStateSource source,
            MaterialMask mask
    ) {
        if (bounds.blockCount() > MAX_CANDIDATE_VOXELS) {
            throw new IllegalArgumentException(
                    "texture region exceeds " + MAX_CANDIDATE_VOXELS + " candidate blocks");
        }
        if (!Double.isFinite(threshold) || threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("threshold must be in [0,1]");
        }
        if (field == null) throw new NullPointerException("field");
        if (source == null) throw new NullPointerException("source");
        if (mask == null) throw new NullPointerException("mask");

        List<PlacementPoint> selected = new ArrayList<>();
        int ordinal = 0;

        for (long y = bounds.minY(); y <= (long) bounds.maxY(); y++) {
            for (long z = bounds.minZ(); z <= (long) bounds.maxZ(); z++) {
                for (long x = bounds.minX(); x <= (long) bounds.maxX(); x++) {
                    int worldX = (int) x;
                    int worldY = (int) y;
                    int worldZ = (int) z;
                    String existing = source.stateAt(worldX, worldY, worldZ);
                    MaterialContext context = new MaterialContext(
                            worldX, worldY, worldZ, existing, seed);
                    if (!mask.test(context) || field.sample(context) < threshold) continue;
                    if (selected.size() >= MAX_PREVIEW_VOXELS) {
                        throw new IllegalArgumentException(
                                "texture preview exceeds " + MAX_PREVIEW_VOXELS + " selected blocks");
                    }
                    selected.add(new PlacementPoint(worldX, worldY, worldZ, ordinal++));
                }
            }
        }
        return List.copyOf(selected);
    }

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
        if (bounds.blockCount() > MAX_CANDIDATE_VOXELS) {
            throw new IllegalArgumentException(
                    "texture region exceeds " + MAX_CANDIDATE_VOXELS + " candidate blocks");
        }
        if (material == null) throw new NullPointerException("material");
        if (source == null) throw new NullPointerException("source");
        if (mask == null) throw new NullPointerException("mask");

        List<PlacementPoint> selected = new ArrayList<>();
        int ordinal = 0;
        for (long y = bounds.minY(); y <= (long) bounds.maxY(); y++) {
            for (long z = bounds.minZ(); z <= (long) bounds.maxZ(); z++) {
                for (long x = bounds.minX(); x <= (long) bounds.maxX(); x++) {
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
}
