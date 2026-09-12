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
        assertEquals(new WorldControlWireProtocol.CloneWorld(id, "build_copy", "Build Copy"),
                roundTrip(new WorldControlWireProtocol.CloneWorld(id, "build_copy", "Build Copy")));
        assertEquals(new WorldControlWireProtocol.DeleteWorld(id, "build"),
                roundTrip(new WorldControlWireProtocol.DeleteWorld(id, "build")));
        assertEquals(new WorldControlWireProtocol.GetSettings(id), roundTrip(new WorldControlWireProtocol.GetSettings(id)));
        assertEquals(new WorldControlWireProtocol.SetAutoLoad(id, false), roundTrip(new WorldControlWireProtocol.SetAutoLoad(id, false)));
        assertEquals(new WorldControlWireProtocol.SetDefaultMode(id, "ADVENTURE"), roundTrip(new WorldControlWireProtocol.SetDefaultMode(id, "ADVENTURE")));
        assertEquals(new WorldControlWireProtocol.SetDifficulty(id, "HARD"), roundTrip(new WorldControlWireProtocol.SetDifficulty(id, "HARD")));
        assertEquals(new WorldControlWireProtocol.SetPvp(id, true), roundTrip(new WorldControlWireProtocol.SetPvp(id, true)));
        assertEquals(new WorldControlWireProtocol.ResetBuildReady(id), roundTrip(new WorldControlWireProtocol.ResetBuildReady(id)));
        assertEquals(new WorldControlWireProtocol.SetSpawnHere(id), roundTrip(new WorldControlWireProtocol.SetSpawnHere(id)));
    }

    @Test
    void responsesRoundTrip() throws Exception {
        UUID id = UUID.randomUUID();
        var world = new WorldControlWireProtocol.WorldSummary(
                id, "build", "Build", "FLAT", "ACTIVE", "LOADED", true, "CREATIVE");
        var list = new WorldControlWireProtocol.WorldList(List.of(world));
        assertEquals(list, WorldControlWireProtocol.decodeResponse(WorldControlWireProtocol.encodeResponse(list)));

        var changed = new WorldControlWireProtocol.WorldChanged("LOAD", world);
        assertEquals(changed, WorldControlWireProtocol.decodeResponse(WorldControlWireProtocol.encodeResponse(changed)));

        var teleported = new WorldControlWireProtocol.TeleportOk(world);
        assertEquals(teleported, WorldControlWireProtocol.decodeResponse(WorldControlWireProtocol.encodeResponse(teleported)));

        var settings = new WorldControlWireProtocol.SettingsSnapshot(
                id, true, "CREATIVE", "NORMAL", false, "CLEAR", 6000L, 0.0, 65.0, 0.0);
        assertEquals(settings, WorldControlWireProtocol.decodeResponse(WorldControlWireProtocol.encodeResponse(settings)));
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
