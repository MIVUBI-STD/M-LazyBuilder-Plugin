package com.halokaryamedia.lazybuilder.performance.shader;

/**
 * Conservative internal budget for LazyBuilder-owned shader render targets.
 *
 * This is not a VRAM estimator. It only prevents the first-party pipeline from
 * allocating an unbounded amount of known auxiliary texture storage at extreme
 * framebuffer sizes.
 */
public final class ShaderMemoryBudget {
    private static final long MIB = 1024L * 1024L;
    private static final long DEFAULT_MAX_AUXILIARY_BYTES = 512L * MIB;
    private static final long MAX_AUXILIARY_BYTES = configuredLimitBytes();
    private static final long RGBA16F_BYTES_PER_PIXEL = 8L;
    private static final long DEPTH32F_BYTES_PER_PIXEL = 4L;

    private ShaderMemoryBudget() {
    }

    public static Estimate estimate(
            int width,
            int height,
            int gbufferAttachments,
            boolean compositePass,
            boolean postProcessPass,
            boolean shadowPass,
            int shadowResolution
    ) {
        if (width <= 0 || height <= 0) {
            return new Estimate(false, 0L, limitBytes(), "invalid-framebuffer-size");
        }

        try {
            long pixels = Math.multiplyExact((long) width, (long) height);
            int colorTargets = Math.max(0, Math.min(2, gbufferAttachments));

            if (postProcessPass) {
                colorTargets += 1; // scene copy
                if (compositePass) colorTargets += 1; // scratch ping-pong target
            }

            long colorBytes = Math.multiplyExact(
                    Math.multiplyExact(pixels, RGBA16F_BYTES_PER_PIXEL),
                    colorTargets
            );

            long shadowBytes = 0L;
            if (shadowPass) {
                int size = Math.max(256, Math.min(4096, shadowResolution));
                long shadowPixels = Math.multiplyExact((long) size, (long) size);
                shadowBytes = Math.multiplyExact(shadowPixels, DEPTH32F_BYTES_PER_PIXEL);
            }

            long total = Math.addExact(colorBytes, shadowBytes);
            long limit = limitBytes();
            return new Estimate(
                    total <= limit,
                    total,
                    limit,
                    total <= limit ? "ready" : "auxiliary-memory-budget-exceeded"
            );
        } catch (ArithmeticException overflow) {
            return new Estimate(false, Long.MAX_VALUE, limitBytes(), "auxiliary-memory-overflow");
        }
    }

    static long limitBytes() {
        return MAX_AUXILIARY_BYTES;
    }

    private static long configuredLimitBytes() {
        long configuredMiB = Long.getLong("lazybuilder.shader.max_aux_mib", 512L);
        long safeMiB = Math.max(128L, Math.min(2048L, configuredMiB));
        return safeMiB * MIB;
    }

    public record Estimate(
            boolean allowed,
            long estimatedBytes,
            long limitBytes,
            String status
    ) {
    }
}
