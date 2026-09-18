package com.halokaryamedia.lazybuilder.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientServerIdentityTest {
    @Test
    void serverAddressEncodingIsStableAcrossCaseAndWhitespace() {
        assertEquals(
                ClientServerIdentity.encode("Example.org:25565"),
                ClientServerIdentity.encode("  example.ORG:25565  ")
        );
    }

    @Test
    void blankIdentityUsesSingleplayerScope() {
        assertEquals(
                ClientServerIdentity.encode("singleplayer"),
                ClientServerIdentity.encode("  ")
        );
    }

    @Test
    void sameWorldAndDimensionAreIsolatedAcrossServers() {
        String localScope = "01234567-89ab-cdef-0123-456789abcdef|minecraft:overworld";
        String serverA = ClientServerIdentity.mapScope(ClientServerIdentity.encode("a.example:25565"), localScope);
        String serverB = ClientServerIdentity.mapScope(ClientServerIdentity.encode("b.example:25565"), localScope);

        assertNotEquals(serverA, serverB);
    }

    @Test
    void repeatedIdentityAndScopeReuseMemoizedValues() {
        String encoded = ClientServerIdentity.encodeCached("cache.example:25565");
        assertSame(encoded, ClientServerIdentity.encodeCached("cache.example:25565"));

        String localScope = "01234567-89ab-cdef-0123-456789abcdef|minecraft:overworld";
        String scoped = ClientServerIdentity.mapScope(encoded, localScope);
        assertSame(scoped, ClientServerIdentity.mapScope(encoded, new String(localScope)));
    }

    @Test
    void longServerIdentityIsDeterministicallyBoundedWithoutCollisionsBetweenInputs() {
        String addressA = "a".repeat(260) + ":25565";
        String addressB = "b".repeat(260) + ":25565";
        String encodedA = ClientServerIdentity.encode(addressA);
        String encodedB = ClientServerIdentity.encode(addressB);

        assertTrue(encodedA.length() <= 66);
        assertTrue(encodedA.startsWith("h."));
        assertEquals(encodedA, ClientServerIdentity.encode(addressA));
        assertNotEquals(encodedA, encodedB);
    }
}
