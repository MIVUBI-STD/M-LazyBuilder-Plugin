package com.halokaryamedia.lazybuilder.terraform;

/** Footprint mountain with macro asymmetry, secondary ridge bias and bounded detail. */
public final class MountainShape implements BoundedShapeField {
    private final MountainSpec spec;
    private final Vec3d side;

    MountainShape(MountainSpec spec) {
        this.spec = spec;
        this.side = new Vec3d(-spec.facing().z(), 0.0, spec.facing().x());
    }

    @Override public double sample(double x, double y, double z) {
        double dx = x - spec.origin().x(); double dz = z - spec.origin().z();
        double forward = dx * spec.facing().x() + dz * spec.facing().z();
        double lateral = dx * side.x() + dz * side.z();
        double localY = y - spec.origin().y();
        double macroWarp = SeededNoise.value2D(x / 34.0, z / 34.0, spec.seed() ^ 0x9B05688CL)
                * spec.size() * 0.10 * spec.variation().macro();
        double fx = (forward + macroWarp * 0.55) / Math.max(1.0, spec.size());
        double fz = (lateral + macroWarp) / Math.max(1.0, spec.size() * 0.86);
        double radius = Math.sqrt(fx * fx + fz * fz);
        double body = Math.pow(Math.max(0.0, 1.0 - radius), 1.28);
        double ridgeBias = Math.max(0.0, 1.0 - Math.abs(fz) * 2.3) * Math.max(0.0, 1.0 - Math.abs(fx + 0.12) * 1.25);
        double meso = SeededNoise.value2D(x / 18.0, z / 18.0, spec.seed() ^ 0x1F83D9ABL) * spec.variation().meso();
        double micro = SeededNoise.value2D(x / 8.0, z / 8.0, spec.seed() ^ 0x5BE0CD19L)
                * spec.height() * 0.025 * spec.variation().micro() * body;
        double surfaceHeight = Math.max(spec.height() * 0.02,
                spec.height() * body * (1.0 + ridgeBias * 0.18 + meso * 0.065) + micro);
        return Math.max((radius - 1.12) * spec.size(), Math.max(-localY, localY - surfaceHeight));
    }

    @Override public ShapeBounds bounds() {
        double radius = spec.size() * 1.18 + 2.0;
        return new ShapeBounds(spec.origin().x() - radius, spec.origin().y(), spec.origin().z() - radius,
                spec.origin().x() + radius, spec.origin().y() + spec.height() * 1.30 + 3.0, spec.origin().z() + radius);
    }
}
