package com.halokaryamedia.lazybuilder.performance.visualproof;

import com.halokaryamedia.lazybuilder.performance.ui.PerformanceVideoSettingsScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.option.VideoOptionsScreen;

/** Real Minecraft renderer proof for LazyBuilder performance settings. */
@SuppressWarnings("UnstableApiUsage")
public final class PerformanceManagerVisualProofTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext ignored = context.worldBuilder().create()) {
            context.waitTicks(30);

            captureVideoIntegration(context, 1440, 900, 2,
                    "performance-video-integration-1440x900-gui2");
            captureVideoIntegration(context, 620, 480, 2,
                    "performance-video-integration-620x480-gui2");

            capturePerformanceSettings(context, 1440, 900, 2,
                    "performance-settings-1440x900-gui2");
            capturePerformanceSettings(context, 620, 480, 2,
                    "performance-settings-620x480-gui2");
            captureScrolledPerformanceSettings(context, 620, 480, 2,
                    "performance-settings-scrolled-620x480-gui2");

            context.setScreen(() -> null);
        }
    }

    private static void captureVideoIntegration(
            ClientGameTestContext context,
            int width,
            int height,
            int guiScale,
            String screenshotName
    ) {
        context.setScreen(() -> null);
        configureViewport(context, width, height, guiScale);
        context.setScreen(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            return new VideoOptionsScreen(null, client, client.options);
        });
        context.waitForScreen(VideoOptionsScreen.class);
        context.waitTicks(8);

        context.runOnClient(client -> {
            if (!(client.currentScreen instanceof VideoOptionsScreen screen)) {
                throw new AssertionError("Expected VideoOptionsScreen");
            }
            boolean found = Screens.getButtons(screen).stream()
                    .anyMatch(button -> "Performance...".equals(button.getMessage().getString()));
            if (!found) {
                throw new AssertionError("Video Settings did not expose the Performance entry");
            }
        });

        context.takeScreenshot(screenshotName);
        context.setScreen(() -> null);
        context.waitTicks(4);
    }

    private static void capturePerformanceSettings(
            ClientGameTestContext context,
            int width,
            int height,
            int guiScale,
            String screenshotName
    ) {
        context.setScreen(() -> null);
        configureViewport(context, width, height, guiScale);
        context.setScreen(() -> new PerformanceVideoSettingsScreen(null));
        context.waitForScreen(PerformanceVideoSettingsScreen.class);
        context.waitTicks(8);
        context.takeScreenshot(screenshotName);
        context.setScreen(() -> null);
        context.waitTicks(4);
    }

    private static void captureScrolledPerformanceSettings(
            ClientGameTestContext context,
            int width,
            int height,
            int guiScale,
            String screenshotName
    ) {
        context.setScreen(() -> null);
        configureViewport(context, width, height, guiScale);
        context.setScreen(() -> new PerformanceVideoSettingsScreen(null));
        context.waitForScreen(PerformanceVideoSettingsScreen.class);
        context.waitTicks(4);
        context.runOnClient(client -> {
            if (!(client.currentScreen instanceof PerformanceVideoSettingsScreen screen)) {
                throw new AssertionError("Expected PerformanceVideoSettingsScreen");
            }
            screen.mouseScrolled(
                    client.getWindow().getScaledWidth() / 2.0,
                    client.getWindow().getScaledHeight() / 2.0,
                    0.0,
                    -8.0
            );
        });
        context.waitTicks(4);
        context.takeScreenshot(screenshotName);
        context.setScreen(() -> null);
        context.waitTicks(4);
    }

    private static void configureViewport(
            ClientGameTestContext context,
            int width,
            int height,
            int guiScale
    ) {
        context.runOnClient(client -> {
            client.options.getGuiScale().setValue(guiScale);
            client.getWindow().setWindowedSize(width, height);
            client.onResolutionChanged();
        });
        context.waitTicks(10);
    }
}
