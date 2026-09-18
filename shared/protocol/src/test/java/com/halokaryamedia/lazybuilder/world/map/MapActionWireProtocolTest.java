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
    void teleportRequestRoundTripsWithCorrelationId() throws Exception {
        WorldId worldId = WorldId.create();
        byte[] payload = MapActionWireProtocol.teleportRequest(41L, worldId, -12, 345);

        var decoded = (MapActionWireProtocol.TeleportLocation) MapActionWireProtocol.decodeRequest(payload);
        assertEquals(41L, decoded.requestId());
        assertEquals(worldId, decoded.worldId());
        assertEquals(-12, decoded.blockX());
        assertEquals(345, decoded.blockZ());
    }

    @Test
    void exportAreaRequestRoundTripsWithDimensionAndCorrelationId() throws Exception {
        WorldId worldId = WorldId.create();
        byte[] payload = MapActionWireProtocol.exportAreaRequest(
                42L, worldId, "minecraft:the_nether", 31, 48, -17, -1,
                "JAVA_1_21_4", "area-export", ExportSettingsWire.Settings.inherit());

        var decoded = (MapActionWireProtocol.ExportArea) MapActionWireProtocol.decodeRequest(payload);
        assertEquals(42L, decoded.requestId());
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
                43L, worldId, "minecraft:the_end", 0, 0, 15, 15,
                "BEDROCK_1_21_80", "custom-area", settings);

        var decoded = (MapActionWireProtocol.ExportArea) MapActionWireProtocol.decodeRequest(payload);
        assertEquals(43L, decoded.requestId());
        assertEquals("minecraft:the_end", decoded.dimensionId());
        assertEquals(settings, decoded.settings());
    }

    @Test
    void currentWorldHandshakeRoundTripsCorrelationForExplicitRefresh() throws Exception {
        var request = (MapActionWireProtocol.CurrentWorldRequest) MapActionWireProtocol.decodeRequest(
                MapActionWireProtocol.currentWorldRequest(44L));
        assertEquals(44L, request.requestId());

        WorldId id = WorldId.create();
        var response = (MapActionWireProtocol.CurrentWorldResult) MapActionWireProtocol.decodeResponse(
                MapActionWireProtocol.currentWorld(44L, id, "Build World", "build-world"));
        assertEquals(44L, response.requestId());
        assertEquals(id, response.worldId());
        assertEquals("Build World", response.displayName());
        assertEquals("build-world", response.folderName());

        var cleared = (MapActionWireProtocol.CurrentWorldCleared) MapActionWireProtocol.decodeResponse(
                MapActionWireProtocol.currentWorldCleared(44L));
        assertEquals(44L, cleared.requestId());
    }

    @Test
    void requestBoundResponsesRoundTripCorrelationId() throws Exception {
        WorldId id = WorldId.create();
        var teleport = (MapActionWireProtocol.TeleportOk) MapActionWireProtocol.decodeResponse(
                MapActionWireProtocol.teleportOk(51L, id, 10.5, 65.0, -3.5));
        assertEquals(51L, teleport.requestId());
        assertEquals(id, teleport.worldId());
        assertEquals(65.0, teleport.y());

        var accepted = (MapActionWireProtocol.ExportAccepted) MapActionWireProtocol.decodeResponse(
                MapActionWireProtocol.exportAccepted(52L, id));
        assertEquals(52L, accepted.requestId());

        var complete = (MapActionWireProtocol.ExportComplete) MapActionWireProtocol.decodeResponse(
                MapActionWireProtocol.exportComplete(52L, id, "area.zip", "JAVA_1_21_4"));
        assertEquals(52L, complete.requestId());
        assertEquals("area.zip", complete.fileName());

        var error = (MapActionWireProtocol.ErrorResponse) MapActionWireProtocol.decodeResponse(
                MapActionWireProtocol.error(53L, "denied"));
        assertEquals(53L, error.requestId());
        assertEquals("denied", error.message());
    }

    @Test
    void unsolicitedCurrentWorldPushUsesZeroCorrelationId() throws Exception {
        WorldId id = WorldId.create();
        var response = (MapActionWireProtocol.CurrentWorldResult) MapActionWireProtocol.decodeResponse(
                MapActionWireProtocol.currentWorld(id, "Build World", "build-world"));
        assertEquals(0L, response.requestId());
    }

    @Test
    void rejectsNonPositiveRequestCorrelationIds() {
        WorldId worldId = WorldId.create();
        assertThrows(IllegalArgumentException.class,
                () -> MapActionWireProtocol.teleportRequest(0L, worldId, 1, 2));
        assertThrows(IllegalArgumentException.class,
                () -> MapActionWireProtocol.currentWorldRequest(-1L));

        byte[] valid = MapActionWireProtocol.teleportRequest(61L, worldId, 1, 2);
        byte[] zeroId = valid.clone();
        Arrays.fill(zeroId, 2, 10, (byte) 0);
        assertThrows(IOException.class, () -> MapActionWireProtocol.decodeRequest(zeroId));
    }

    @Test
    void rejectsZeroCorrelationForRequestBoundResponses() {
        WorldId worldId = WorldId.create();

        byte[] teleport = MapActionWireProtocol.teleportOk(71L, worldId, 1.0, 64.0, 2.0);
        byte[] zeroTeleport = teleport.clone();
        Arrays.fill(zeroTeleport, 2, 10, (byte) 0);
        assertThrows(IOException.class, () -> MapActionWireProtocol.decodeResponse(zeroTeleport));

        byte[] accepted = MapActionWireProtocol.exportAccepted(72L, worldId);
        byte[] zeroAccepted = accepted.clone();
        Arrays.fill(zeroAccepted, 2, 10, (byte) 0);
        assertThrows(IOException.class, () -> MapActionWireProtocol.decodeResponse(zeroAccepted));

        byte[] complete = MapActionWireProtocol.exportComplete(
                73L, worldId, "area.zip", "JAVA_1_21_4");
        byte[] zeroComplete = complete.clone();
        Arrays.fill(zeroComplete, 2, 10, (byte) 0);
        assertThrows(IOException.class, () -> MapActionWireProtocol.decodeResponse(zeroComplete));
    }

    @Test
    void rejectsNegativeResponseCorrelationIds() {
        WorldId worldId = WorldId.create();
        byte[] response = MapActionWireProtocol.currentWorld(
                74L, worldId, "Build World", "build-world");
        byte[] negative = response.clone();
        Arrays.fill(negative, 2, 10, (byte) 0xFF);

        assertThrows(IOException.class, () -> MapActionWireProtocol.decodeResponse(negative));
    }

    @Test
    void rejectsOversizedMapPayloadsAndStrings() {
        assertThrows(
                IOException.class,
                () -> MapActionWireProtocol.decodeResponse(
                        new byte[MapActionWireProtocol.MAX_MESSAGE_BYTES + 1]));

        String oversized = "x".repeat(193);
        assertThrows(
                IllegalArgumentException.class,
                () -> MapActionWireProtocol.error(75L, oversized));
    }

    @Test
    void rejectsUnsupportedVersionAndTrailingBytes() {
        WorldId worldId = WorldId.create();
        byte[] payload = MapActionWireProtocol.teleportRequest(61L, worldId, 1, 2);

        byte[] badVersion = payload.clone();
        badVersion[0] = 99;
        assertThrows(IOException.class, () -> MapActionWireProtocol.decodeRequest(badVersion));

        byte[] trailing = Arrays.copyOf(payload, payload.length + 1);
        trailing[trailing.length - 1] = 7;
        assertThrows(IOException.class, () -> MapActionWireProtocol.decodeRequest(trailing));

        byte[] response = MapActionWireProtocol.exportAccepted(61L, worldId);
        byte[] responseTrailing = Arrays.copyOf(response, response.length + 1);
        assertThrows(IOException.class, () -> MapActionWireProtocol.decodeResponse(responseTrailing));
    }
}
