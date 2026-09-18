package com.halokaryamedia.lazybuilder.builder.region;

/**
 * Compact Minecraft-world block position packing compatible with the vanilla
 * 26/12/26 bit coordinate envelope.
 */
public final class PackedWorldBlockPosition {
    private static final int XZ_BITS = 26;
    private static final int Y_BITS = 12;
    private static final int Y_SHIFT = XZ_BITS;
    private static final int X_SHIFT = Y_BITS + XZ_BITS;
    private static final long XZ_MASK = (1L << XZ_BITS) - 1L;
    private static final long Y_MASK = (1L << Y_BITS) - 1L;
    private static final int XZ_MIN = -(1 << (XZ_BITS - 1));
    private static final int XZ_MAX = (1 << (XZ_BITS - 1)) - 1;
    private static final int Y_MIN = -(1 << (Y_BITS - 1));
    private static final int Y_MAX = (1 << (Y_BITS - 1)) - 1;

    private PackedWorldBlockPosition() {}

    public static long pack(int x, int y, int z) {
        if (x < XZ_MIN || x > XZ_MAX || z < XZ_MIN || z > XZ_MAX) {
            throw new IllegalArgumentException("x/z exceed packed world range");
        }
        if (y < Y_MIN || y > Y_MAX) {
            throw new IllegalArgumentException("y exceeds packed world range");
        }
        return ((x & XZ_MASK) << X_SHIFT)
                | ((z & XZ_MASK) << Y_BITS)
                | (y & Y_MASK);
    }

    public static int x(long packed) {
        return signExtend(packed >>> X_SHIFT, XZ_BITS);
    }

    public static int y(long packed) {
        return signExtend(packed & Y_MASK, Y_BITS);
    }

    public static int z(long packed) {
        return signExtend((packed >>> Y_BITS) & XZ_MASK, XZ_BITS);
    }

    private static int signExtend(long value, int bits) {
        long sign = 1L << (bits - 1);
        long mask = (1L << bits) - 1L;
        long normalized = value & mask;
        return (int) ((normalized ^ sign) - sign);
    }
}
