package com.halokaryamedia.lazybuilder.utility.capture;

/** Small presentation-only estimates for capture storage planning. */
public final class CaptureEstimates {
    private CaptureEstimates() {}

    public static String screenshotApproximation(
            CapturePreferences.ScreenshotQuality quality,
            int width,
            int height
    ) {
        long pixels = Math.max(1L, (long) Math.max(1, width) * (long) Math.max(1, height));
        double megapixels = pixels / 1_000_000.0D;
        double lowMb;
        double highMb;
        switch (quality) {
            case EFFICIENT -> {
                lowMb = 0.12D * megapixels;
                highMb = 0.35D * megapixels;
            }
            case HIGH -> {
                lowMb = 0.25D * megapixels;
                highMb = 0.70D * megapixels;
            }
            case MAXIMUM -> {
                lowMb = 0.80D * megapixels;
                highMb = 2.50D * megapixels;
            }
            default -> {
                lowMb = 0.25D * megapixels;
                highMb = 0.70D * megapixels;
            }
        }
        return formatRange(lowMb, highMb) + " per image";
    }

    public static String videoApproximation(
            CapturePreferences preferences,
            int sourceWidth,
            int sourceHeight
    ) {
        int[] resolved = preferences.videoResolution().resolve(sourceWidth, sourceHeight);
        double pixels = (double) resolved[0] * (double) resolved[1];
        double referencePixels = 1920.0D * 1080.0D;
        double resolutionFactor = Math.max(0.25D, pixels / referencePixels);
        double fpsFactor = preferences.videoFrameRate().fps() / 60.0D;

        double baseMbps = switch (preferences.videoQuality()) {
            case EFFICIENT -> 8.0D;
            case HIGH -> 16.0D;
            case PRODUCTION -> 32.0D;
            case NEAR_LOSSLESS -> 70.0D;
        };
        double estimatedMbps = baseMbps * resolutionFactor * fpsFactor;
        double lowMb = estimatedMbps * 60.0D * 10.0D / 8.0D * 0.65D;
        double highMb = estimatedMbps * 60.0D * 10.0D / 8.0D * 1.35D;
        return formatRange(lowMb, highMb) + " / 10 min";
    }

    public static String effectiveVideoResolution(
            CapturePreferences preferences,
            int sourceWidth,
            int sourceHeight
    ) {
        int[] size = preferences.videoResolution().resolve(sourceWidth, sourceHeight);
        boolean clamped = preferences.videoResolution().targetHeight() > 0
                && preferences.videoResolution().targetHeight() >= Math.max(2, sourceHeight);
        String suffix = clamped && preferences.videoResolution() != CapturePreferences.VideoResolution.GAME
                ? " · native limit"
                : "";
        return size[0] + "×" + size[1] + suffix;
    }

    private static String formatRange(double lowMb, double highMb) {
        if (highMb < 1000.0D) {
            return Math.max(1L, Math.round(lowMb)) + "–" + Math.max(1L, Math.round(highMb)) + " MB";
        }
        return oneDecimal(lowMb / 1024.0D) + "–" + oneDecimal(highMb / 1024.0D) + " GB";
    }

    private static String oneDecimal(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
