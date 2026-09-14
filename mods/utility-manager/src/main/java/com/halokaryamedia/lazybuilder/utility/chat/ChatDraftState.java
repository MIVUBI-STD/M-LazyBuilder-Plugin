package com.halokaryamedia.lazybuilder.utility.chat;

/**
 * Session-local chat draft state.
 *
 * Draft text is intentionally not written to disk: it only survives closing and
 * reopening the chat during the current Minecraft session.
 */
public final class ChatDraftState {
    private static String draft = "";

    private ChatDraftState() {
    }

    public static String get() {
        return draft;
    }

    public static void save(String text) {
        draft = text == null ? "" : text;
    }

    public static void clear() {
        draft = "";
    }
}
