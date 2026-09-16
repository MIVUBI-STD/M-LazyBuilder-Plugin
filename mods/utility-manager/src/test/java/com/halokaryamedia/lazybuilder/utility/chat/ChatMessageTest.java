package com.halokaryamedia.lazybuilder.utility.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class ChatMessageTest {
    @Test
    void fingerprintIsStableForEquivalentSemanticMessages() {
        ChatMessage first = new ChatMessage(ChatMessageType.WARNING, "terraform", "Keybind conflict", "raw-a");
        ChatMessage second = new ChatMessage(ChatMessageType.WARNING, "terraform", "Keybind conflict", "raw-b");

        assertEquals(first.fingerprint(), second.fingerprint());
    }

    @Test
    void fingerprintChangesWhenSemanticContentChanges() {
        ChatMessage first = new ChatMessage(ChatMessageType.WARNING, "terraform", "Keybind conflict", "raw");
        ChatMessage second = new ChatMessage(ChatMessageType.WARNING, "axiom", "Keybind conflict", "raw");

        assertNotEquals(first.fingerprint(), second.fingerprint());
    }
}
