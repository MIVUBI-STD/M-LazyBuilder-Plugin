package com.halokaryamedia.lazybuilder.utility.capture;

/** Persisted capture preferences with a deliberately small user-facing surface. */
public record CapturePreferences(
        ScreenshotQuality screenshotQuality,
        VideoQuality videoQuality,
        VideoFrameRate videoFrameRate,
        VideoEncoderMode videoEncoderMode
) {
    public static CapturePreferences defaults() {
        return new CapturePreferences(
                ScreenshotQuality.HIGH,
                VideoQuality.HIGH,
                VideoFrameRate.FPS_60,
                VideoEncoderMode.AUTOMATIC
        );
    }

    public CapturePreferences {
        screenshotQuality = screenshotQuality == null ? ScreenshotQuality.HIGH : screenshotQuality;
        videoQuality = videoQuality == null ? VideoQuality.HIGH : videoQuality;
        videoFrameRate = videoFrameRate == null ? VideoFrameRate.FPS_60 : videoFrameRate;
        videoEncoderMode = videoEncoderMode == null ? VideoEncoderMode.AUTOMATIC : videoEncoderMode;
    }

    public CapturePreferences withScreenshotQuality(ScreenshotQuality quality) {
        return new CapturePreferences(quality, videoQuality, videoFrameRate, videoEncoderMode);
    }

    public CapturePreferences withVideoQuality(VideoQuality quality) {
        return new CapturePreferences(screenshotQuality, quality, videoFrameRate, videoEncoderMode);
    }

    public CapturePreferences withVideoFrameRate(VideoFrameRate frameRate) {
        return new CapturePreferences(screenshotQuality, videoQuality, frameRate, videoEncoderMode);
    }

    public CapturePreferences withVideoEncoderMode(VideoEncoderMode encoderMode) {
        return new CapturePreferences(screenshotQuality, videoQuality, videoFrameRate, encoderMode);
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

        public String label() { return label; }
        public String format() { return format; }
        public float jpegQuality() { return jpegQuality; }
        public String extension() { return this == MAXIMUM ? ".png" : ".jpg"; }
    }

    public enum VideoQuality {
        EFFICIENT("Efficient", 26),
        HIGH("High", 20),
        PRODUCTION("Production", 16),
        NEAR_LOSSLESS("Near Lossless", 12);

        private final String label;
        private final int qualityValue;

        VideoQuality(String label, int qualityValue) {
            this.label = label;
            this.qualityValue = qualityValue;
        }

        public String label() { return label; }
        public int qualityValue() { return qualityValue; }
    }

    public enum VideoFrameRate {
        FPS_30("30 FPS", 30),
        FPS_60("60 FPS", 60),
        FPS_120("120 FPS", 120);

        private final String label;
        private final int fps;

        VideoFrameRate(String label, int fps) {
            this.label = label;
            this.fps = fps;
        }

        public String label() { return label; }
        public int fps() { return fps; }
    }

    public enum VideoEncoderMode {
        AUTOMATIC("Automatic"),
        SOFTWARE("Software");

        private final String label;

        VideoEncoderMode(String label) {
            this.label = label;
        }

        public String label() { return label; }
    }
}
