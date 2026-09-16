package com.halokaryamedia.lazybuilder.terraform;

final class PathShapeSupport {
    private PathShapeSupport() {}

    record Projection(PathFrame frame, double alongDistance, double frontDistance) {}

    static Projection nearest(PathFrame[] frames, double x, double z) {
        PathFrame best = frames[0];
        double bestDistance = Double.POSITIVE_INFINITY;
        double bestFront = 0.0;
        for (PathFrame frame : frames) {
            double dx = x - frame.position().x();
            double dz = z - frame.position().z();
            double along = dx * frame.tangent().x() + dz * frame.tangent().z();
            double front = dx * frame.front().x() + dz * frame.front().z();
            double distance = Math.hypot(along, front);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = frame;
                bestFront = front;
            }
        }
        return new Projection(best, bestDistance, bestFront);
    }

    static double endpointEnvelope(double progress) {
        double t = TerrainMath.clamp01(progress);
        double taper = Math.min(TerrainMath.smooth(t / 0.12), TerrainMath.smooth((1.0 - t) / 0.12));
        return 0.42 + 0.58 * taper;
    }

    static double macro(long seed, double progress) {
        return SeededNoise.value1D(progress * 4.5, seed ^ 0x6A09E667L);
    }

    static double meso(long seed, double progress) {
        return SeededNoise.value1D(progress * 9.0 + 17.0, seed ^ 0xBB67AE85L);
    }
}
