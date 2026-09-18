package com.halokaryamedia.lazybuilder.builder.structure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EntityExtensionPayloadContractTest {
    @Test
    void templateLimitIsPublicAndEnforcedAtConstruction() {
        assertEquals(32 * 1024, EntityExtensionPayload.MAX_TEMPLATE_BYTES);
        assertThrows(IllegalArgumentException.class, () ->
                EntityExtensionPayload.present(
                        0.0, 64.0, 0.0,
                        new byte[EntityExtensionPayload.MAX_TEMPLATE_BYTES + 1]
                ));
    }
}
