package com.halokaryamedia.lazybuilder.builder.history;

/**
 * Compact chunk-local block position encoding.
 *
 * <p>The low 8 bits store local X/Z (4 bits each). The upper 32 bits store the
 * signed Y coordinate without constraining the supported world height.</p>
 */
public final class LocalBlockPosition {
    private LocalBlockPosition() {
    }

    public static long pack(int localX, int y, int localZ) {
        if (localX < 0 || localX > 15) {
            throw new IllegalArgumentException("localX must be in [0, 15]");
        }
        if (localZ < 0 || localZ > 15) {
            throw new IllegalArgumentException("localZ must be in [0, 15]");
        }
        return (Integer.toUnsignedLong(y) << 8) | ((long) localZ << 4) | localX;
    }

    public static int localX(long packed) {
        return (int) (packed & 0x0fL);
    }

    public static int localZ(long packed) {
        return (int) ((packed >>> 4) & 0x0fL);
    }

    public static int y(long packed) {
        return (int) (packed >>> 8);
    }
}
