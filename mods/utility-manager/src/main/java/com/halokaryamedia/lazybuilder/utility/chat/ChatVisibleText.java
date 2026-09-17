package com.halokaryamedia.lazybuilder.utility.chat;

import java.util.Objects;
import java.util.regex.Pattern;

/** Helpers for user-facing text derived from rendered chat lines. */
public final class ChatVisibleText {
    private static final Pattern TIMESTAMP_PREFIX = Pattern.compile("^(?:\\d{2}:\\d{2}\\s{2}|\\[\\d{2}:\\d{2}:\\d{2}\\]\\s+)");

    private ChatVisibleText() {
    }

    public static String withoutTimestamp(String text) {
        return TIMESTAMP_PREFIX.matcher(Objects.requireNonNullElse(text, "")).replaceFirst("");
    }
}
