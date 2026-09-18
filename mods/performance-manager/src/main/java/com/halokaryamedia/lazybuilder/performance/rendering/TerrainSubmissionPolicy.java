package com.halokaryamedia.lazybuilder.performance.rendering;

/** Pure decision policy for the per-layer terrain submission index. */
public final class TerrainSubmissionPolicy {
    private static final int BLOCK_LAYER_COUNT = 5;
    private static final int MIN_VISIBLE_SECTIONS = 64;

    private TerrainSubmissionPolicy() {
    }

    public static boolean shouldUseIndex(int visibleSections, int layerEntries) {
        if (visibleSections < MIN_VISIBLE_SECTIONS || layerEntries < 0) return false;

        long vanillaVisits = (long) visibleSections * BLOCK_LAYER_COUNT;
        long indexedVisits = (long) visibleSections + layerEntries;
        return indexedVisits < vanillaVisits;
    }

    public static int mappedIteratorIndex(int originalIndex, int originalSize, int filteredSize) {
        if (originalIndex <= 0) return 0;
        if (originalIndex >= originalSize) return filteredSize;
        return Math.min(originalIndex, filteredSize);
    }
}
