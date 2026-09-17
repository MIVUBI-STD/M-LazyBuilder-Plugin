package com.halokaryamedia.lazybuilder.performance.rendering;

/**
 * Capacity policy for writable GPU buffers.
 *
 * Small growth headroom reduces repeated native/GPU reallocations while keeping over-allocation bounded.
 * This policy never shrinks an existing allocation.
 */
public final class GpuBufferGrowthPolicy {
    private static final int GRANULARITY = 4 * 1024;
    private static final int MAX_HEADROOM = 1024 * 1024;

    private GpuBufferGrowthPolicy() {
    }

    public static int capacityFor(int currentSize, int requiredSize) {
        if (requiredSize <= 0) return Math.max(0, currentSize);
        if (requiredSize <= currentSize) return currentSize;

        long proportionalHeadroom = Math.max(GRANULARITY, requiredSize / 4L);
        long headroom = Math.min(MAX_HEADROOM, proportionalHeadroom);
        long target = (long) requiredSize + headroom;
        long aligned = ((target + GRANULARITY - 1L) / GRANULARITY) * GRANULARITY;

        return aligned > Integer.MAX_VALUE ? requiredSize : (int) aligned;
    }
}
