package com.halokaryamedia.lazybuilder.builder.wire;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BuilderExtensionWireProtocolEntitySizeTest {
    @Test
    void entityTemplateLimitUsesUtf8BytesNotCharacterCount() {
        String multiByte = "界".repeat(11_000);
        assertTrue(multiByte.length() < 32_768);
        assertTrue(multiByte.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 32_768);

        assertThrows(IllegalArgumentException.class, () ->
                new BuilderExtensionWireProtocol.EntityMutation(
                        "marker",
                        0.0, 64.0, 0.0,
                        0.0f, 0.0f,
                        false, true,
                        multiByte
                ));
    }
}
