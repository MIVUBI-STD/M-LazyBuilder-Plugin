package com.halokaryamedia.lazybuilder.builder.structure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EntityExtensionPayloadTest {
    @Test
    void absenceAndPresenceRoundTrip() throws Exception {
        EntityExtensionPayload absent = EntityExtensionPayload.absent(1.25, 64.0, -2.5);
        EntityExtensionPayload present = EntityExtensionPayload.present(
                1.25, 64.0, -2.5, new byte[]{1, 2, 3});

        assertEquals(absent, EntityExtensionPayload.decode(absent.encode()));
        EntityExtensionPayload decoded = EntityExtensionPayload.decode(present.encode());
        assertTrue(decoded.present());
        assertArrayEquals(new byte[]{1, 2, 3}, decoded.templateNbt());
        assertTrue(absent.sameSlot(decoded));
    }
}
