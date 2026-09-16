package com.halokaryamedia.lazybuilder.utility.chat;

import java.util.List;
import java.util.Objects;

/** Lightweight state for a Ctrl+F style chat search overlay. */
public final class ChatSearchSession {
    private final ChatSearchHistory history;
    private String query = "";
    private List<ChatHistoryEntry> matches = List.of();
    private int selectedIndex = -1;

    public ChatSearchSession(ChatSearchHistory history) {
        this.history = Objects.requireNonNull(history, "history");
    }

    public void setQuery(String query) {
        this.query = Objects.requireNonNullElse(query, "");
        this.matches = history.search(this.query);
        this.selectedIndex = matches.isEmpty() ? -1 : matches.size() - 1;
    }

    public ChatHistoryEntry next() {
        if (matches.isEmpty()) return null;
        selectedIndex = (selectedIndex + 1) % matches.size();
        return matches.get(selectedIndex);
    }

    public ChatHistoryEntry previous() {
        if (matches.isEmpty()) return null;
        selectedIndex = (selectedIndex - 1 + matches.size()) % matches.size();
        return matches.get(selectedIndex);
    }

    public ChatHistoryEntry selected() {
        return selectedIndex < 0 ? null : matches.get(selectedIndex);
    }

    public int selectedOrdinal() {
        return selectedIndex < 0 ? 0 : selectedIndex + 1;
    }

    public int matchCount() {
        return matches.size();
    }

    public String query() {
        return query;
    }

    public void clear() {
        query = "";
        matches = List.of();
        selectedIndex = -1;
    }
}
