package com.halokaryamedia.lazybuilder.utility.capture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CapturePreferencesTest {
    @Test
    void resolutionPresetDownscalesButNeverUpscales() {
        assertArrayEquals(
                new int[]{1920, 1080},
                CapturePreferences.VideoResolution.HD_1080.resolve(2560, 1440)
        );
        assertArrayEquals(
                new int[]{1920, 1080},
                CapturePreferences.VideoResolution.UHD_4K.resolve(1920, 1080)
        );
    }

    @Test
    void resolutionPreservesAspectAndEncoderSafeEvenDimensions() {
        int[] resolved = CapturePreferences.VideoResolution.HD_1080.resolve(3440, 1440);

        assertEquals(1080, resolved[1]);
        assertEquals(0, resolved[0] & 1);
        assertTrue(resolved[0] > 1920);
    }

    @Test
    void captureReadbackBudgetAllows4KAndRejectsUnboundedSources() {
        assertTrue(VideoCaptureSession.withinReadbackBudget(3840, 2160));
        assertTrue(VideoCaptureSession.withinReadbackBudget(5120, 2880));
        assertTrue(!VideoCaptureSession.withinReadbackBudget(7680, 4320));
        assertTrue(!VideoCaptureSession.withinReadbackBudget(Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertTrue(!VideoCaptureSession.withinReadbackBudget(0, 2160));
    }

    @Test
    void estimatesRemainPresentationOnlyAndReadable() {
        CapturePreferences preferences = CapturePreferences.defaults()
                .withVideoResolution(CapturePreferences.VideoResolution.HD_1080)
                .withVideoQuality(CapturePreferences.VideoQuality.HIGH);

        assertTrue(CaptureEstimates.videoApproximation(preferences, 2560, 1440).contains("/ 10 min"));
        assertTrue(CaptureEstimates.screenshotApproximation(
                CapturePreferences.ScreenshotQuality.HIGH,
                1920,
                1080
        ).contains("per image"));
    }
}
