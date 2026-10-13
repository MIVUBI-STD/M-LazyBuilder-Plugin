package com.halokaryamedia.lazybuilder.builder.axiom;

import org.junit.jupiter.api.Test;
import com.halokaryamedia.lazybuilder.builder.wire.BuilderExtensionWireProtocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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
    void separatePaperWorldsCannotShareDurableRecoveryScope() {
        var first = new BuilderExtensionWireProtocol.WorldIdentity(
                "11111111-1111-4111-8111-111111111111", "minecraft:overworld");
        var second = new BuilderExtensionWireProtocol.WorldIdentity(
                "22222222-2222-4222-8222-222222222222", "minecraft:overworld");
        String endpoint = "remote|example.org:25565";
        assertNotEquals(
                AxiomWorldScope.remoteScopeId(endpoint, first, "minecraft:overworld"),
                AxiomWorldScope.remoteScopeId(endpoint, second, "minecraft:overworld"));
    }

    @Test
    void singleplayerSavePathMustBeIdentifiableForDurableHistory() {
        assertEquals("singleplayer|/saves/world-a",
                AxiomWorldScope.singleplayerEndpoint("/saves/world-a"));
        assertNotEquals(AxiomWorldScope.singleplayerEndpoint("/saves/world-a"),
                AxiomWorldScope.singleplayerEndpoint("/saves/world-b"));
        assertThrows(IllegalStateException.class,
                () -> AxiomWorldScope.singleplayerEndpoint(null));
        assertThrows(IllegalStateException.class,
                () -> AxiomWorldScope.singleplayerEndpoint(" "));
    }

    @Test
    void rejectsUnavailableRemoteServerIdentity() {
        assertThrows(IllegalStateException.class,
                () -> AxiomWorldScope.remoteEndpoint(null, null));
        assertThrows(IllegalStateException.class,
                () -> AxiomWorldScope.remoteEndpoint(" ", ""));
    }
}
