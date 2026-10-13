package com.halokaryamedia.lazybuilder.builder.axiom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AxiomWorldScopeRemoteEndpointTest {
    @Test
    void usesStableIdentifiedRemoteServerAddress() {
        assertEquals("remote|example.org:25565",
                AxiomWorldScope.remoteEndpoint("EXAMPLE.ORG:25565", null));
        assertEquals("remote|127.0.0.1:25565",
                AxiomWorldScope.remoteEndpoint("", "127.0.0.1:25565"));
        assertEquals("remote|example.org",
                AxiomWorldScope.remoteEndpoint("  EXAMPLE.ORG  ", null));
    }

    @Test
    void rejectsUnavailableRemoteServerIdentity() {
        assertThrows(IllegalStateException.class,
                () -> AxiomWorldScope.remoteEndpoint(null, null));
        assertThrows(IllegalStateException.class,
                () -> AxiomWorldScope.remoteEndpoint(" ", ""));
    }
}
