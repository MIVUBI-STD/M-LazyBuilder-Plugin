package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ClientWorldControllerTest {
    @Test
    void refreshUsesInjectedRequestSenderWithoutDiscardingKnownWorlds() {
        List<WorldControlWireProtocol.Request> sent = new ArrayList<>();
        ClientWorldController controller = new ClientWorldController(ignored -> { }, sent::add);

        controller.accept(new WorldControlWireProtocol.WorldList(List.of(), true, true));
        assertTrue(controller.worldListReady());

        controller.refresh();

        assertEquals(1, sent.size());
        assertInstanceOf(WorldControlWireProtocol.ListWorlds.class, sent.get(0));
        assertTrue(controller.worldListReady());
        assertTrue(controller.worldListPending());
        controller.refresh();
        assertEquals(1, sent.size(), "a second view may not duplicate an outstanding world-list request");
        controller.accept(new WorldControlWireProtocol.WorldList(List.of(), true, true));
        controller.refresh();
        assertEquals(2, sent.size(), "a completed request permits the next explicit refresh");
    }

    @Test
    void importCompletesOnlyForMatchingAuthoritativeWorldResult() {
        List<WorldControlWireProtocol.Request> sent = new ArrayList<>();
        ClientWorldController controller = new ClientWorldController(ignored -> { }, sent::add);
        controller.importWorld("upload.zip", "target", "Target");
        assertEquals(1, sent.size());
        assertTrue(controller.importPending());
        assertThrows(IllegalStateException.class,
                () -> controller.importWorld("upload.zip", "target", "Target"));
        assertEquals(1, sent.size(), "duplicate submission must be rejected before dispatch");

        var unrelated = new WorldControlWireProtocol.WorldSummary(
                UUID.randomUUID(), "unrelated", "Unrelated", "IMPORTED", "ACTIVE", "CREATIVE");
        controller.accept(new WorldControlWireProtocol.WorldChanged("UPDATE", unrelated));
        assertTrue(controller.importPending());
        assertFalse(controller.importCompletedFor("target"));
        assertEquals("Validating and importing world…", controller.activityMessage());
        controller.accept(new WorldControlWireProtocol.WorldChanged("IMPORT", unrelated));
        assertTrue(controller.importPending(), "another import result cannot acknowledge this one");
        assertFalse(controller.importCompletedFor("target"));

        var target = new WorldControlWireProtocol.WorldSummary(
                UUID.randomUUID(), "target", "Target", "IMPORTED", "ACTIVE", "CREATIVE");
        controller.accept(new WorldControlWireProtocol.WorldChanged("IMPORT", target));
        assertFalse(controller.importPending());
        assertTrue(controller.importCompletedFor("target"));
        assertFalse(controller.importCompletedFor("unrelated"));
    }

    @Test
    void failedOrDisconnectedImportDoesNotRetainSuccess() {
        ClientWorldController controller = new ClientWorldController(ignored -> { }, request -> { });
        controller.importWorld("upload.zip", "target", "Target");
        controller.accept(new WorldControlWireProtocol.ErrorResponse("Import failed"));
        assertFalse(controller.importPending());
        assertFalse(controller.importCompletedFor("target"));
        controller.importWorld("upload.zip", "target", "Target");
        controller.reset();
        assertFalse(controller.importPending());
        assertFalse(controller.importCompletedFor("target"));
    }

    @Test
    void exportFormatRefreshUsesSameInjectedBoundary() {
        List<WorldControlWireProtocol.Request> sent = new ArrayList<>();
        ClientWorldController controller = new ClientWorldController(ignored -> { }, sent::add);

        controller.requestExportFormats();

        assertEquals(1, sent.size());
        assertInstanceOf(WorldControlWireProtocol.GetExportFormats.class, sent.get(0));
    }
}
