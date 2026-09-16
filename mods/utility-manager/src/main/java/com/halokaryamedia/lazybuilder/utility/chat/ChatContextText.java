package com.halokaryamedia.lazybuilder.utility.chat;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure text parsing used by the small vanilla chat context menu. */
public final class ChatContextText {
    private static final Pattern VANILLA_PLAYER_CHAT = Pattern.compile("^<([^<>]{1,32})>\\s+(.+)$");

    private ChatContextText() {
    }

    public static Parsed parse(String visibleText) {
        String text = ChatVisibleText.withoutTimestamp(Objects.requireNonNullElse(visibleText, "")).trim();
        if (text.isEmpty()) return new Parsed("", "", "");

        Matcher matcher = VANILLA_PLAYER_CHAT.matcher(text);
        if (matcher.matches()) {
            return new Parsed(text, matcher.group(1).trim(), matcher.group(2).trim());
        }
        return new Parsed(text, "", text);
    }

    public record Parsed(String fullText, String sender, String body) {
        public Parsed {
            fullText = Objects.requireNonNullElse(fullText, "");
            sender = Objects.requireNonNullElse(sender, "");
            body = Objects.requireNonNullElse(body, "");
        }

        public boolean hasSender() {
            return !sender.isBlank();
        }
    }
}
