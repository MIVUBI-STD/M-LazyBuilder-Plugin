package com.halokaryamedia.lazybuilder.performance.culling;

import java.util.Set;

/**
 * Immutable render-section openness hint published by the terrain visibility owner.
 *
 * This is deliberately one-way: an open section may bypass expensive ray refinement, while a
 * missing/closed hint never causes culling by itself. Correctness therefore remains fail-open.
 */
public final class SectionVisibilityHint {
    private static volatile Set<Long> openSections = Set.of();

    private SectionVisibilityHint() {
    }

    public static void publish(Set<Long> sections) {
        openSections = sections == null || sections.isEmpty() ? Set.of() : Set.copyOf(sections);
    }

    public static void clear() {
        openSections = Set.of();
    }

    public static boolean isOpen(double blockX, double blockY, double blockZ) {
        int sectionX = Math.floorDiv((int) Math.floor(blockX), 16);
        int sectionY = Math.floorDiv((int) Math.floor(blockY), 16);
        int sectionZ = Math.floorDiv((int) Math.floor(blockZ), 16);
        return openSections.contains(key(sectionX, sectionY, sectionZ));
    }

    public static long key(int sectionX, int sectionY, int sectionZ) {
        return ((long) sectionX & 0x3FFFFFL) << 42
                | ((long) sectionZ & 0x3FFFFFL) << 20
                | ((long) sectionY & 0xFFFFFL);
    }
}
