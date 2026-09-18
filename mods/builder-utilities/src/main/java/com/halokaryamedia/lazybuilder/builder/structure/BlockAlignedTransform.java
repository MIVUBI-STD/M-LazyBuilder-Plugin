package com.halokaryamedia.lazybuilder.builder.structure;

import com.halokaryamedia.lazybuilder.builder.placement.PlacementTransform;

/**
 * Lossless grid transform for schematic/template blocks. Arbitrary yaw and
 * non-unit scaling are intentionally rejected instead of silently resampling.
 */
public record BlockAlignedTransform(int quarterTurns, boolean mirrorX) {
    public BlockAlignedTransform {
        quarterTurns = Math.floorMod(quarterTurns, 4);
    }

    public static BlockAlignedTransform fromPlacement(PlacementTransform transform, double epsilonDegrees) {
        if (transform == null) throw new NullPointerException("transform");
        if (!Double.isFinite(epsilonDegrees) || epsilonDegrees < 0.0) {
            throw new IllegalArgumentException("epsilonDegrees must be finite and >= 0");
        }
        if (Math.abs(transform.scale() - 1.0) > 1e-9) {
            throw new IllegalArgumentException("block-aligned structures require scale=1");
        }
        double normalized = ((transform.yawDegrees() % 360.0) + 360.0) % 360.0;
        int turns = (int) Math.round(normalized / 90.0) & 3;
        double target = turns * 90.0;
        double delta = Math.min(Math.abs(normalized - target),
                360.0 - Math.abs(normalized - target));
        if (delta > epsilonDegrees) {
            throw new IllegalArgumentException(
                    "block-aligned structures require yaw near a multiple of 90 degrees");
        }
        return new BlockAlignedTransform(turns, transform.mirrorX());
    }

    public LocalPoint apply(int x, int y, int z) {
        int tx = mirrorX ? Math.negateExact(x) : x;
        return switch (quarterTurns) {
            case 0 -> new LocalPoint(tx, y, z);
            case 1 -> new LocalPoint(Math.negateExact(z), y, tx);
            case 2 -> new LocalPoint(Math.negateExact(tx), y, Math.negateExact(z));
            case 3 -> new LocalPoint(z, y, Math.negateExact(tx));
            default -> throw new AssertionError(quarterTurns);
        };
    }

    public record LocalPoint(int x, int y, int z) {}
}
