package com.halokaryamedia.lazybuilder.utility.visualproof;

import com.halokaryamedia.lazybuilder.utility.connection.ReconnectState;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

/** L4 visual proof for Utility Manager actions injected into vanilla client screens. */
@SuppressWarnings("UnstableApiUsage")
public final class UtilityManagerVisualProofTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext ignored = context.worldBuilder().create()) {
            context.waitTicks(40);
            context.runOnClient(client -> ReconnectState.capture(new ServerInfo(
                    "LazyBuilder Preview",
                    "play.lazybuilder.test:25565",
                    ServerInfo.ServerType.OTHER
            )));

            int previousBlur = context.computeOnClient(client -> client.options.getMenuBackgroundBlurriness().getValue());
            context.runOnClient(client -> client.options.getMenuBackgroundBlurriness().setValue(0));
            try {
                captureDisconnected(context, 1440, 900, 2,
                        "utility-disconnected-actions-1440x900-gui2");
                captureDisconnected(context, 620, 480, 2,
                        "utility-disconnected-actions-620x480-gui2");
            } finally {
                context.runOnClient(client -> client.options.getMenuBackgroundBlurriness().setValue(previousBlur));
            }

            context.setScreen(() -> null);
        }
    }

    private static void captureDisconnected(
            ClientGameTestContext context,
            int width,
            int height,
            int guiScale,
            String screenshotName
    ) {
        context.setScreen(() -> null);
        configureViewport(context, width, height, guiScale);
        context.setScreen(() -> new DisconnectedScreen(
                null,
                Text.literal("Connection Lost"),
                Text.literal("Disconnected by server: preview reason with enough text to exercise the vanilla wrapped-message layout.")));
        context.waitForScreen(DisconnectedScreen.class);
        context.waitTicks(12);
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
