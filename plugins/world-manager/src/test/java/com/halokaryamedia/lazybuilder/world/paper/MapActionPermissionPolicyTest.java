package com.halokaryamedia.lazybuilder.world.paper;

import com.halokaryamedia.lazybuilder.world.export.ExportSettingsWire;
import com.halokaryamedia.lazybuilder.world.map.MapActionWireProtocol;
import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class MapActionPermissionPolicyTest {
    @Test
    void currentWorldAllowsEitherWorldPermission() {
        var request = new MapActionWireProtocol.CurrentWorldRequest(11L);

        assertNull(MapActionPermissionPolicy.denial(
                request, Set.of(PaperMapActionPayloadAdapter.TELEPORT_PERMISSION)::contains));
        assertNull(MapActionPermissionPolicy.denial(
                request, Set.of(PaperMapActionPayloadAdapter.MANAGE_PERMISSION)::contains));
        assertEquals(
                "Missing LazyBuilder world permission",
                MapActionPermissionPolicy.denial(request, ignored -> false));
    }

    @Test
    void teleportRequiresTeleportPermission() {
        var request = new MapActionWireProtocol.TeleportLocation(
                12L, WorldId.create(), -128, -64);

        assertNull(MapActionPermissionPolicy.denial(
                request, Set.of(PaperMapActionPayloadAdapter.TELEPORT_PERMISSION)::contains));
        assertEquals(
                "Missing permission: " + PaperMapActionPayloadAdapter.TELEPORT_PERMISSION,
                MapActionPermissionPolicy.denial(
                        request, Set.of(PaperMapActionPayloadAdapter.MANAGE_PERMISSION)::contains));
    }

    @Test
    void areaExportRequiresManagePermission() {
        var request = new MapActionWireProtocol.ExportArea(
                13L,
                WorldId.create(),
                "minecraft:overworld",
                -32, -48, 31, 15,
                "JAVA_1_21_4",
                "permission-proof",
                ExportSettingsWire.Settings.inherit());

        assertNull(MapActionPermissionPolicy.denial(
                request, Set.of(PaperMapActionPayloadAdapter.MANAGE_PERMISSION)::contains));
        assertEquals(
                "Missing permission: " + PaperMapActionPayloadAdapter.MANAGE_PERMISSION,
                MapActionPermissionPolicy.denial(
                        request, Set.of(PaperMapActionPayloadAdapter.TELEPORT_PERMISSION)::contains));
    }
}
