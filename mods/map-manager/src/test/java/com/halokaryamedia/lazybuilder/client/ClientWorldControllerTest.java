package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void exportFormatRefreshUsesSameInjectedBoundary() {
        List<WorldControlWireProtocol.Request> sent = new ArrayList<>();
        ClientWorldController controller = new ClientWorldController(ignored -> { }, sent::add);

        controller.requestExportFormats();

        assertEquals(1, sent.size());
        assertInstanceOf(WorldControlWireProtocol.GetExportFormats.class, sent.get(0));
    }
}
