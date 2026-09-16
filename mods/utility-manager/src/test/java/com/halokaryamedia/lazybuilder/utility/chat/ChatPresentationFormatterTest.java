package com.halokaryamedia.lazybuilder.utility.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class ChatPresentationFormatterTest {
    @Test
    void warningKeepsInternalKeyOutOfNormalChatLine() {
        ChatMessage message = new ChatMessage(
                ChatMessageType.WARNING,
                "Terraform",
                "Keybind conflict",
                "key.lazybuilder.terraform.toggle_panel"
        );

        String line = ChatPresentationFormatter.chatLine(message, 1);

        assertEquals("⚠ Terraform  Keybind conflict", line);
        assertFalse(line.contains("key.lazybuilder"));
    }

    @Test
    void duplicateCountIsCompact() {
        ChatMessage message = new ChatMessage(ChatMessageType.SYSTEM, "Clockwork", "Server ready", "");
        assertEquals("Clockwork  Server ready ×4", ChatPresentationFormatter.chatLine(message, 4));
    }

    @Test
    void consoleRetainsDiagnosticDetail() {
        ChatMessage message = new ChatMessage(
                ChatMessageType.ERROR,
                "Command",
                "Unknown command",
                "/games <--[HERE]"
        );

        assertEquals("! Command  Unknown command | /games <--[HERE]", ChatPresentationFormatter.consoleLine(message));
    }
}
