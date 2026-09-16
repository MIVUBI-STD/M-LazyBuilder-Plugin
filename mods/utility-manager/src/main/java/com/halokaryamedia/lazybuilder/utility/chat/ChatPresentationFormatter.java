package com.halokaryamedia.lazybuilder.utility.chat;

import java.util.Objects;

/**
 * Pure presentation formatter for normalized Utility messages.
 * Keeps technical diagnostic detail out of the normal chat line.
 */
public final class ChatPresentationFormatter {
    private ChatPresentationFormatter() {
    }

    public static String chatLine(ChatMessage message, int duplicateCount) {
        Objects.requireNonNull(message, "message");
        if (duplicateCount < 1) throw new IllegalArgumentException("duplicateCount must be at least 1");

        StringBuilder line = new StringBuilder();
        switch (message.type()) {
            case WARNING -> line.append("⚠ ");
            case ERROR -> line.append("! ");
            case CHAT, GAME, SYSTEM -> {
            }
        }

        if (!message.source().isBlank()) {
            line.append(message.source()).append("  ");
        }
        line.append(message.text());

        if (duplicateCount > 1) {
            line.append(" ×").append(duplicateCount);
        }
        return line.toString();
    }

    public static String toastTitle(ChatMessage message) {
        Objects.requireNonNull(message, "message");
        String severity = switch (message.type()) {
            case WARNING -> "Warning";
            case ERROR -> "Error";
            case CHAT, GAME, SYSTEM -> "LazyBuilder";
        };
        return message.source().isBlank() ? severity : severity + " · " + message.source();
    }

    public static String consoleLine(ChatMessage message) {
        Objects.requireNonNull(message, "message");
        String base = chatLine(message, 1);
        return message.diagnosticDetail().isBlank()
                ? base
                : base + " | " + message.diagnosticDetail();
    }
}
