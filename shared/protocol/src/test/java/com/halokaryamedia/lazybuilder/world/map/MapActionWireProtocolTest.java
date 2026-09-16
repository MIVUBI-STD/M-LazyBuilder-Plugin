package com.halokaryamedia.lazybuilder.world.map;

import com.halokaryamedia.lazybuilder.world.export.ExportSettingsWire;
import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MapActionWireProtocolTest {
    @Test
    void teleportRequestRoundTrips() throws Exception {
        WorldId worldId = WorldId.create();
        byte[] payload = MapActionWireProtocol.teleportRequest(worldId, -12, 345);

        var decoded = (MapActionWireProtocol.TeleportLocation) MapActionWireProtocol.decodeRequest(payload);
        assertEquals(worldId, decoded.worldId());
        assertEquals(-12, decoded.blockX());
        assertEquals(345, decoded.blockZ());
    }

    @Test
    void exportAreaRequestRoundTripsWithDimension() throws Exception {
        WorldId worldId = WorldId.create();
        byte[] payload = MapActionWireProtocol.exportAreaRequest(
                worldId, "minecraft:the_nether", 31, 48, -17, -1,
                "JAVA_1_21_4", "area-export");

        var decoded = (MapActionWireProtocol.ExportArea) MapActionWireProtocol.decodeRequest(payload);
        assertEquals(worldId, decoded.worldId());
        assertEquals("minecraft:the_nether", decoded.dimensionId());
        assertEquals(31, decoded.x1());
        assertEquals(48, decoded.z1());
        assertEquals(-17, decoded.x2());
        assertEquals(-1, decoded.z2());
        assertEquals("JAVA_1_21_4", decoded.targetFormat());
        assertEquals("area-export", decoded.artifactName());
        assertEquals(ExportSettingsWire.Settings.inherit(), decoded.settings());
    }

    @Test
    void customizedExportAreaRequestCarriesSharedSettings() throws Exception {
        WorldId worldId = WorldId.create();
        var settings = new ExportSettingsWire.Settings(
                "ADVENTURE", "HARD", Map.of("keepinventory", "true"));
        byte[] payload = MapActionWireProtocol.exportAreaRequest(
                worldId, "minecraft:the_end", 0, 0, 15, 15,
                "BEDROCK_1_21_80", "custom-area", settings);

        var decoded = (MapActionWireProtocol.ExportArea) MapActionWireProtocol.decodeRequest(payload);
        assertEquals("minecraft:the_end", decoded.dimensionId());
        assertEquals(settings, decoded.settings());
    }

    @Test
    void currentWorldHandshakeRoundTripsForClient() throws Exception {
        var request = MapActionWireProtocol.decodeRequest(MapActionWireProtocol.currentWorldRequest());
        assertEquals(MapActionWireProtocol.CurrentWorldRequest.class, request.getClass());

        WorldId id = WorldId.create();
        var response = (MapActionWireProtocol.CurrentWorldResult) MapActionWireProtocol.decodeResponse(
                MapActionWireProtocol.currentWorld(id, "Build World", "build-world"));
        assertEquals(id, response.worldId());
        assertEquals("Build World", response.displayName());
        assertEquals("build-world", response.folderName());

        var cleared = MapActionWireProtocol.decodeResponse(MapActionWireProtocol.currentWorldCleared());
        assertEquals(MapActionWireProtocol.CurrentWorldCleared.class, cleared.getClass());
    }

    @Test
    void responseRoundTrips() throws Exception {
        WorldId id = WorldId.create();
        var teleport = (MapActionWireProtocol.TeleportOk) MapActionWireProtocol.decodeResponse(
                MapActionWireProtocol.teleportOk(id, 10.5, 65.0, -3.5));
        assertEquals(id, teleport.worldId());
        assertEquals(65.0, teleport.y());

        var complete = (MapActionWireProtocol.ExportComplete) MapActionWireProtocol.decodeResponse(
                MapActionWireProtocol.exportComplete(id, "area.zip", "JAVA_1_21_4"));
        assertEquals("area.zip", complete.fileName());
    }

    @Test
    void rejectsUnsupportedVersionAndTrailingBytes() {
        WorldId worldId = WorldId.create();
        byte[] payload = MapActionWireProtocol.teleportRequest(worldId, 1, 2);

        byte[] badVersion = payload.clone();
        badVersion[0] = 99;
        assertThrows(IOException.class, () -> MapActionWireProtocol.decodeRequest(badVersion));

        byte[] trailing = Arrays.copyOf(payload, payload.length + 1);
        trailing[trailing.length - 1] = 7;
        assertThrows(IOException.class, () -> MapActionWireProtocol.decodeRequest(trailing));

        byte[] response = MapActionWireProtocol.exportAccepted(worldId);
        byte[] responseTrailing = Arrays.copyOf(response, response.length + 1);
        assertThrows(IOException.class, () -> MapActionWireProtocol.decodeResponse(responseTrailing));
    }
}
