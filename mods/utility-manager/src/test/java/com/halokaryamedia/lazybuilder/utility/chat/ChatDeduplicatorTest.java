package com.halokaryamedia.lazybuilder.utility.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ChatDeduplicatorTest {
    @Test
    void playerChatIsNeverCollapsed() {
        ChatDeduplicator deduplicator = new ChatDeduplicator(Duration.ofSeconds(8));
        ChatMessage message = new ChatMessage(ChatMessageType.CHAT, "Berchman", "hello", "");

        assertFalse(deduplicator.accept(message, 1000).duplicate());
        assertFalse(deduplicator.accept(message, 1001).duplicate());
    }

    @Test
    void eligibleSystemMessagesCollapseInsideWindow() {
        ChatDeduplicator deduplicator = new ChatDeduplicator(Duration.ofSeconds(8));
        ChatMessage message = new ChatMessage(ChatMessageType.SYSTEM, "Local Host", "Server ready", "");

        assertFalse(deduplicator.accept(message, 1000).duplicate());
        ChatDeduplicator.Result second = deduplicator.accept(message, 1500);
        assertTrue(second.duplicate());
        assertEquals(2, second.count());
    }

    @Test
    void duplicateWindowExpires() {
        ChatDeduplicator deduplicator = new ChatDeduplicator(Duration.ofSeconds(1));
        ChatMessage message = new ChatMessage(ChatMessageType.WARNING, "Terraform", "Keybind conflict", "detail");

        deduplicator.accept(message, 1000);
        assertFalse(deduplicator.accept(message, 2101).duplicate());
    }

    @Test
    void trackingIsBounded() {
        ChatDeduplicator deduplicator = new ChatDeduplicator(Duration.ofMinutes(1), 2);

        deduplicator.accept(new ChatMessage(ChatMessageType.SYSTEM, "a", "1", ""), 1);
        deduplicator.accept(new ChatMessage(ChatMessageType.SYSTEM, "b", "2", ""), 2);
        deduplicator.accept(new ChatMessage(ChatMessageType.SYSTEM, "c", "3", ""), 3);

        assertEquals(2, deduplicator.trackedMessageCount());
    }
}
