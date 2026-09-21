package com.halokaryamedia.lazybuilder.utility.capture;

import com.halokaryamedia.lazybuilder.utility.UtilityManagerClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.text.Text;

import java.io.File;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

/** Single runtime owner for screenshot capture and future video capture sessions. */
public final class CaptureManager {
    private static final ScreenshotCaptureService SCREENSHOTS = new ScreenshotCaptureService();

    private static CaptureConfigStore configStore;
    private static volatile CapturePreferences preferences = CapturePreferences.defaults();

    private CaptureManager() {}

    public static void initialize(Path configDirectory) {
        configStore = new CaptureConfigStore(configDirectory);
        preferences = configStore.load();
    }

    public static CapturePreferences preferences() {
        return preferences;
    }

    public static void updatePreferences(CapturePreferences updated) {
        preferences = Objects.requireNonNull(updated, "updated");
        CaptureConfigStore store = configStore;
        if (store != null) store.save(updated);
    }

    public static boolean captureScreenshot(
            File gameDirectory,
            String requestedFileName,
            Framebuffer framebuffer,
            Consumer<Text> messageReceiver
    ) {
        CapturePreferences current = preferences;
        return SCREENSHOTS.capture(
                gameDirectory,
                requestedFileName,
                framebuffer,
                messageReceiver,
                current.screenshotQuality(),
                UtilityManagerClient.preferences().contextualScreenshotNames()
        );
    }

    public static void shutdown() {
        SCREENSHOTS.shutdown();
    }
}
