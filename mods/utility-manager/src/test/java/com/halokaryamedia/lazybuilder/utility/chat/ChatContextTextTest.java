package com.halokaryamedia.lazybuilder.utility.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChatContextTextTest {
    @Test
    void vanillaPlayerChatExposesSenderWithoutTimestamp() {
        ChatContextText.Parsed parsed = ChatContextText.parse("02:40  <Marcel> hello there");

        assertEquals("<Marcel> hello there", parsed.fullText());
        assertEquals("Marcel", parsed.sender());
        assertEquals("hello there", parsed.body());
        assertTrue(parsed.hasSender());
    }

    @Test
    void systemTextDoesNotInventSender() {
        ChatContextText.Parsed parsed = ChatContextText.parse("02:40  Berchman joined ×3");

        assertEquals("Berchman joined ×3", parsed.fullText());
        assertEquals("", parsed.sender());
        assertEquals("Berchman joined ×3", parsed.body());
        assertFalse(parsed.hasSender());
    }

    @Test
    void externalTimestampStyleIsAlsoRemoved() {
        ChatContextText.Parsed parsed = ChatContextText.parse("[02:40:31] <Builder> copied block");

        assertEquals("<Builder> copied block", parsed.fullText());
        assertEquals("Builder", parsed.sender());
    }
}
