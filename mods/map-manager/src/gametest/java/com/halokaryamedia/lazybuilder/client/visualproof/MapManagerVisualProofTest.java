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
import java.util.List;
import java.util.UUID;

/**
 * L4 visual proof for the production Map Manager screen.
 *
 * <p>The Minecraft client is the renderer. Only controller responses are deterministic fixtures;
 * the screen, font, widgets, terrain rendering, GUI scaling and framebuffer are production paths.</p>
 */
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

            resize(context, 1440, 900);
            openMap(context, state);
            context.waitTicks(24);
            context.takeScreenshot("map-manager-wide-managed-worlds");

            resize(context, 900, 600);
            context.waitTicks(12);
            context.takeScreenshot("map-manager-compact-managed-worlds");

            resize(context, 620, 480);
            openMap(context, state);
            context.waitTicks(12);
            context.takeScreenshot("map-manager-narrow-collapsed-sidebar");

            context.setScreen(() -> null);
            state.transfers.shutdownIo();
        }
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

        maps.accept(new MapActionWireProtocol.CurrentWorldResult(
                new WorldId(TANA), "Tana Samawa", "tana_samawa"));

        return new PreviewState(worlds, transfers, maps);
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

    private static void resize(ClientGameTestContext context, int width, int height) {
        context.runOnClient(client -> client.getWindow().setWindowedSize(width, height));
        context.waitTicks(8);
    }

    /**
     * The proof already injects an authoritative CurrentWorldResult. Prevent the screen's normal
     * first-open refresh from attempting a LazyBuilder payload against the integrated vanilla server.
     */
    private static void suppressInitialNetworkRefresh(WorldMapScreen screen) {
        try {
            Field field = WorldMapScreen.class.getDeclaredField("requestedCurrentWorld");
            field.setAccessible(true);
            field.setBoolean(screen, true);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Map visual proof could not suppress test-only network refresh", exception);
        }
    }

    private record PreviewState(
            ClientWorldController worlds,
            ClientTransferController transfers,
            ClientMapController maps
    ) { }
}
