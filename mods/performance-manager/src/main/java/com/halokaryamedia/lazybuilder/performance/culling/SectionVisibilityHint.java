package com.halokaryamedia.lazybuilder.performance.culling;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * Reusable render-section openness hint published by the terrain visibility owner.
 *
 * This is deliberately one-way: an open section may bypass expensive ray refinement, while a
 * missing/closed hint never causes culling by itself. Both publisher and consumers run on the
 * Minecraft client/render thread, allowing storage reuse without cross-thread synchronization.
 */
public final class SectionVisibilityHint {
    private static final LongOpenHashSet OPEN_SECTIONS = new LongOpenHashSet();

    private SectionVisibilityHint() {
    }

    public static void publish(LongOpenHashSet sections) {
        OPEN_SECTIONS.clear();
        if (sections != null && !sections.isEmpty()) OPEN_SECTIONS.addAll(sections);
    }

    public static void clear() {
        OPEN_SECTIONS.clear();
    }

    public static boolean isOpen(double blockX, double blockY, double blockZ) {
        int sectionX = Math.floorDiv((int) Math.floor(blockX), 16);
        int sectionY = Math.floorDiv((int) Math.floor(blockY), 16);
        int sectionZ = Math.floorDiv((int) Math.floor(blockZ), 16);
        return OPEN_SECTIONS.contains(key(sectionX, sectionY, sectionZ));
    }

    public static long key(int sectionX, int sectionY, int sectionZ) {
        return ((long) sectionX & 0x3FFFFFL) << 42
                | ((long) sectionZ & 0x3FFFFFL) << 20
                | ((long) sectionY & 0xFFFFFL);
    }
}
