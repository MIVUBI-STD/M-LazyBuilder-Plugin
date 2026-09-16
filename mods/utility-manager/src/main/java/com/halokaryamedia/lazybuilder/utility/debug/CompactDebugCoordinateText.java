package com.halokaryamedia.lazybuilder.utility.debug;

/** Canonical X/Y/Z presentation and clipboard ordering for Compact Debug coordinates. */
final class CompactDebugCoordinateText {
    private CompactDebugCoordinateText() {
    }

    static String display(int x, int y, int z) {
        return "X " + x + "   Y " + y + "   Z " + z;
    }

    static String clipboard(int x, int y, int z) {
        return x + " " + y + " " + z;
    }
}
