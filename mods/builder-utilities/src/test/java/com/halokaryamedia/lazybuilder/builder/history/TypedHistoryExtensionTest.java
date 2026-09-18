package com.halokaryamedia.lazybuilder.builder.history;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class TypedHistoryExtensionTest {
    private static final HistoryExtensionType<String> STRING_TYPE = new HistoryExtensionType<>(
            HistoryExtensionTypes.BLOCK_ENTITY,
            new HistoryExtensionCodec<>() {
                @Override
                public byte[] encode(String value) {
                    return value.getBytes(StandardCharsets.UTF_8);
                }

                @Override
                public String decode(byte[] payload) {
                    return new String(payload, StandardCharsets.UTF_8);
                }
            }
    );

    @Test
    void typedExtensionRoundTripsThroughOpaqueFrame() throws Exception {
        TypedHistoryExtension<String> typed = new TypedHistoryExtension<>(
                STRING_TYPE, -2, 3, 77L, "before", "after");
        HistoryExtensionFrame frame = typed.toFrame();
        TypedHistoryExtension<String> decoded =
                TypedHistoryExtension.fromFrame(STRING_TYPE, frame);

        assertEquals("before", decoded.before());
        assertEquals("after", decoded.after());
        assertEquals(HistoryExtensionTypes.BLOCK_ENTITY, frame.typeId());
    }

    @Test
    void registryRejectsDuplicateIds() {
        HistoryExtensionRegistry.Builder builder = HistoryExtensionRegistry.builder()
                .register(STRING_TYPE);
        assertThrows(IllegalArgumentException.class, () -> builder.register(STRING_TYPE));
    }
}
