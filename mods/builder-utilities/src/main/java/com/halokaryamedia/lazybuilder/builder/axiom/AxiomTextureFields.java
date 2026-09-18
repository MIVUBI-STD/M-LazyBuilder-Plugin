package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.material.CellularNoiseField;
import com.halokaryamedia.lazybuilder.builder.material.LightLevelField;
import com.halokaryamedia.lazybuilder.builder.material.RidgedNoiseField;
import com.halokaryamedia.lazybuilder.builder.material.ScalarField;
import com.halokaryamedia.lazybuilder.builder.material.SurfaceCurvatureField;
import com.halokaryamedia.lazybuilder.builder.material.SurfaceFlowField;
import com.halokaryamedia.lazybuilder.builder.material.SurfaceSlopeField;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;

import java.util.Objects;

/** Produces normalized [0,1] procedural fields used by Axiom texturing preview and commit. */
public final class AxiomTextureFields {
    private AxiomTextureFields() {}

    public static ScalarField create(
            int mode,
            ClientWorld world,
            double frequency,
            int octaves,
            double flowAngleDegrees
    ) {
        Objects.requireNonNull(world, "world");
        return switch (mode) {
            case 0 -> ProceduralTexturePreview.field(frequency, octaves);
            case 1 -> slope(world);
            case 2 -> curvature(world);
            case 3 -> flow(world, flowAngleDegrees);
            case 4 -> light(world);
            case 5 -> new RidgedNoiseField(
                    ProceduralTexturePreview.field(frequency, octaves), 1.5);
            case 6 -> new CellularNoiseField(frequency, 0x43454c4c554c4152L);
            default -> throw new IllegalArgumentException("Unknown texture field mode: " + mode);
        };
    }

    public static String name(int mode) {
        return switch (mode) {
            case 0 -> "Fractal";
            case 1 -> "Slope";
            case 2 -> "Curvature";
            case 3 -> "Flow";
            case 4 -> "Light";
            case 5 -> "Ridged";
            case 6 -> "Cellular";
            default -> "Unknown";
        };
    }

    private static ScalarField slope(ClientWorld world) {
        SurfaceSlopeField slope = new SurfaceSlopeField(
                new AxiomWorldSurfaceHeightSource(world, 0), 1);
        return context -> clamp01(slope.sample(context) / 90.0);
    }

    private static ScalarField curvature(ClientWorld world) {
        SurfaceCurvatureField curvature = new SurfaceCurvatureField(
                new AxiomWorldSurfaceHeightSource(world, 0), 1, 4.0);
        return context -> clamp01((curvature.sample(context) + 1.0) * 0.5);
    }

    private static ScalarField flow(ClientWorld world, double angleDegrees) {
        if (!Double.isFinite(angleDegrees)) {
            throw new IllegalArgumentException("flow angle must be finite");
        }
        double radians = Math.toRadians(angleDegrees);
        return new SurfaceFlowField(
                new AxiomWorldSurfaceHeightSource(world, 0),
                1,
                Math.cos(radians),
                Math.sin(radians)
        );
    }

    private static ScalarField light(ClientWorld world) {
        return new LightLevelField((x, y, z) -> {
            BlockPos pos = new BlockPos(x, y, z);
            return Math.max(
                    world.getLightLevel(LightType.SKY, pos),
                    world.getLightLevel(LightType.BLOCK, pos)
            );
        });
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
