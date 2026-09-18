package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.material.ScalarField;
import com.halokaryamedia.lazybuilder.builder.material.SurfaceCurvatureField;
import com.halokaryamedia.lazybuilder.builder.material.SurfaceSlopeField;
import net.minecraft.client.world.ClientWorld;

import java.util.Objects;

/** Produces normalized [0,1] procedural fields used by Axiom texturing preview and commit. */
public final class AxiomTextureFields {
    private AxiomTextureFields() {}

    public static ScalarField create(
            int mode,
            ClientWorld world,
            double frequency,
            int octaves
    ) {
        Objects.requireNonNull(world, "world");
        return switch (mode) {
            case 0 -> ProceduralTexturePreview.field(frequency, octaves);
            case 1 -> slope(world);
            case 2 -> curvature(world);
            default -> throw new IllegalArgumentException("Unknown texture field mode: " + mode);
        };
    }

    public static String name(int mode) {
        return switch (mode) {
            case 0 -> "Noise";
            case 1 -> "Slope";
            case 2 -> "Curvature";
            default -> "Unknown";
        };
    }

    private static ScalarField slope(ClientWorld world) {
        SurfaceSlopeField slope = new SurfaceSlopeField(
                new AxiomWorldSurfaceHeightSource(world, 0), 1);
        return context -> Math.max(0.0, Math.min(1.0, slope.sample(context) / 90.0));
    }

    private static ScalarField curvature(ClientWorld world) {
        SurfaceCurvatureField curvature = new SurfaceCurvatureField(
                new AxiomWorldSurfaceHeightSource(world, 0), 1, 4.0);
        return context -> Math.max(0.0, Math.min(1.0,
                (curvature.sample(context) + 1.0) * 0.5));
    }
}
