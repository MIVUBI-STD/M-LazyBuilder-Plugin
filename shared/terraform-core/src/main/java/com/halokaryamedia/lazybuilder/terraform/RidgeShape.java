package com.halokaryamedia.lazybuilder.terraform;

/** Symmetric path landform with a coherent crest and two terrain slopes. */
public final class RidgeShape implements ShapeField {
    private final RidgeSpec spec;
    private final PathFrame[] frames;

    RidgeShape(RidgeSpec spec) {
        this.spec = spec;
        Vec3d firstTangent = spec.path().tangentAt(0);
        this.frames = PathFrame.build(spec.path(), new Vec3d(-firstTangent.z(), 0.0, firstTangent.x()));
    }

    @Override
    public double sample(double x, double y, double z) {
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
        double crestBreakup = SeededNoise.value2D(
                frame.position().x() / 18.0,
                frame.position().z() / 18.0,
                spec.seed() ^ 0x510E527FL) * 0.045 * spec.variation().micro();
        double surfaceHeight = spec.height() * endpoint * Math.max(0.0, slope)
                * (1.0 + macro * 0.12 + meso * 0.04 + crestBreakup);
        double outsidePath = p.alongDistance() - spec.size() * 0.55;
        double outsideWidth = Math.abs(p.frontDistance()) - halfWidth;
        double outsideBottom = -localY;
        double outsideTop = localY - Math.max(spec.height() * 0.025, surfaceHeight);
        return Math.max(Math.max(outsidePath, outsideWidth), Math.max(outsideBottom, outsideTop));
    }
}
