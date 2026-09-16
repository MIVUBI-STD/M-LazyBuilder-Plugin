package com.halokaryamedia.lazybuilder.utility.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class MinecraftMessageClassifierTest {
    @Test
    void playerChatIsExplicitAndNeverGuessed() {
        ChatMessage message = MinecraftMessageClassifier.playerChat("Marcel", "hello");

        assertEquals(ChatMessageType.CHAT, message.type());
        assertEquals("Marcel", message.source());
        assertEquals("hello", message.text());
    }

    @Test
    void joinAndLeaveMessagesBecomeCompactGameEvents() {
        ChatMessage joined = MinecraftMessageClassifier.systemMessage("Berchman joined the game");
        ChatMessage left = MinecraftMessageClassifier.systemMessage("Berchman left the game");

        assertEquals(ChatMessageType.GAME, joined.type());
        assertEquals("Berchman joined", joined.text());
        assertEquals(ChatMessageType.GAME, left.type());
        assertEquals("Berchman left", left.text());
    }

    @Test
    void commandFailureBecomesHumanReadableErrorWithoutDroppingRawDetail() {
        String raw = "Unknown or incomplete command, see below for error";
        ChatMessage message = MinecraftMessageClassifier.systemMessage(raw);

        assertEquals(ChatMessageType.ERROR, message.type());
        assertEquals("Command", message.source());
        assertEquals("Unknown command", message.text());
        assertEquals(raw, message.diagnosticDetail());
    }

    @Test
    void prefixedKeybindConflictHidesInternalTranslationKeyFromVisibleText() {
        String raw = "[Axion] The Editor UI keybind has conflicts: key.lazybuilder.terraform.toggle_panel";
        ChatMessage message = MinecraftMessageClassifier.systemMessage(raw);

        assertEquals(ChatMessageType.WARNING, message.type());
        assertEquals("Axion", message.source());
        assertFalse(message.text().contains("key.lazybuilder"));
        assertEquals(raw, message.diagnosticDetail());
    }

    @Test
    void unknownPrefixedMessagesRemainSystemMessages() {
        ChatMessage message = MinecraftMessageClassifier.systemMessage("[Clockwork] Server ready");

        assertEquals(ChatMessageType.SYSTEM, message.type());
        assertEquals("Clockwork", message.source());
        assertEquals("Server ready", message.text());
    }

    @Test
    void unrecognizedTextFallsBackToSystemWithoutDestructiveGuessing() {
        ChatMessage message = MinecraftMessageClassifier.systemMessage("Custom server text");

        assertEquals(ChatMessageType.SYSTEM, message.type());
        assertEquals("Custom server text", message.text());
    }
}
