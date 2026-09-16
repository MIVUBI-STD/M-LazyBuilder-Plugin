package com.halokaryamedia.lazybuilder.utility.chat;

import java.util.Objects;

/** Immutable, session-scoped chat history entry used by Utility search. */
public record ChatHistoryEntry(long timestampMillis, String text) {
    public ChatHistoryEntry {
        text = Objects.requireNonNullElse(text, "");
    }
}
