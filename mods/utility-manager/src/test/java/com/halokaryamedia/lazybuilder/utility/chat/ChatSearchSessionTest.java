package com.halokaryamedia.lazybuilder.utility.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ChatSearchSessionTest {
    @Test
    void newestMatchIsSelectedFirst() {
        ChatSearchHistory history = new ChatSearchHistory();
        history.record("alpha one", 1);
        history.record("other", 2);
        history.record("alpha two", 3);

        ChatSearchSession session = new ChatSearchSession(history);
        session.setQuery("alpha");

        assertEquals(2, session.matchCount());
        assertEquals(2, session.selectedOrdinal());
        assertEquals("alpha two", session.selected().text());
    }

    @Test
    void navigationWraps() {
        ChatSearchHistory history = new ChatSearchHistory();
        history.record("hit one", 1);
        history.record("hit two", 2);

        ChatSearchSession session = new ChatSearchSession(history);
        session.setQuery("hit");

        assertEquals("hit one", session.next().text());
        assertEquals("hit two", session.previous().text());
    }

    @Test
    void noMatchesStayEmpty() {
        ChatSearchSession session = new ChatSearchSession(new ChatSearchHistory());
        session.setQuery("missing");

        assertEquals(0, session.matchCount());
        assertNull(session.selected());
        assertNull(session.next());
        assertNull(session.previous());
    }
}
