package com.halokaryamedia.lazybuilder.builder.net;

import com.halokaryamedia.lazybuilder.builder.wire.BuilderExtensionTransport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Same bounded envelope as the Paper plugin message transport. */
final class BuilderExtensionPayloadBoundsTest {
    @Test
    void validatesFragmentPayloadLengthsBeforeAllocation() {
        assertDoesNotThrow(() -> BuilderExtensionPayload.requireValidPayloadLength(2));
        assertDoesNotThrow(() -> BuilderExtensionPayload.requireValidPayloadLength(BuilderExtensionTransport.MAX_PLUGIN_MESSAGE_BYTES));
        assertThrows(IllegalArgumentException.class, () -> BuilderExtensionPayload.requireValidPayloadLength(1));
        assertThrows(IllegalArgumentException.class, () -> BuilderExtensionPayload.requireValidPayloadLength(BuilderExtensionTransport.MAX_PLUGIN_MESSAGE_BYTES + 1));
        assertThrows(IllegalArgumentException.class, () -> BuilderExtensionPayload.requireValidPayloadLength(Integer.MAX_VALUE));
    }
}
