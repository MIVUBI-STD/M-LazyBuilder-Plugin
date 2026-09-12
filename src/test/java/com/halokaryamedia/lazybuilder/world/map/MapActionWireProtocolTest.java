package com.halokaryamedia.lazybuilder.world.map;

import com.halokaryamedia.lazybuilder.world.registry.WorldId;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;

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
    void exportAreaRequestRoundTrips() throws Exception {
        WorldId worldId = WorldId.create();
        byte[] payload = MapActionWireProtocol.exportAreaRequest(
                worldId, 31, 48, -17, -1, "JAVA_1_21_4", "area-export");

        var decoded = (MapActionWireProtocol.ExportArea) MapActionWireProtocol.decodeRequest(payload);
        assertEquals(worldId, decoded.worldId());
        assertEquals(31, decoded.x1());
        assertEquals(48, decoded.z1());
        assertEquals(-17, decoded.x2());
        assertEquals(-1, decoded.z2());
        assertEquals("JAVA_1_21_4", decoded.targetFormat());
        assertEquals("area-export", decoded.artifactName());
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
    }
}
