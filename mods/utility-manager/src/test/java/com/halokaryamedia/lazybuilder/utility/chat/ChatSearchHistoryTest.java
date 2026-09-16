package com.halokaryamedia.lazybuilder.utility.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChatSearchHistoryTest {
    @Test
    void searchIsCaseInsensitiveAndKeepsChronologicalOrder() {
        ChatSearchHistory history = new ChatSearchHistory(10);
        history.record("Berchman joined", 1000);
        history.record("Marcel: Hello", 2000);
        history.record("BERCHMAN left", 3000);

        assertEquals(
                java.util.List.of("Berchman joined", "BERCHMAN left"),
                history.search("berchman").stream().map(ChatHistoryEntry::text).toList()
        );
    }

    @Test
    void historyIsBounded() {
        ChatSearchHistory history = new ChatSearchHistory(2);
        history.record("one", 1);
        history.record("two", 2);
        history.record("three", 3);

        assertEquals(2, history.size());
        assertTrue(history.search("one").isEmpty());
    }

    @Test
    void sessionClearRemovesEntries() {
        ChatSearchHistory history = new ChatSearchHistory();
        history.record("hello", 1);
        history.clearSession();

        assertEquals(0, history.size());
    }

    @Test
    void blankMessagesAreIgnored() {
        ChatSearchHistory history = new ChatSearchHistory();
        history.record("   ", 1);
        assertEquals(0, history.size());
    }
}
