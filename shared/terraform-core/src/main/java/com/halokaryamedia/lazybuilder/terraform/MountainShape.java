package com.halokaryamedia.lazybuilder.terraform;

/** Footprint mountain with dominant mass, offset summit complex, secondary ridges, and bounded valleys. */
public final class MountainShape implements BoundedShapeField {
    private final MountainSpec spec;
    private final Vec3d side;

    MountainShape(MountainSpec spec) {
        this.spec = spec;
        this.side = new Vec3d(-spec.facing().z(), 0.0, spec.facing().x());
    }

    @Override public double sample(double x, double y, double z) {
        double dx = x - spec.origin().x();
        double dz = z - spec.origin().z();
        double forward = dx * spec.facing().x() + dz * spec.facing().z();
        double lateral = dx * side.x() + dz * side.z();
        double localY = y - spec.origin().y();
        double rootDepth = spec.size() * (0.20 + 0.05 * spec.variation().macro());
        double rootBlend = localY >= 0.0 ? 1.0 : TerrainMath.smooth(TerrainMath.clamp01((localY + rootDepth) / Math.max(1.0, rootDepth)));

        double macroWarpX = SeededNoise.value2D(x / 44.0, z / 44.0, spec.seed() ^ 0x9B05688CL)
                * spec.size() * 0.070 * spec.variation().macro();
        double macroWarpZ = SeededNoise.value2D(x / 51.0 + 7.0, z / 51.0 - 5.0, spec.seed() ^ 0x1F83D9ABL)
                * spec.size() * 0.060 * spec.variation().macro();

        double fx = (forward + macroWarpX) / Math.max(1.0, spec.size());
        double fz = (lateral + macroWarpZ) / Math.max(1.0, spec.size() * 0.90);
        double radius = Math.sqrt(fx * fx + fz * fz);

        double body = Math.pow(Math.max(0.0, 1.0 - radius), 1.13);
        double summit = mound(fx + 0.12, fz - 0.03, 0.58, 0.44, 1.55) * 0.24;
        double summitShoulder = mound(fx - 0.16, fz + 0.08, 0.82, 0.64, 1.70) * 0.12;

        double mainRidge = ridge(fx + 0.10, fz, 1.18, 2.65) * 0.20;
        double branchA = ridge(fx - 0.10, fz + fx * 0.46, 1.50, 4.0) * 0.115;
        double branchB = ridge(fx + 0.20, fz - fx * 0.38, 1.70, 4.55) * 0.095;
        double branchC = ridge(fx + 0.02, fz + fx * 0.18 - 0.22, 1.40, 5.1) * 0.060;

        double meso = SeededNoise.value2D(x / 24.0, z / 24.0, spec.seed() ^ 0xA54FF53AL) * spec.variation().meso();
        double valleyA = valley(fx, fz + fx * 0.38 - 0.30, 4.8, 1.30);
        double valleyB = valley(fx + 0.08, fz - fx * 0.42 + 0.27, 5.4, 1.45);
        double valleyNoise = SeededNoise.value2D(x / 31.0 + 3.7, z / 31.0 - 1.9, spec.seed() ^ 0x5A827999L);
        double valleyCut = (valleyA * 0.11 + valleyB * 0.09 + Math.max(0.0, valleyNoise) * 0.055)
                * Math.max(0.0, radius - 0.16) * spec.variation().meso();

        double micro = SeededNoise.value2D(x / 11.0, z / 11.0, spec.seed() ^ 0x5BE0CD19L)
                * spec.height() * 0.016 * spec.variation().micro() * body;

        double hierarchy = body
                + summit * body
                + summitShoulder * body
                + mainRidge * body
                + branchA * body
                + branchB * body
                + branchC * body;

        double macroLift = 1.0 + meso * 0.035 - valleyCut;
        double surfaceHeight = Math.max(spec.height() * 0.02,
                spec.height() * hierarchy * Math.max(0.60, macroLift) + micro);

        double footprintPulse = SeededNoise.value2D(x / 56.0, z / 56.0, spec.seed() ^ 0xCBBB9D5DL)
                * 0.035 * spec.variation().macro();
        double rootedRadius = (1.14 + footprintPulse) * (0.68 + 0.32 * rootBlend);
        double footprint = (radius - rootedRadius) * spec.size();
        return Math.max(footprint, Math.max(-rootDepth - localY, localY - surfaceHeight));
    }

    private static double ridge(double x, double z, double lengthScale, double widthScale) {
        double along = Math.max(0.0, 1.0 - Math.abs(x) / lengthScale);
        double across = Math.max(0.0, 1.0 - Math.abs(z) * widthScale);
        return TerrainMath.smooth(along) * TerrainMath.smooth(across);
    }

    private static double mound(double x, double z, double sx, double sz, double power) {
        double r = Math.sqrt((x * x) / (sx * sx) + (z * z) / (sz * sz));
        return Math.pow(Math.max(0.0, 1.0 - r), power);
    }

    private static double valley(double x, double z, double widthScale, double lengthScale) {
        double across = Math.max(0.0, 1.0 - Math.abs(z) * widthScale);
        double along = Math.max(0.0, 1.0 - Math.abs(x) / lengthScale);
        return TerrainMath.smooth(across) * TerrainMath.smooth(along);
    }

    @Override public ShapeBounds bounds() {
        double radius = spec.size() * 1.22 + 3.0;
        double rootDepth = spec.size() * (0.20 + 0.05 * spec.variation().macro());
        return new ShapeBounds(spec.origin().x() - radius, spec.origin().y() - rootDepth, spec.origin().z() - radius,
                spec.origin().x() + radius, spec.origin().y() + spec.height() * 1.38 + 3.0, spec.origin().z() + radius);
    }
}
