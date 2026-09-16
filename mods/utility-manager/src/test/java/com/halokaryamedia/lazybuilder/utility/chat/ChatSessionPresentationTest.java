package com.halokaryamedia.lazybuilder.utility.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ChatSessionPresentationTest {
    @Test
    void sessionSeparatorUsesTargetAndShortLocalTime() {
        ChatMessage message = ChatSessionPresentation.started(
                "localhost:25565",
                0L,
                ZoneId.of("UTC")
        );

        assertEquals(ChatMessageType.SYSTEM, message.type());
        assertEquals("──────── localhost:25565 • 00:00 ────────", message.text());
    }

    @Test
    void blankTargetFallsBackWithoutLeakingNull() {
        ChatMessage message = ChatSessionPresentation.started(" ", 0L, ZoneId.of("UTC"));
        assertEquals("──────── Minecraft • 00:00 ────────", message.text());
    }
}
