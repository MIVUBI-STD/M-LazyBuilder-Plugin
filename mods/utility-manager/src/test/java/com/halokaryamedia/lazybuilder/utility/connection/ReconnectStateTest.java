package com.halokaryamedia.lazybuilder.utility.connection;

import net.minecraft.client.network.ServerInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ReconnectStateTest {
    @AfterEach
    void clearState() {
        ReconnectState.clear();
    }

    @Test
    void validTargetIsSessionAvailable() {
        ReconnectState.capture(new ServerInfo(
                "Utility Test",
                "example.test:25565",
                ServerInfo.ServerType.OTHER
        ));

        assertTrue(ReconnectState.canReconnect());
        assertEquals("example.test:25565", ReconnectState.serverAddress());
    }

    @Test
    void unavailableCurrentTargetClearsPreviousSessionTarget() {
        ReconnectState.capture(new ServerInfo(
                "Utility Test",
                "example.test:25565",
                ServerInfo.ServerType.OTHER
        ));

        ReconnectState.capture(null);

        assertFalse(ReconnectState.canReconnect());
        assertEquals("", ReconnectState.serverAddress());
    }

    @Test
    void explicitClearRemovesTarget() {
        ReconnectState.capture(new ServerInfo(
                "Utility Test",
                "example.test:25565",
                ServerInfo.ServerType.OTHER
        ));

        ReconnectState.clear();

        assertFalse(ReconnectState.canReconnect());
    }
}
