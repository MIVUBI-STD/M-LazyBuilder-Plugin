package com.halokaryamedia.lazybuilder.utility.visualproof;

import com.halokaryamedia.lazybuilder.utility.UtilityManagerClient;
import com.halokaryamedia.lazybuilder.utility.accessibility.NarratorSuppressionController;
import com.halokaryamedia.lazybuilder.utility.connection.ReconnectState;
import com.halokaryamedia.lazybuilder.utility.debug.CompactDebugRenderer;
import com.halokaryamedia.lazybuilder.utility.ui.LazyBuilderKeybindSettingsScreen;
import com.halokaryamedia.lazybuilder.utility.ui.LazyBuilderDisplayConfirmScreen;
import com.halokaryamedia.lazybuilder.utility.ui.LazyBuilderResourcePackScreen;
import com.halokaryamedia.lazybuilder.utility.ui.LazyBuilderSettingsScreen;
import com.halokaryamedia.lazybuilder.utility.ui.LazyBuilderShaderOptionsScreen;
import com.halokaryamedia.lazybuilder.utility.ui.LazyBuilderShaderScreen;
import com.halokaryamedia.lazybuilder.utility.ui.LazyBuilderSettingsSearchScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.itemgroup.v1.FabricCreativeInventoryScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.NarratorMode;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemGroups;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** L4 visual/runtime proof for Utility Manager actions injected into vanilla client screens. */
@SuppressWarnings("UnstableApiUsage")
public final class UtilityManagerVisualProofTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext ignored = context.worldBuilder().create()) {
            context.waitTicks(40);
            installPerformanceSettingsContract(context);
            verifyNarratorSuppression(context);
            captureCompactDebug(context, 1440, 900, 2,
                    "utility-compact-debug-1440x900-gui2");
            captureCompactDebug(context, 620, 480, 2,
                    "utility-compact-debug-620x480-gui2");
            verifyInstantCreativeSearch(context);
            captureSettings(context, 1440, 900, 2,
                    LazyBuilderSettingsScreen.Category.VIDEO,
                    "utility-settings-video-1440x900-gui2");
            captureSettings(context, 620, 480, 2,
                    LazyBuilderSettingsScreen.Category.VIDEO,
                    "utility-settings-video-620x480-gui2");
            captureScrolledSettings(context, 620, 480, 2,
                    LazyBuilderSettingsScreen.Category.VIDEO,
                    "utility-settings-video-performance-scrolled-620x480-gui2");
            captureVideoDropdown(context, 1440, 900, 2,
                    "utility-settings-video-dropdown-1440x900-gui2");
            captureVideoDropdown(context, 620, 480, 2,
                    "utility-settings-video-dropdown-620x480-gui2");
            captureVideoPage(context, 1440, 900, 2,
                    LazyBuilderSettingsScreen.VideoPage.VISUAL,
                    "utility-settings-video-visual-1440x900-gui2");
            captureVideoPage(context, 620, 480, 2,
                    LazyBuilderSettingsScreen.VideoPage.VISUAL,
                    "utility-settings-video-visual-620x480-gui2");
            captureResourcePackManager(context, 1440, 900, 2,
                    "utility-resource-packs-1440x900-gui2");
            captureResourcePackManager(context, 620, 480, 2,
                    "utility-resource-packs-620x480-gui2");
            captureShaderManager(context, 1440, 900, 2,
                    "utility-shaders-1440x900-gui2");
            captureShaderManager(context, 620, 480, 2,
                    "utility-shaders-620x480-gui2");
            captureShaderOptions(context, 1440, 900, 2,
                    "utility-shader-options-1440x900-gui2");
            captureShaderOptions(context, 620, 480, 2,
                    "utility-shader-options-620x480-gui2");
            captureDisplayRecovery(context, 1440, 900, 2,
                    "utility-display-recovery-1440x900-gui2");
            captureSettings(context, 1440, 900, 2,
                    LazyBuilderSettingsScreen.Category.AUDIO,
                    "utility-settings-audio-1440x900-gui2");
            captureSettings(context, 620, 480, 2,
                    LazyBuilderSettingsScreen.Category.AUDIO,
                    "utility-settings-audio-620x480-gui2");
            captureSettings(context, 1440, 900, 2,
                    LazyBuilderSettingsScreen.Category.ACCESSIBILITY,
                    "utility-settings-accessibility-1440x900-gui2");
            captureSettings(context, 620, 480, 2,
                    LazyBuilderSettingsScreen.Category.ACCESSIBILITY,
                    "utility-settings-accessibility-620x480-gui2");
            captureSettings(context, 1440, 900, 2,
                    LazyBuilderSettingsScreen.Category.CHAT,
                    "utility-settings-chat-1440x900-gui2");
            captureSettings(context, 620, 480, 2,
                    LazyBuilderSettingsScreen.Category.CHAT,
                    "utility-settings-chat-620x480-gui2");
            captureKeybindSearch(context, 1440, 900, 2,
                    "jump",
                    "utility-keybinds-search-jump-1440x900-gui2");
            captureSettingsSearch(context, 1440, 900, 2,
                    "render",
                    "utility-settings-search-render-1440x900-gui2");
            captureSettingsSearch(context, 620, 480, 2,
                    "shader",
                    "utility-settings-search-shader-620x480-gui2");
            captureFocusedSetting(context, 1440, 900, 2,
                    LazyBuilderSettingsScreen.Category.VIDEO,
                    LazyBuilderSettingsScreen.VideoPage.VIEW,
                    "Render Distance",
                    "utility-settings-focus-render-distance-1440x900-gui2");
            captureFocusedSetting(context, 620, 480, 2,
                    LazyBuilderSettingsScreen.Category.CHAT,
                    LazyBuilderSettingsScreen.VideoPage.QUALITY,
                    "Show Report Button",
                    "utility-settings-focus-chat-report-620x480-gui2");
            captureSettings(context, 1440, 900, 2,
                    LazyBuilderSettingsScreen.Category.CONTROLS,
                    "utility-settings-controls-1440x900-gui2");
            captureSettings(context, 620, 480, 2,
                    LazyBuilderSettingsScreen.Category.CONTROLS,
                    "utility-settings-controls-620x480-gui2");
            captureScrolledSettings(context, 620, 480, 2,
                    LazyBuilderSettingsScreen.Category.CONTROLS,
                    "utility-settings-controls-scrolled-620x480-gui2");
            captureKeybindSettings(context, 1440, 900, 2,
                    "utility-keybinds-1440x900-gui2");
            captureKeybindSettings(context, 620, 480, 2,
                    "utility-keybinds-620x480-gui2");
            captureScrolledKeybindSettings(context, 620, 480, 2,
                    "utility-keybinds-scrolled-620x480-gui2");
            captureKeybindConflict(context, 1440, 900, 2,
                    "utility-keybinds-conflict-1440x900-gui2");
            captureSettings(context, 1440, 900, 2,
                    LazyBuilderSettingsScreen.Category.INTERFACE,
                    "utility-settings-interface-1440x900-gui2");
            captureSettings(context, 620, 480, 2,
                    LazyBuilderSettingsScreen.Category.INTERFACE,
                    "utility-settings-interface-620x480-gui2");
            captureLanguageManager(context, 1440, 900, 2,
                    "utility-language-1440x900-gui2");
            captureLanguageManager(context, 620, 480, 2,
                    "utility-language-620x480-gui2");
            captureScrolledSettings(context, 620, 480, 2,
                    LazyBuilderSettingsScreen.Category.INTERFACE,
                    "utility-settings-interface-scrolled-620x480-gui2");
            captureInterfaceResetConfirmation(context, 1440, 900, 2,
                    "utility-settings-interface-reset-confirmation-1440x900-gui2");
            captureModifiedInterfaceSetting(context, 1440, 900, 2,
                    "utility-settings-interface-modified-1440x900-gui2");
            captureSettings(context, 1440, 900, 2,
                    LazyBuilderSettingsScreen.Category.TOOLS,
                    "utility-settings-tools-1440x900-gui2");
            captureSettings(context, 620, 480, 2,
                    LazyBuilderSettingsScreen.Category.TOOLS,
                    "utility-settings-tools-620x480-gui2");

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

