package com.halokaryamedia.lazybuilder.client.visualproof;

import com.halokaryamedia.lazybuilder.client.ClientMapController;
import com.halokaryamedia.lazybuilder.client.ClientTransferController;
import com.halokaryamedia.lazybuilder.client.ClientWorldController;
import com.halokaryamedia.lazybuilder.client.WorldMapScreen;
import com.halokaryamedia.lazybuilder.client.WorldNavigationPreferences;
import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;
import com.halokaryamedia.lazybuilder.world.map.MapActionWireProtocol;
import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

/** L4 visual proof for the production Map Manager and Export workspace. */
@SuppressWarnings("UnstableApiUsage")
public final class MapManagerVisualProofTest implements FabricClientGameTest {
    private static final UUID TANA = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID MUSEUM = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID JALUR = UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final UUID RAMPOGAN = UUID.fromString("10000000-0000-0000-0000-000000000004");
    private static final UUID MOSAIC = UUID.fromString("10000000-0000-0000-0000-000000000005");
    private static final UUID PIRATES = UUID.fromString("10000000-0000-0000-0000-000000000006");

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext ignored = context.worldBuilder().create()) {
            context.waitTicks(40);

            PreviewState state = previewState();
            context.runOnClient(client -> pinPreviewWorlds());

            int previousBlur = context.computeOnClient(client -> client.options.getMenuBackgroundBlurriness().getValue());
            context.runOnClient(client -> client.options.getMenuBackgroundBlurriness().setValue(0));
            try {
                captureScenario(context, state, 1440, 900, 2,
                        "map-manager-wide-1440x900-gui2");
                captureScenario(context, state, 900, 600, 2,
                        "map-manager-compact-900x600-gui2");
                captureScenario(context, state, 620, 480, 2,
                        "map-manager-narrow-620x480-gui2");
                captureScenario(context, state, 1440, 900, 3,
                        "map-manager-wide-1440x900-gui3");

                captureExportWorkspace(context, state, 1440, 900, 2, false, false,
                        "map-manager-export-full-world-1440x900-gui2");
                captureExportWorkspace(context, state, 1440, 900, 2, true, true,
                        "map-manager-export-custom-area-1440x900-gui2");
                captureExportWorkspace(context, state, 620, 480, 2, true, false,
                        "map-manager-export-custom-area-620x480-gui2");
            } finally {
                context.runOnClient(client -> client.options.getMenuBackgroundBlurriness().setValue(previousBlur));
            }

            context.setScreen(() -> null);
            state.transfers.shutdownIo();
        }
    }

    private static void captureScenario(
            ClientGameTestContext context,
            PreviewState state,
            int width,
            int height,
            int guiScale,
            String screenshotName
    ) {
        context.setScreen(() -> null);
        configureViewport(context, width, height, guiScale);
        openMap(context, state);
        context.waitTicks(24);
        context.takeScreenshot(screenshotName);
        context.setScreen(() -> null);
        context.waitTicks(4);
    }

    private static void captureExportWorkspace(
            ClientGameTestContext context,
            PreviewState state,
            int width,
            int height,
            int guiScale,
            boolean customArea,
            boolean expandAdvanced,
            String screenshotName
    ) {
        context.setScreen(() -> null);
        configureViewport(context, width, height, guiScale);
        openMap(context, state);
        context.runOnClient(client -> {
            if (!(client.currentScreen instanceof WorldMapScreen screen)) {
                throw new IllegalStateException("Map visual proof screen is not open");
            }
            invokeExportWorkspace(screen, customArea);
            if (expandAdvanced) expandAdvancedSections(screen);
        });
        context.waitTicks(18);
        context.takeScreenshot(screenshotName);
        context.setScreen(() -> null);
        context.waitTicks(4);
    }

    private static PreviewState previewState() {
        ClientTransferController transfers = new ClientTransferController();
        ClientWorldController worlds = new ClientWorldController(ignored -> { });
        ClientMapController maps = new ClientMapController(ignored -> { });

        worlds.accept(new WorldControlWireProtocol.WorldList(List.of(
                world(TANA, "tana_samawa", "Tana Samawa", "IMPORTED", "ACTIVE"),
                world(MUSEUM, "museum_khatulistiwa", "Museum Khatulistiwa", "IMPORTED", "ACTIVE"),
                world(JALUR, "jalur_tanam", "Jalur Tanam", "IMPORTED", "ACTIVE"),
                world(RAMPOGAN, "rampogan_arena", "Rampogan Arena", "FLAT", "ACTIVE"),
                world(MOSAIC, "mosaic_of_us", "Mosaic of Us — Long Managed World Name", "IMPORTED", "ACTIVE"),
                world(PIRATES, "pirates_global_south", "Pirates of Global South", "IMPORTED", "ACTIVE")
        ), true, true));

        worlds.accept(new WorldControlWireProtocol.ExportFormats(List.of(
                "JAVA_1_21_4", "BEDROCK_1_21_80")));
        worlds.accept(new WorldControlWireProtocol.SettingsSnapshot(
                TANA,
                "CREATIVE",
                "NORMAL",
                false,
                "CLEAR",
                6000L,
                32.0,
                72.0,
                -48.0,
                List.of(
                        rule("keepInventory", "BOOLEAN", "false"),
                        rule("mobGriefing", "BOOLEAN", "true"),
                        rule("doMobSpawning", "BOOLEAN", "true"),
                        rule("doDaylightCycle", "BOOLEAN", "true"),
                        rule("doWeatherCycle", "BOOLEAN", "true"),
                        rule("doFireTick", "BOOLEAN", "true"),
                        rule("naturalRegeneration", "BOOLEAN", "true"),
                        rule("randomTickSpeed", "INTEGER", "3")
                )
        ));

        maps.accept(new MapActionWireProtocol.CurrentWorldResult(
                new WorldId(TANA), "Tana Samawa", "tana_samawa"));

        return new PreviewState(worlds, transfers, maps);
    }

    private static WorldControlWireProtocol.GameRuleValue rule(String name, String type, String value) {
        return new WorldControlWireProtocol.GameRuleValue(name, type, value);
    }

    private static WorldControlWireProtocol.WorldSummary world(
            UUID id,
            String folder,
            String name,
            String kind,
            String lifecycle
    ) {
        return new WorldControlWireProtocol.WorldSummary(
                id, folder, name, kind, lifecycle, "CREATIVE");
    }

    private static void pinPreviewWorlds() {
        WorldNavigationPreferences preferences = WorldNavigationPreferences.shared();
        preferences.reload();
        for (UUID id : List.of(MUSEUM, JALUR, RAMPOGAN)) {
            if (!preferences.isPinned(id)) preferences.togglePinned(id);
        }
    }

    private static void openMap(ClientGameTestContext context, PreviewState state) {
        context.setScreen(() -> {
            WorldMapScreen screen = new WorldMapScreen(state.worlds, state.transfers, state.maps);
            suppressInitialNetworkRefresh(screen);
            return screen;
        });
        context.waitForScreen(WorldMapScreen.class);
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

    private static void suppressInitialNetworkRefresh(WorldMapScreen screen) {
        setBooleanField(screen, "requestedCurrentWorld", true,
                "Map visual proof could not suppress test-only network refresh");
    }

    private static void invokeExportWorkspace(WorldMapScreen screen, boolean customArea) {
        try {
            Method method = WorldMapScreen.class.getDeclaredMethod("openExportWorkspaceForProof", boolean.class);
            method.setAccessible(true);
            method.invoke(screen, customArea);
            setBooleanField(screen, "requestedExportSettings", true,
                    "Export proof could not suppress test-only settings refresh");
            setBooleanField(screen, "requestedExportFormats", true,
                    "Export proof could not suppress test-only format refresh");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not open production Export workspace for visual proof", exception);
        }
    }

    private static void expandAdvancedSections(WorldMapScreen screen) {
        try {
            Field stateField = WorldMapScreen.class.getDeclaredField("exportWorkspace");
            stateField.setAccessible(true);
            Object state = stateField.get(screen);
            Method worldSettings = state.getClass().getDeclaredMethod("toggleWorldSettings");
            Method gameRules = state.getClass().getDeclaredMethod("toggleGameRules");
            worldSettings.setAccessible(true);
            gameRules.setAccessible(true);
            worldSettings.invoke(state);
            gameRules.invoke(state);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not expand production Export settings for visual proof", exception);
        }
    }

    private static void setBooleanField(Object target, String name, boolean value, String message) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.setBoolean(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(message, exception);
        }
    }

    private record PreviewState(
            ClientWorldController worlds,
            ClientTransferController transfers,
            ClientMapController maps
    ) { }
}
