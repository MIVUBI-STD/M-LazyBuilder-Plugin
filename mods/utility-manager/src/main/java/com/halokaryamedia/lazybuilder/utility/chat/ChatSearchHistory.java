package com.halokaryamedia.lazybuilder.utility.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Bounded, session-scoped index of rendered chat text.
 *
 * This class stores plain visible text only. It does not persist chat to disk and
 * has no background worker; entries are recorded only when ChatHud receives them.
 */
public final class ChatSearchHistory {
    private static final int DEFAULT_LIMIT = 1000;

    private final int limit;
    private final List<ChatHistoryEntry> entries = new ArrayList<>();

    public ChatSearchHistory() {
        this(DEFAULT_LIMIT);
    }

    ChatSearchHistory(int limit) {
        if (limit < 1) throw new IllegalArgumentException("limit must be at least 1");
        this.limit = limit;
    }

    public synchronized void record(String text, long timestampMillis) {
        String normalized = Objects.requireNonNullElse(text, "");
        if (normalized.isBlank()) return;

        entries.add(new ChatHistoryEntry(timestampMillis, normalized));
        int overflow = entries.size() - limit;
        if (overflow > 0) entries.subList(0, overflow).clear();
    }

    /** Replaces the newest indexed line without creating a second search result. */
    public synchronized void replaceLatest(String text) {
        String normalized = Objects.requireNonNullElse(text, "");
        if (normalized.isBlank() || entries.isEmpty()) return;

        int index = entries.size() - 1;
        ChatHistoryEntry previous = entries.get(index);
        entries.set(index, new ChatHistoryEntry(previous.timestampMillis(), normalized));
    }

    public synchronized List<ChatHistoryEntry> search(String query) {
        String needle = Objects.requireNonNullElse(query, "").trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return List.of();

        List<ChatHistoryEntry> matches = new ArrayList<>();
        for (ChatHistoryEntry entry : entries) {
            if (entry.text().toLowerCase(Locale.ROOT).contains(needle)) {
                matches.add(entry);
            }
        }
        return Collections.unmodifiableList(matches);
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized void clearSession() {
        entries.clear();
    }
}
