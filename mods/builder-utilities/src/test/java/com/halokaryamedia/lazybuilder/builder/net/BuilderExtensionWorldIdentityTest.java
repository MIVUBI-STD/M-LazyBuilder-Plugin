package com.halokaryamedia.lazybuilder.builder.net;

import com.halokaryamedia.lazybuilder.builder.wire.BuilderExtensionWireProtocol;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BuilderExtensionWorldIdentityTest {
    private static final BuilderExtensionWireProtocol.WorldIdentity WORLD_A =
            new BuilderExtensionWireProtocol.WorldIdentity(
                    "11111111-1111-4111-8111-111111111111", "minecraft:overworld");
    private static final BuilderExtensionWireProtocol.WorldIdentity WORLD_B =
            new BuilderExtensionWireProtocol.WorldIdentity(
                    "22222222-2222-4222-8222-222222222222", "minecraft:overworld");

    @Test
    void conflictingWorldSnapshotsRequireRenegotiationInsteadOfGuessingOrder() {
        assertFalse(BuilderExtensionClientNetworking.handshakeAgreesWithWorldChange(
                WORLD_A, WORLD_B));
        assertFalse(BuilderExtensionClientNetworking.handshakeAgreesWithWorldChange(
                WORLD_B, WORLD_A));
    }

    @Test
    void matchingOrAbsentWorldChangePermitsTheHandshake() {
        assertTrue(BuilderExtensionClientNetworking.handshakeAgreesWithWorldChange(
                WORLD_A, WORLD_A));
        assertTrue(BuilderExtensionClientNetworking.handshakeAgreesWithWorldChange(
                WORLD_A, null));
        assertThrows(NullPointerException.class,
                () -> BuilderExtensionClientNetworking.handshakeAgreesWithWorldChange(null, null));
    }
}
