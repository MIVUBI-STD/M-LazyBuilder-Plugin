package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;
import com.halokaryamedia.lazybuilder.world.map.MapActionWireProtocol;
import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.util.List;
import java.util.UUID;

/** L4 visual proof for the production Map Manager, Export workspace, and dedicated transfer surfaces. */
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

                captureWorldManagerEntry(context, state, 1440, 900, 2,
                        "world-manager-export-import-entry-1440x900-gui2");
                captureWorldManagerCompactDetail(context, state, 620, 480, 2,
                        "world-manager-compact-detail-620x480-gui2");

                captureExportWorkspace(context, state, 1440, 900, 2, false, false,
                        "map-manager-export-full-world-1440x900-gui2");
                captureExportWorkspace(context, state, 1440, 900, 2, true, true,
                        "map-manager-export-custom-area-1440x900-gui2");
                captureExportWorkspace(context, state, 620, 480, 2, true, false,
                        "map-manager-export-custom-area-620x480-gui2");

                captureNegativeCoordinateExport(context, state, 1440, 900, 2,
                        "map-manager-export-negative-area-1440x900-gui2");

                captureDedicatedExport(context, state, 1440, 900, 2, true,
                        "world-transfer-export-advanced-1440x900-gui2");
                captureDedicatedExport(context, state, 620, 480, 2, false,
                        "world-transfer-export-narrow-620x480-gui2");

                captureDedicatedImport(context, state, 1440, 900, 2, false, false,
                        "world-transfer-import-initial-1440x900-gui2");
                captureDedicatedImport(context, state, 1440, 900, 2, true, false,
                        "world-transfer-import-advanced-1440x900-gui2");
                captureDedicatedImport(context, state, 1440, 900, 2, true, true,
                        "world-transfer-import-review-1440x900-gui2");
                captureDedicatedImport(context, state, 620, 480, 2, true, true,
                        "world-transfer-import-review-narrow-620x480-gui2");
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

    private static void captureWorldManagerEntry(
            ClientGameTestContext context,
            PreviewState state,
            int width,
            int height,
            int guiScale,
            String screenshotName
    ) {
        context.setScreen(() -> null);
        configureViewport(context, width, height, guiScale);
        context.setScreen(() -> {
            WorldMapScreen parent = new WorldMapScreen(state.worlds, state.transfers, state.maps);
            WorldManagerScreen screen = new WorldManagerScreen(parent, state.worlds, state.transfers, state.maps);
            screen.prepareVisualProof(TANA, false);
            return screen;
        });
        context.waitForScreen(WorldManagerScreen.class);
        context.waitTicks(12);
        context.takeScreenshot(screenshotName);

        context.runOnClient(client -> {
            if (!(client.currentScreen instanceof WorldManagerScreen manager)) {
                throw new IllegalStateException("World Manager proof screen is not open");
            }
            openCurrentWorldExport(manager, state.worlds);
        });
        context.waitForScreen(WorldMapScreen.class);
        context.waitTicks(8);
        context.setScreen(() -> null);
        context.waitTicks(4);
    }

    private static void captureWorldManagerCompactDetail(
            ClientGameTestContext context,
            PreviewState state,
            int width,
            int height,
            int guiScale,
            String screenshotName
    ) {
        context.setScreen(() -> null);
        configureViewport(context, width, height, guiScale);
        context.setScreen(() -> {
            WorldMapScreen parent = new WorldMapScreen(state.worlds, state.transfers, state.maps);
            WorldManagerScreen screen = new WorldManagerScreen(parent, state.worlds, state.transfers, state.maps);
            screen.prepareVisualProof(TANA, true);
            return screen;
        });
        context.waitForScreen(WorldManagerScreen.class);
        context.waitTicks(12);
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
            screen.openExportWorkspaceForProof(customArea);
            if (expandAdvanced) screen.expandWorldSettingsForProof();
        });
        context.waitTicks(18);
        context.takeScreenshot(screenshotName);
        context.setScreen(() -> null);
        context.waitTicks(4);
    }

    private static void captureNegativeCoordinateExport(
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
        context.runOnClient(client -> {
            if (!(client.currentScreen instanceof WorldMapScreen screen)) {
                throw new IllegalStateException("Negative-coordinate Map proof screen is not open");
            }
            screen.openExportWorkspaceForProof(true, -96, -80);
        });
        context.waitTicks(18);
        context.takeScreenshot(screenshotName);
        context.setScreen(() -> null);
        context.waitTicks(4);
    }

    private static void captureDedicatedExport(
            ClientGameTestContext context,
            PreviewState state,
            int width,
            int height,
            int guiScale,
            boolean advanced,
            String screenshotName
    ) {
        context.setScreen(() -> null);
        configureViewport(context, width, height, guiScale);
        context.setScreen(() -> {
            WorldControlWireProtocol.WorldSummary world = state.worlds.worlds().stream()
                    .filter(candidate -> candidate.worldId().equals(MUSEUM))
                    .findFirst()
                    .orElseThrow();
            WorldTransferScreen screen = WorldTransferScreen.forWorldExport(
                    null, state.worlds, state.transfers, world);
            screen.prepareVisualProof(advanced, null);
            return screen;
        });
        context.waitForScreen(WorldTransferScreen.class);
        context.waitTicks(12);
        context.takeScreenshot(screenshotName);
        context.setScreen(() -> null);
        context.waitTicks(4);
    }

    private static void captureDedicatedImport(
            ClientGameTestContext context,
            PreviewState state,
            int width,
            int height,
            int guiScale,
            boolean advanced,
            boolean reviewed,
            String screenshotName
    ) {
        context.setScreen(() -> null);
        configureViewport(context, width, height, guiScale);
        context.setScreen(() -> {
            WorldTransferScreen screen = WorldTransferScreen.forImport(null, state.worlds, state.transfers);
            WorldControlWireProtocol.ImportInspection inspection = reviewed
                    ? new WorldControlWireProtocol.ImportInspection(
                            "preview-import.mcworld",
                            "BEDROCK",
                            "1.21.80",
                            "Mosaic of Us — Imported World With A Deliberately Long Review Name")
                    : null;
            screen.prepareVisualProof(advanced, inspection);
            return screen;
        });
        context.waitForScreen(WorldTransferScreen.class);
        context.waitTicks(12);
        context.takeScreenshot(screenshotName);
        context.setScreen(() -> null);
        context.waitTicks(4);
    }

    private static PreviewState previewState() {
        ClientTransferController transfers = new ClientTransferController();
        ClientWorldController worlds = new ClientWorldController(ignored -> { }, ignored -> { });
        ClientMapController maps = new ClientMapController(ignored -> { }, ignored -> { }, ignored -> { });

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
        context.setScreen(() -> new WorldMapScreen(state.worlds, state.transfers, state.maps));
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

    private static void openCurrentWorldExport(
            WorldManagerScreen manager,
            ClientWorldController worlds
    ) {
        WorldControlWireProtocol.WorldSummary current = worlds.worlds().stream()
                .filter(world -> world.worldId().equals(TANA))
                .findFirst()
                .orElseThrow();
        manager.openExport(current);
    }

    private record PreviewState(
            ClientWorldController worlds,
            ClientTransferController transfers,
            ClientMapController maps
    ) { }
}
