package com.halokaryamedia.lazybuilder.performance.visualproof;

import com.halokaryamedia.lazybuilder.performance.ui.PerformanceVideoSettingsScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.Screen;

import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Real Minecraft renderer proof for LazyBuilder performance settings. */
@SuppressWarnings("UnstableApiUsage")
public final class PerformanceManagerVisualProofTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext ignored = context.worldBuilder().create()) {
            context.waitTicks(30);

            verifySettingsBridge(context);

            capturePerformanceSettings(context, 1440, 900, 2,
                    "performance-settings-1440x900-gui2");
            capturePerformanceSettings(context, 620, 480, 2,
                    "performance-settings-620x480-gui2");
            captureScrolledPerformanceSettings(context, 620, 480, 2,
                    "performance-settings-scrolled-620x480-gui2");

            context.setScreen(() -> null);
        }
    }

    @SuppressWarnings("unchecked")
    private static void verifySettingsBridge(ClientGameTestContext context) {
        context.runOnClient(client -> {
            Object shared = FabricLoader.getInstance().getObjectShare()
                    .get("lazybuilder-performance-manager:settings-screen");
            if (!(shared instanceof Function<?, ?> raw)) {
                throw new AssertionError("Performance settings screen provider was not published");
            }
            Screen screen = ((Function<Screen, Screen>) raw).apply(null);
            if (!(screen instanceof PerformanceVideoSettingsScreen)) {
                throw new AssertionError("Performance settings provider returned the wrong screen");
            }
        });
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
