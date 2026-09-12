package com.halokaryamedia.lazybuilder.world.control;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorldControlWireProtocolTest {
    @Test
    void requestsRoundTrip() throws Exception {
        UUID id = UUID.randomUUID();
        assertEquals(new WorldControlWireProtocol.ListWorlds(), roundTrip(new WorldControlWireProtocol.ListWorlds()));
        assertEquals(new WorldControlWireProtocol.CreateWorld("build", "Build", "FLAT"),
                roundTrip(new WorldControlWireProtocol.CreateWorld("build", "Build", "FLAT")));
        assertEquals(new WorldControlWireProtocol.LoadWorld(id), roundTrip(new WorldControlWireProtocol.LoadWorld(id)));
        assertEquals(new WorldControlWireProtocol.UnloadWorld(id), roundTrip(new WorldControlWireProtocol.UnloadWorld(id)));
        assertEquals(new WorldControlWireProtocol.TeleportWorld(id), roundTrip(new WorldControlWireProtocol.TeleportWorld(id)));
        assertEquals(new WorldControlWireProtocol.ArchiveWorld(id), roundTrip(new WorldControlWireProtocol.ArchiveWorld(id)));
        assertEquals(new WorldControlWireProtocol.RestoreWorld(id), roundTrip(new WorldControlWireProtocol.RestoreWorld(id)));
    }

    @Test
    void responsesRoundTrip() throws Exception {
        var world = new WorldControlWireProtocol.WorldSummary(
                UUID.randomUUID(), "build", "Build", "FLAT", "ACTIVE", "LOADED", true, "CREATIVE");
        var list = new WorldControlWireProtocol.WorldList(List.of(world));
        assertEquals(list, WorldControlWireProtocol.decodeResponse(WorldControlWireProtocol.encodeResponse(list)));

        var changed = new WorldControlWireProtocol.WorldChanged("LOAD", world);
        assertEquals(changed, WorldControlWireProtocol.decodeResponse(WorldControlWireProtocol.encodeResponse(changed)));

        var teleported = new WorldControlWireProtocol.TeleportOk(world);
        assertEquals(teleported, WorldControlWireProtocol.decodeResponse(WorldControlWireProtocol.encodeResponse(teleported)));
    }

    @Test
    void rejectsUnknownVersionAndTrailingBytes() throws Exception {
        byte[] request = WorldControlWireProtocol.encodeRequest(new WorldControlWireProtocol.ListWorlds());
        byte[] wrongVersion = request.clone();
        wrongVersion[0] = 99;
        assertThrows(IOException.class, () -> WorldControlWireProtocol.decodeRequest(wrongVersion));

        byte[] response = WorldControlWireProtocol.encodeResponse(new WorldControlWireProtocol.ErrorResponse("x"));
        byte[] trailing = Arrays.copyOf(response, response.length + 1);
        assertThrows(IOException.class, () -> WorldControlWireProtocol.decodeResponse(trailing));
    }

    private static WorldControlWireProtocol.Request roundTrip(WorldControlWireProtocol.Request request) throws Exception {
        return WorldControlWireProtocol.decodeRequest(WorldControlWireProtocol.encodeRequest(request));
    }
}