    private static void installPerformanceSettingsContract(ClientGameTestContext context) {
        context.runOnClient(client -> {
            AtomicReference<Map<String, Object>> state = new AtomicReference<>(Map.of(
                    "backgroundFpsPolicy", true,
                    "unfocusedFpsLimit", 30,
                    "minimizedFpsLimit", 10,
                    "hiddenObjectSkipping", false,
                    "renderingOptimizations", true,
                    "memoryOptimizations", true
            ));

            var share = FabricLoader.getInstance().getObjectShare();
            share.put(
                    "lazybuilder-performance-manager:settings-snapshot",
                    (Supplier<Map<String, Object>>) state::get
            );
            share.put(
                    "lazybuilder-performance-manager:settings-update",
                    (Consumer<Map<String, Object>>) values -> state.set(Map.copyOf(values))
            );
            share.put(
                    "lazybuilder-performance-manager:settings-status",
                    (Supplier<String>) () -> "applied"
            );

            AtomicReference<Map<String, Object>> shaderState = new AtomicReference<>(Map.ofEntries(
                    Map.entry("revision", 1L),
                    Map.entry("owner", "lazybuilder"),
                    Map.entry("stage", "terrain+postprocess-active"),
                    Map.entry("sourceReady", true),
                    Map.entry("compiledReady", true),
                    Map.entry("postProcessReady", true),
                    Map.entry("shadowReady", true),
                    Map.entry("renderingReady", true),
                    Map.entry("terrainIntegrated", true),
                    Map.entry("configuredEnabled", true),
                    Map.entry("selectedPackId", "studio-shader"),
                    Map.entry("selectedPackName", "Studio Shader"),
                    Map.entry("activePackId", "studio-shader"),
                    Map.entry("activePackName", "Studio Shader"),
                    Map.entry("packIds", List.of("studio-shader")),
                    Map.entry("packNames", List.of("Studio Shader")),
                    Map.entry("shaderpacksDirectory", client.runDirectory.toPath().resolve("shaderpacks").toString()),
                    Map.entry("lastError", ""),
                    Map.entry("compatibilityBlocked", false),
                    Map.entry("compatibilityOwner", ""),
                    Map.entry("options", List.of(
                            Map.of(
                                    "id", "shadows",
                                    "label", "Shadows",
                                    "type", "boolean",
                                    "value", "true",
                                    "default", "true",
                                    "min", "",
                                    "max", "",
                                    "step", ""
                            ),
                            Map.of(
                                    "id", "exposure",
                                    "label", "Exposure",
                                    "type", "float",
                                    "value", "1.0",
                                    "default", "1.0",
                                    "min", "0.5",
                                    "max", "2.0",
                                    "step", "0.25"
                            )
                    ))
            ));
            share.put(
                    "lazybuilder-performance-manager:shader-snapshot",
                    (Supplier<Map<String, Object>>) shaderState::get
            );
            share.put(
                    "lazybuilder-performance-manager:shader-refresh",
                    (Runnable) () -> {}
            );
            share.put(
                    "lazybuilder-performance-manager:shader-select",
                    (Consumer<String>) value -> {}
            );
            share.put(
                    "lazybuilder-performance-manager:shader-compile",
                    (Runnable) () -> {}
            );
            share.put(
                    "lazybuilder-performance-manager:shader-disable",
                    (Runnable) () -> {}
            );
            share.put(
                    "lazybuilder-performance-manager:shader-option-update",
                    (BiConsumer<String, String>) (id, value) -> {}
            );
            share.put(
                    "lazybuilder-performance-manager:shader-options-apply",
                    (Runnable) () -> {}
            );
        });
    }

