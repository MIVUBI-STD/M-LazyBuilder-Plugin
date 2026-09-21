package com.halokaryamedia.lazybuilder.utility.capture;

/** Persisted capture preferences with a deliberately small user-facing surface. */
public record CapturePreferences(ScreenshotQuality screenshotQuality) {
    public static CapturePreferences defaults() {
        return new CapturePreferences(ScreenshotQuality.HIGH);
    }

    public CapturePreferences {
        screenshotQuality = screenshotQuality == null ? ScreenshotQuality.HIGH : screenshotQuality;
    }

    public CapturePreferences withScreenshotQuality(ScreenshotQuality quality) {
        return new CapturePreferences(quality);
    }

    public enum ScreenshotQuality {
        EFFICIENT("Efficient", "JPEG", 0.85F),
        HIGH("High", "JPEG", 0.95F),
        MAXIMUM("Maximum", "PNG", 1.0F);

        private final String label;
        private final String format;
        private final float jpegQuality;

        ScreenshotQuality(String label, String format, float jpegQuality) {
            this.label = label;
            this.format = format;
            this.jpegQuality = jpegQuality;
        }

        public String label() {
            return label;
        }

        public String format() {
            return format;
        }

        public float jpegQuality() {
            return jpegQuality;
        }

        public String extension() {
            return this == MAXIMUM ? ".png" : ".jpg";
        }
    }
}
