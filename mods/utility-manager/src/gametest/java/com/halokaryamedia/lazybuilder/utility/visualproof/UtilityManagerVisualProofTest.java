package com.halokaryamedia.lazybuilder.utility.visualproof;

import com.halokaryamedia.lazybuilder.utility.connection.ReconnectState;
import com.halokaryamedia.lazybuilder.utility.debug.CompactDebugRenderer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.itemgroup.v1.FabricCreativeInventoryScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.item.ItemGroups;
import net.minecraft.text.Text;

/** L4 visual/runtime proof for Utility Manager actions injected into vanilla client screens. */
@SuppressWarnings("UnstableApiUsage")
public final class UtilityManagerVisualProofTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext ignored = context.worldBuilder().create()) {
            context.waitTicks(40);
            captureCompactDebug(context, 1440, 900, 2,
                    "utility-compact-debug-1440x900-gui2");
            captureCompactDebug(context, 620, 480, 2,
                    "utility-compact-debug-620x480-gui2");
            verifyInstantCreativeSearch(context);

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
                captureMultiplayer(context, 1440, 900, 2,
                        "utility-multiplayer-reconnect-1440x900-gui2");
                captureMultiplayer(context, 620, 480, 2,
                        "utility-multiplayer-reconnect-620x480-gui2");
            } finally {
                context.runOnClient(client -> client.options.getMenuBackgroundBlurriness().setValue(previousBlur));
            }

            context.setScreen(() -> null);
        }
    }

    /**
     * Renderer-only visual fixture for Compact Debug layout at representative GUI widths.
     * The production renderer is used directly; F3/mixin/input acceptance remains a live-client proof boundary.
     */
    private static void captureCompactDebug(
            ClientGameTestContext context,
            int width,
            int height,
            int guiScale,
            String screenshotName
    ) {
        context.setScreen(() -> null);
        configureViewport(context, width, height, guiScale);
        context.setScreen(() -> new Screen(Text.literal("Compact Debug Visual Proof")) {
            @Override
            public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
                CompactDebugRenderer.render(MinecraftClient.getInstance(), drawContext);
            }

            @Override
            public boolean shouldPause() {
                return false;
            }
        });
        context.waitTicks(8);
        context.takeScreenshot(screenshotName);
        context.setScreen(() -> null);
        context.waitTicks(4);
    }

    private static void verifyInstantCreativeSearch(ClientGameTestContext context) {
        context.setScreen(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) {
                throw new AssertionError("Instant Creative Search proof requires a client player");
            }
            return new CreativeInventoryScreen(
                    client.player,
                    client.player.getWorld().getEnabledFeatures(),
                    client.player.hasPermissionLevel(2)
            );
        });
        context.waitForScreen(CreativeInventoryScreen.class);

        context.runOnClient(client -> {
            CreativeInventoryScreen screen = (CreativeInventoryScreen) client.currentScreen;
            FabricCreativeInventoryScreen fabricScreen = (FabricCreativeInventoryScreen) screen;
            var searchGroup = ItemGroups.getSearchGroup();
            var startingGroup = ItemGroups.getGroupsToDisplay().stream()
                    .filter(group -> group != searchGroup)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Creative inventory exposed no non-search item group"));
            fabricScreen.setSelectedItemGroup(startingGroup);
            if (fabricScreen.getSelectedItemGroup() != startingGroup) {
                throw new AssertionError("Creative test did not start from a non-search item group");
            }
        });

        context.getInput().typeChars("stone");
        context.waitTicks(2);

        context.runOnClient(client -> {
            if (!(client.currentScreen instanceof CreativeInventoryScreen screen)) {
                throw new AssertionError("Creative inventory closed while testing instant search");
            }

            FabricCreativeInventoryScreen fabricScreen = (FabricCreativeInventoryScreen) screen;
            if (fabricScreen.getSelectedItemGroup() != ItemGroups.getSearchGroup()) {
                throw new AssertionError("Typing did not switch to the vanilla Search Items tab");
            }

            Element focused = screen.getFocused();
            if (!(focused instanceof TextFieldWidget searchField)) {
                throw new AssertionError("Typing did not focus the vanilla creative search field");
            }
            if (!"stone".equals(searchField.getText())) {
                throw new AssertionError(
                        "Expected the full first-to-last query 'stone', got '" + searchField.getText() + "'"
                );
            }
        });

        context.takeScreenshot("utility-instant-creative-search-stone");
        context.setScreen(() -> null);
        context.waitTicks(4);
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
                Text.literal("Connection to the server was lost. You can reconnect or return to the server list.")));
        context.waitForScreen(DisconnectedScreen.class);
        context.waitTicks(12);
        context.takeScreenshot(screenshotName);
        context.setScreen(() -> null);
        context.waitTicks(4);
    }

    private static void captureMultiplayer(
            ClientGameTestContext context,
            int width,
            int height,
            int guiScale,
            String screenshotName
    ) {
        context.setScreen(() -> null);
        configureViewport(context, width, height, guiScale);
        context.setScreen(() -> new MultiplayerScreen(null));
        context.waitForScreen(MultiplayerScreen.class);
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
