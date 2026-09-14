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
        assertEquals(new WorldControlWireProtocol.GetExportFormats(), roundTrip(new WorldControlWireProtocol.GetExportFormats()));
        assertEquals(new WorldControlWireProtocol.InspectImport("incoming.zip"),
                roundTrip(new WorldControlWireProtocol.InspectImport("incoming.zip")));
        assertEquals(new WorldControlWireProtocol.CreateWorld("build", "Build", "FLAT"),
                roundTrip(new WorldControlWireProtocol.CreateWorld("build", "Build", "FLAT")));
        assertEquals(new WorldControlWireProtocol.TeleportWorld(id), roundTrip(new WorldControlWireProtocol.TeleportWorld(id)));
        assertEquals(new WorldControlWireProtocol.ArchiveWorld(id), roundTrip(new WorldControlWireProtocol.ArchiveWorld(id)));
        assertEquals(new WorldControlWireProtocol.RestoreWorld(id), roundTrip(new WorldControlWireProtocol.RestoreWorld(id)));
        assertEquals(new WorldControlWireProtocol.DuplicateWorld(id, "build_copy", "Build Copy"),
                roundTrip(new WorldControlWireProtocol.DuplicateWorld(id, "build_copy", "Build Copy")));
        assertEquals(new WorldControlWireProtocol.DeleteWorld(id, "Build"),
                roundTrip(new WorldControlWireProtocol.DeleteWorld(id, "Build")));
        assertEquals(new WorldControlWireProtocol.GetSettings(id), roundTrip(new WorldControlWireProtocol.GetSettings(id)));
        assertEquals(new WorldControlWireProtocol.SetDefaultMode(id, "ADVENTURE"),
                roundTrip(new WorldControlWireProtocol.SetDefaultMode(id, "ADVENTURE")));
        assertEquals(new WorldControlWireProtocol.SetDifficulty(id, "HARD"),
                roundTrip(new WorldControlWireProtocol.SetDifficulty(id, "HARD")));
        assertEquals(new WorldControlWireProtocol.SetPvp(id, true), roundTrip(new WorldControlWireProtocol.SetPvp(id, true)));
        assertEquals(new WorldControlWireProtocol.ResetBuildReady(id), roundTrip(new WorldControlWireProtocol.ResetBuildReady(id)));
        assertEquals(new WorldControlWireProtocol.SetSpawnHere(id), roundTrip(new WorldControlWireProtocol.SetSpawnHere(id)));
        assertEquals(new WorldControlWireProtocol.ExportWorld(id, "JAVA_1_21_4", "build-export"),
                roundTrip(new WorldControlWireProtocol.ExportWorld(id, "JAVA_1_21_4", "build-export")));
        assertEquals(new WorldControlWireProtocol.ImportWorld("incoming.zip", "incoming", "Incoming"),
                roundTrip(new WorldControlWireProtocol.ImportWorld("incoming.zip", "incoming", "Incoming")));
    }

    @Test
    void responsesRoundTrip() throws Exception {
        UUID id = UUID.randomUUID();
        var world = new WorldControlWireProtocol.WorldSummary(
                id, "build", "Build", "FLAT", "ACTIVE", "CREATIVE");
        var list = new WorldControlWireProtocol.WorldList(List.of(world), true, false);
        assertEquals(list, WorldControlWireProtocol.decodeResponse(WorldControlWireProtocol.encodeResponse(list)));

        var changed = new WorldControlWireProtocol.WorldChanged("DUPLICATE", world);
        assertEquals(changed, WorldControlWireProtocol.decodeResponse(WorldControlWireProtocol.encodeResponse(changed)));

        var teleported = new WorldControlWireProtocol.TeleportOk(world);
        assertEquals(teleported, WorldControlWireProtocol.decodeResponse(WorldControlWireProtocol.encodeResponse(teleported)));

        var settings = new WorldControlWireProtocol.SettingsSnapshot(
                id, "CREATIVE", "NORMAL", false, "CLEAR", 6000L, 0.0, 65.0, 0.0);
        assertEquals(settings, WorldControlWireProtocol.decodeResponse(WorldControlWireProtocol.encodeResponse(settings)));

        var export = new WorldControlWireProtocol.ExportReady(id, "build-export.zip", "JAVA_1_21_4");
        assertEquals(export, WorldControlWireProtocol.decodeResponse(WorldControlWireProtocol.encodeResponse(export)));

        var formats = new WorldControlWireProtocol.ExportFormats(List.of("JAVA_1_21_4", "BEDROCK_1_21_0"));
        assertEquals(formats, WorldControlWireProtocol.decodeResponse(WorldControlWireProtocol.encodeResponse(formats)));

        var inspection = new WorldControlWireProtocol.ImportInspection(
                "incoming.zip", "JAVA", "1.21.4", "Incoming");
        assertEquals(inspection,
                WorldControlWireProtocol.decodeResponse(WorldControlWireProtocol.encodeResponse(inspection)));
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
