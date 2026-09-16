package com.halokaryamedia.lazybuilder.terraform;

/** Symmetric path landform with a coherent crest and two terrain slopes. */
public final class RidgeShape implements BoundedShapeField {
    private final RidgeSpec spec;
    private final PathFrame[] frames;

    RidgeShape(RidgeSpec spec) {
        this.spec = spec;
        Vec3d tangent = spec.path().tangentAt(0);
        this.frames = PathFrame.build(spec.path(), new Vec3d(-tangent.z(), 0.0, tangent.x()));
    }

    @Override public double sample(double x, double y, double z) {
        PathShapeSupport.Projection p = PathShapeSupport.nearest(frames, x, z);
        PathFrame frame = p.frame();
        double localY = y - frame.position().y();
        double progress = frame.progress();
        double endpoint = PathShapeSupport.endpointEnvelope(progress);
        double macro = PathShapeSupport.macro(spec.seed(), progress) * spec.variation().macro();
        double meso = PathShapeSupport.meso(spec.seed(), progress) * spec.variation().meso();
        double halfWidth = spec.size() * (0.58 + 0.06 * macro);
        double lateral = Math.abs(p.frontDistance()) / Math.max(1.0, halfWidth);
        double slope = Math.pow(Math.max(0.0, 1.0 - lateral), 1.35);
        double breakup = SeededNoise.value2D(frame.position().x() / 18.0, frame.position().z() / 18.0,
                spec.seed() ^ 0x510E527FL) * 0.045 * spec.variation().micro();
        double surfaceHeight = spec.height() * endpoint * slope * (1.0 + macro * 0.12 + meso * 0.04 + breakup);
        double outsidePath = p.alongDistance() - spec.size() * 0.55;
        double outsideWidth = Math.abs(p.frontDistance()) - halfWidth;
        return Math.max(Math.max(outsidePath, outsideWidth),
                Math.max(-localY, localY - Math.max(spec.height() * 0.025, surfaceHeight)));
    }

    @Override public ShapeBounds bounds() {
        double horizontal = spec.size() * 1.35 + 3.0;
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (Vec3d point : spec.path().points()) {
            minX = Math.min(minX, point.x() - horizontal); maxX = Math.max(maxX, point.x() + horizontal);
            minY = Math.min(minY, point.y()); maxY = Math.max(maxY, point.y() + spec.height() * 1.30 + 3.0);
            minZ = Math.min(minZ, point.z() - horizontal); maxZ = Math.max(maxZ, point.z() + horizontal);
        }
        return new ShapeBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
