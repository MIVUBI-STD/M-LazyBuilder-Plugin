package com.halokaryamedia.lazybuilder.utility.chat;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conservative classifier for Minecraft/mod text before Utility presentation.
 *
 * Player chat should enter through {@link #playerChat(String, String)} so it is
 * never guessed from rendered text. System classification intentionally handles
 * only stable, high-value patterns and falls back to SYSTEM rather than trying
 * to infer every possible server/plugin format.
 */
public final class MinecraftMessageClassifier {
    private static final Pattern PREFIXED = Pattern.compile("^\\[([^]]+)]\\s*(.*)$");
    private static final Pattern INTERNAL_KEY = Pattern.compile("\\bkey\\.[a-z0-9_.-]+\\b", Pattern.CASE_INSENSITIVE);

    private MinecraftMessageClassifier() {
    }

    public static ChatMessage playerChat(String sender, String text) {
        return new ChatMessage(
                ChatMessageType.CHAT,
                Objects.requireNonNullElse(sender, ""),
                Objects.requireNonNullElse(text, ""),
                ""
        );
    }

    public static ChatMessage systemMessage(String rawText) {
        String raw = Objects.requireNonNullElse(rawText, "").trim();
        if (raw.isEmpty()) return new ChatMessage(ChatMessageType.SYSTEM, "", "", "");

        String lower = raw.toLowerCase(Locale.ROOT);

        if (lower.startsWith("unknown or incomplete command") || lower.startsWith("unknown command")) {
            return new ChatMessage(ChatMessageType.ERROR, "Command", "Unknown command", raw);
        }

        if (lower.startsWith("incorrect argument for command") || lower.startsWith("expected ")) {
            return new ChatMessage(ChatMessageType.ERROR, "Command", "Invalid command argument", raw);
        }

        if (lower.endsWith(" joined the game")) {
            return new ChatMessage(ChatMessageType.GAME, "", stripSuffix(raw, " joined the game") + " joined", "");
        }

        if (lower.endsWith(" left the game")) {
            return new ChatMessage(ChatMessageType.GAME, "", stripSuffix(raw, " left the game") + " left", "");
        }

        if (lower.startsWith("game mode set to ")) {
            String mode = raw.substring("Game mode set to ".length()).trim();
            return new ChatMessage(ChatMessageType.GAME, "", "Gamemode → " + mode, "");
        }

        Matcher prefixed = PREFIXED.matcher(raw);
        if (prefixed.matches()) {
            String source = prefixed.group(1).trim();
            String body = prefixed.group(2).trim();
            String bodyLower = body.toLowerCase(Locale.ROOT);

            if (bodyLower.contains("conflict") || bodyLower.contains("warning")) {
                String visible = INTERNAL_KEY.matcher(body).replaceAll("").replaceAll("\\s+", " ").trim();
                if (visible.endsWith(":")) visible = visible.substring(0, visible.length() - 1).trim();
                if (visible.isBlank()) visible = "Configuration conflict";
                return new ChatMessage(ChatMessageType.WARNING, source, visible, raw);
            }

            return new ChatMessage(ChatMessageType.SYSTEM, source, body, "");
        }

        if (lower.contains(" has made the advancement ") || lower.contains(" has completed the challenge ")) {
            return new ChatMessage(ChatMessageType.GAME, "", raw, "");
        }

        return new ChatMessage(ChatMessageType.SYSTEM, "", raw, "");
    }

    private static String stripSuffix(String value, String suffix) {
        return value.substring(0, value.length() - suffix.length()).trim();
    }
}