    private static void verifyNarratorSuppression(ClientGameTestContext context) {
        NarratorMode previousNarrator = context.computeOnClient(client -> client.options.getNarrator().getValue());
        boolean previousHotkey = context.computeOnClient(client -> client.options.getNarratorHotkey().getValue());
        try {
            context.runOnClient(client -> {
                client.options.getNarrator().setValue(NarratorMode.ALL);
                client.options.getNarratorHotkey().setValue(true);
                NarratorSuppressionController.applyIfEnabled(client, true);

                if (client.options.getNarrator().getValue() != NarratorMode.OFF) {
                    throw new AssertionError("Narrator suppression did not force NarratorMode.OFF");
                }
                if (client.options.getNarratorHotkey().getValue()) {
                    throw new AssertionError("Narrator suppression did not disable the narrator hotkey");
                }
            });
        } finally {
            context.runOnClient(client -> {
                client.options.getNarrator().setValue(previousNarrator);
                client.options.getNarratorHotkey().setValue(previousHotkey);
            });
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
        GameMode previousGameMode = context.computeOnClient(client -> {
            if (client.player == null || client.interactionManager == null) {
                throw new AssertionError("Instant Creative Search proof requires an active client player");
            }
            return client.interactionManager.getCurrentGameMode();
        });

        context.runOnClient(client -> {
            if (client.player == null || client.interactionManager == null) {
                throw new AssertionError("Instant Creative Search proof requires an active client player");
            }
            client.interactionManager.setGameModes(GameMode.CREATIVE, previousGameMode);
            client.interactionManager.copyAbilities(client.player);
        });

        try {
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
        } finally {
            context.setScreen(() -> null);
            context.runOnClient(client -> {
                if (client.player != null && client.interactionManager != null) {
                    client.interactionManager.setGameModes(previousGameMode, GameMode.CREATIVE);
                    client.interactionManager.copyAbilities(client.player);
                }
            });
            context.waitTicks(4);
        }
    }

    private static void captureKeybindSearch(
            ClientGameTestContext context,
            int width,
            int height,
            int guiScale,
            String query,
            String screenshotName
    ) {
        context.setScreen(() -> null);
        configureViewport(context, width, height, guiScale);
        context.setScreen(() -> new LazyBuilderKeybindSettingsScreen(null));
        context.waitForScreen(LazyBuilderKeybindSettingsScreen.class);
        context.waitTicks(4);
        context.runOnClient(client -> {
            if (!(client.currentScreen instanceof LazyBuilderKeybindSettingsScreen screen)) {
                throw new AssertionError("Expected LazyBuilderKeybindSettingsScreen");
            }
            for (Element element : screen.children()) {
                if (element instanceof TextFieldWidget field) {
                    field.setText(query);
                    return;
                }
            }
            throw new AssertionError("Expected key binding search field");
        });
        context.waitTicks(6);
        context.takeScreenshot(screenshotName);
        context.setScreen(() -> null);
        context.waitTicks(4);
    }

    private static void captureSettingsSearch(
            ClientGameTestContext context,
            int width,
            int height,
            int guiScale,
            String query,
            String screenshotName
    ) {
        context.setScreen(() -> null);
        configureViewport(context, width, height, guiScale);
        context.setScreen(() -> new LazyBuilderSettingsSearchScreen(null));
        context.waitForScreen(LazyBuilderSettingsSearchScreen.class);
        context.waitTicks(4);
        context.runOnClient(client -> {
            if (!(client.currentScreen instanceof LazyBuilderSettingsSearchScreen screen)) {
                throw new AssertionError("Expected LazyBuilderSettingsSearchScreen");
            }
            for (Element element : screen.children()) {
                if (element instanceof TextFieldWidget field) {
                    field.setText(query);
                    return;
                }
            }
            throw new AssertionError("Expected Settings search field");
        });
        context.waitTicks(6);
        context.takeScreenshot(screenshotName);
        context.setScreen(() -> null);
        context.waitTicks(4);
    }

    private static void captureFocusedSetting(
            ClientGameTestContext context,
            int width,
            int height,
            int guiScale,
            LazyBuilderSettingsScreen.Category category,
            LazyBuilderSettingsScreen.VideoPage page,
            String target,
            String screenshotName
    ) {
        context.setScreen(() -> null);
        configureViewport(context, width, height, guiScale);
        context.setScreen(() -> new LazyBuilderSettingsScreen(null, category, page, target));
        context.waitForScreen(LazyBuilderSettingsScreen.class);
        context.waitTicks(8);
        context.takeScreenshot(screenshotName);
        context.setScreen(() -> null);
        context.waitTicks(4);
    }

    private static void captureVideoPage(
            ClientGameTestContext context,
            int width,
            int height,
            int guiScale,
            LazyBuilderSettingsScreen.VideoPage page,
            String screenshotName
    ) {
        context.setScreen(() -> null);
        configureViewport(context, width, height, guiScale);
        context.setScreen(() -> new LazyBuilderSettingsScreen(
                null,
                LazyBuilderSettingsScreen.Category.VIDEO,
                page
        ));
        context.waitForScreen(LazyBuilderSettingsScreen.class);
        context.waitTicks(8);
        context.takeScreenshot(screenshotName);
        context.setScreen(() -> null);
        context.waitTicks(4);
    }

    private static void captureLanguageManager(
            ClientGameTestContext context,
            int width,
            int height,
            int guiScale,
            String screenshotName
    ) {
        context.setScreen(() -> null);
        configureViewport(context, width, height, guiScale);
        context.setScreen(() -> new LazyBuilderLanguageScreen(null));
        context.waitForScreen(LazyBuilderLanguageScreen.class);
        context.waitTicks(8);
        context.takeScreenshot(screenshotName);
        context.setScreen(() -> null);
        context.waitTicks(4);
    }

    private static void captureShaderOptions(
            ClientGameTestContext context,
            int width,
            int height,
            int guiScale,
            String screenshotName
    ) {
        context.setScreen(() -> null);
        configureViewport(context, width, height, guiScale);
        context.setScreen(() -> new LazyBuilderShaderOptionsScreen(null));
        context.waitForScreen(LazyBuilderShaderOptionsScreen.class);
        context.waitTicks(8);
        context.takeScreenshot(screenshotName);
        context.setScreen(() -> null);
        context.waitTicks(4);
    }

    private static void captureShaderManager(
            ClientGameTestContext context,
            int width,
            int height,
            int guiScale,
            String screenshotName
    ) {
        context.setScreen(() -> null);
        configureViewport(context, width, height, guiScale);
        context.setScreen(() -> new LazyBuilderShaderScreen(null));
        context.waitForScreen(LazyBuilderShaderScreen.class);
        context.waitTicks(8);
        context.takeScreenshot(screenshotName);
        context.setScreen(() -> null);
        context.waitTicks(4);
    }

    private static void captureDisplayRecovery(
            ClientGameTestContext context,
            int width,
            int height,
            int guiScale,
            String screenshotName
    ) {
        context.setScreen(() -> null);
        configureViewport(context, width, height, guiScale);
        context.setScreen(() -> new LazyBuilderDisplayConfirmScreen(
                null,
                () -> {},
                () -> {}
        ));
        context.waitForScreen(LazyBuilderDisplayConfirmScreen.class);
        context.waitTicks(20);
        context.takeScreenshot(screenshotName);
        context.setScreen(() -> null);
        context.waitTicks(4);
    }

    private static void captureResourcePackManager(
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
            return new LazyBuilderResourcePackScreen(
                    null,
                    client.getResourcePackManager(),
                    manager -> {},
                    client.getResourcePackDir()
            );
        });
        context.waitForScreen(LazyBuilderResourcePackScreen.class);
        context.waitTicks(12);
        context.takeScreenshot(screenshotName);
        context.setScreen(() -> null);
        context.waitTicks(4);
    }

    private static void captureVideoDropdown(
            ClientGameTestContext context,
            int width,
            int height,
            int guiScale,
            String screenshotName
    ) {
        context.setScreen(() -> null);
        configureViewport(context, width, height, guiScale);
        context.setScreen(() -> new LazyBuilderSettingsScreen(null, LazyBuilderSettingsScreen.Category.VIDEO));
        context.waitForScreen(LazyBuilderSettingsScreen.class);
        context.waitTicks(4);
        context.runOnClient(client -> {
            if (!(client.currentScreen instanceof LazyBuilderSettingsScreen screen)) {
                throw new AssertionError("Expected LazyBuilderSettingsScreen");
            }
            PressableWidget selector = Screens.getButtons(screen).stream()
                    .filter(widget -> widget.getMessage().getString().endsWith(" FPS"))
                    .filter(PressableWidget.class::isInstance)
                    .map(PressableWidget.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Expected an FPS selector in Video settings"));
            selector.onPress();
        });
        context.waitTicks(4);
        context.takeScreenshot(screenshotName);
        context.setScreen(() -> null);
        context.waitTicks(4);
    }

    private static void captureModifiedInterfaceSetting(
            ClientGameTestContext context,
            int width,
            int height,
            int guiScale,
            String screenshotName
    ) {
        context.setScreen(() -> null);
        configureViewport(context, width, height, guiScale);

        boolean previous = context.computeOnClient(
                client -> UtilityManagerClient.preferences().contextualScreenshotNames()
        );
        context.runOnClient(client -> UtilityManagerClient.updatePreferences(
                UtilityManagerClient.preferences().withContextualScreenshotNames(!previous)
        ));

        try {
            context.setScreen(() -> new LazyBuilderSettingsScreen(
                    null,
                    LazyBuilderSettingsScreen.Category.INTERFACE,
                    LazyBuilderSettingsScreen.VideoPage.QUALITY,
                    "Contextual Screenshot Names"
            ));
            context.waitForScreen(LazyBuilderSettingsScreen.class);
            context.waitTicks(8);
            context.takeScreenshot(screenshotName);
        } finally {
            context.setScreen(() -> null);
            context.runOnClient(client -> UtilityManagerClient.updatePreferences(
                    UtilityManagerClient.preferences().withContextualScreenshotNames(previous)
            ));
            context.waitTicks(4);
        }
    }

    private static void captureInterfaceResetConfirmation(
            ClientGameTestContext context,
            int width,
            int height,
            int guiScale,
            String screenshotName
    ) {
        context.setScreen(() -> null);
        configureViewport(context, width, height, guiScale);
        context.setScreen(() -> new LazyBuilderSettingsScreen(null, LazyBuilderSettingsScreen.Category.INTERFACE));
        context.waitForScreen(LazyBuilderSettingsScreen.class);
        context.waitTicks(4);
        context.runOnClient(client -> {
            if (!(client.currentScreen instanceof LazyBuilderSettingsScreen screen)) {
                throw new AssertionError("Expected LazyBuilderSettingsScreen");
            }
            PressableWidget reset = Screens.getButtons(screen).stream()
                    .filter(widget -> "Reset".equals(widget.getMessage().getString()))
                    .filter(PressableWidget.class::isInstance)
                    .map(PressableWidget.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Expected Interface Reset control"));
            reset.onPress();
        });
        context.waitTicks(4);
        context.takeScreenshot(screenshotName);
        context.setScreen(() -> null);
        context.waitTicks(4);
    }

    private static void captureKeybindConflict(
            ClientGameTestContext context,
            int width,
            int height,
            int guiScale,
            String screenshotName
    ) {
        context.setScreen(() -> null);
        configureViewport(context, width, height, guiScale);

        context.runOnClient(client -> {
            List<KeyBinding> bindings = new ArrayList<>(List.of(client.options.allKeys));
            bindings.sort(Comparator
                    .comparing(KeyBinding::getCategory)
                    .thenComparing(KeyBinding::getTranslationKey));
            if (bindings.size() < 2) {
                throw new AssertionError("Expected at least two key bindings");
            }
            InputUtil.Key conflict = InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_F6);
            bindings.get(0).setBoundKey(conflict);
            bindings.get(1).setBoundKey(conflict);
            KeyBinding.updateKeysByCode();
        });

        try {
            context.setScreen(() -> new LazyBuilderKeybindSettingsScreen(null));
            context.waitForScreen(LazyBuilderKeybindSettingsScreen.class);
            context.waitTicks(8);
            context.takeScreenshot(screenshotName);
        } finally {
            context.setScreen(() -> null);
            context.runOnClient(client -> {
                List<KeyBinding> bindings = new ArrayList<>(List.of(client.options.allKeys));
                bindings.sort(Comparator
                        .comparing(KeyBinding::getCategory)
                        .thenComparing(KeyBinding::getTranslationKey));
                if (bindings.size() >= 2) {
                    bindings.get(0).setBoundKey(bindings.get(0).getDefaultKey());
                    bindings.get(1).setBoundKey(bindings.get(1).getDefaultKey());
                    KeyBinding.updateKeysByCode();
                }
            });
            context.waitTicks(4);
        }
    }

    private static void captureSettings(
            ClientGameTestContext context,
            int width,
            int height,
            int guiScale,
            LazyBuilderSettingsScreen.Category category,
            String screenshotName
    ) {
        context.setScreen(() -> null);
        configureViewport(context, width, height, guiScale);
        context.setScreen(() -> new LazyBuilderSettingsScreen(null, category));
        context.waitForScreen(LazyBuilderSettingsScreen.class);
        context.waitTicks(8);
        context.takeScreenshot(screenshotName);
        context.setScreen(() -> null);
        context.waitTicks(4);
    }

    private static void captureScrolledSettings(
            ClientGameTestContext context,
            int width,
            int height,
            int guiScale,
            LazyBuilderSettingsScreen.Category category,
            String screenshotName
    ) {
        context.setScreen(() -> null);
        configureViewport(context, width, height, guiScale);
        context.setScreen(() -> new LazyBuilderSettingsScreen(null, category));
        context.waitForScreen(LazyBuilderSettingsScreen.class);
        context.waitTicks(4);
        context.runOnClient(client -> {
            if (!(client.currentScreen instanceof LazyBuilderSettingsScreen screen)) {
                throw new AssertionError("Expected LazyBuilderSettingsScreen");
            }
            screen.mouseScrolled(
                    client.getWindow().getScaledWidth() / 2.0,
                    client.getWindow().getScaledHeight() / 2.0,
                    0.0,
                    -6.0
            );
        });
        context.waitTicks(4);
        context.takeScreenshot(screenshotName);
        context.setScreen(() -> null);
        context.waitTicks(4);
    }

    private static void captureKeybindSettings(
            ClientGameTestContext context,
            int width,
            int height,
            int guiScale,
            String screenshotName
    ) {
        context.setScreen(() -> null);
        configureViewport(context, width, height, guiScale);
        context.setScreen(() -> new LazyBuilderKeybindSettingsScreen(null));
        context.waitForScreen(LazyBuilderKeybindSettingsScreen.class);
        context.waitTicks(8);
        context.takeScreenshot(screenshotName);
        context.setScreen(() -> null);
        context.waitTicks(4);
    }

    private static void captureScrolledKeybindSettings(
            ClientGameTestContext context,
            int width,
            int height,
            int guiScale,
            String screenshotName
    ) {
        context.setScreen(() -> null);
        configureViewport(context, width, height, guiScale);
        context.setScreen(() -> new LazyBuilderKeybindSettingsScreen(null));
        context.waitForScreen(LazyBuilderKeybindSettingsScreen.class);
        context.waitTicks(4);
        context.runOnClient(client -> {
            if (!(client.currentScreen instanceof LazyBuilderKeybindSettingsScreen screen)) {
                throw new AssertionError("Expected LazyBuilderKeybindSettingsScreen");
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
