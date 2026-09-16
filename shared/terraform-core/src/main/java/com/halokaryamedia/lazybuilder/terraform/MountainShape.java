package com.halokaryamedia.lazybuilder.terraform;

/** Footprint mountain with a dominant mass, offset crest, secondary ridges and controlled valley cuts. */
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

        double macroWarp = SeededNoise.value2D(x / 38.0, z / 38.0, spec.seed() ^ 0x9B05688CL)
                * spec.size() * 0.085 * spec.variation().macro();
        double fx = (forward + macroWarp * 0.45) / Math.max(1.0, spec.size());
        double fz = (lateral + macroWarp) / Math.max(1.0, spec.size() * 0.88);
        double radius = Math.sqrt(fx * fx + fz * fz);
        double body = Math.pow(Math.max(0.0, 1.0 - radius), 1.18);

        double mainCrest = ridge(fx + 0.12, fz, 1.15, 2.8) * 0.22;
        double branchA = ridge(fx - 0.12, fz + fx * 0.42, 1.55, 4.2) * 0.12;
        double branchB = ridge(fx + 0.18, fz - fx * 0.35, 1.75, 4.8) * 0.09;
        double meso = SeededNoise.value2D(x / 20.0, z / 20.0, spec.seed() ^ 0x1F83D9ABL) * spec.variation().meso();
        double valleyNoise = SeededNoise.value2D(x / 24.0 + 3.7, z / 24.0 - 1.9, spec.seed() ^ 0x5A827999L);
        double valleyCut = Math.max(0.0, valleyNoise) * Math.max(0.0, radius - 0.20)
                * 0.16 * spec.variation().meso();
        double micro = SeededNoise.value2D(x / 9.0, z / 9.0, spec.seed() ^ 0x5BE0CD19L)
                * spec.height() * 0.020 * spec.variation().micro() * body;

        double hierarchy = body + mainCrest * body + branchA * body + branchB * body;
        double surfaceHeight = Math.max(spec.height() * 0.02,
                spec.height() * hierarchy * (1.0 + meso * 0.05 - valleyCut) + micro);
        double footprint = (radius - 1.14) * spec.size();
        return Math.max(footprint, Math.max(-localY, localY - surfaceHeight));
    }

    private static double ridge(double x, double z, double lengthScale, double widthScale) {
        double along = Math.max(0.0, 1.0 - Math.abs(x) / lengthScale);
        double across = Math.max(0.0, 1.0 - Math.abs(z) * widthScale);
        return TerrainMath.smooth(along) * TerrainMath.smooth(across);
    }

    @Override public ShapeBounds bounds() {
        double radius = spec.size() * 1.20 + 3.0;
        return new ShapeBounds(spec.origin().x() - radius, spec.origin().y(), spec.origin().z() - radius,
                spec.origin().x() + radius, spec.origin().y() + spec.height() * 1.34 + 3.0, spec.origin().z() + radius);
    }
}
