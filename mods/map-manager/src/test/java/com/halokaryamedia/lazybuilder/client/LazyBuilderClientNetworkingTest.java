package com.halokaryamedia.lazybuilder.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LazyBuilderClientNetworkingTest {
    @Test
    void payloadFromPreviousConnectionDoesNotMatchCurrentHandler() {
        Object oldConnection = new Object();
        Object activeConnection = new Object();

        assertTrue(LazyBuilderClientNetworking.sameConnection(activeConnection, activeConnection));
        assertFalse(LazyBuilderClientNetworking.sameConnection(oldConnection, activeConnection));
        assertFalse(LazyBuilderClientNetworking.sameConnection(null, activeConnection));
        assertFalse(LazyBuilderClientNetworking.sameConnection(oldConnection, null));
    }

    @Test
    void delayedDisconnectCannotResetNewSessionButCanClearClosedSession() {
        Object disconnected = new Object();
        Object nextConnection = new Object();

        assertFalse(LazyBuilderClientNetworking.mayResetDisconnectedConnection(disconnected, nextConnection));
        assertTrue(LazyBuilderClientNetworking.mayResetDisconnectedConnection(disconnected, null));
        assertTrue(LazyBuilderClientNetworking.mayResetDisconnectedConnection(disconnected, disconnected));
    }
}
