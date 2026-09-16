package com.halokaryamedia.lazybuilder.utility.chat;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Consecutive duplicate tracker used by the vanilla ChatHud adapter.
 *
 * Only a small, explicit set of compact GAME/WARNING presentation shapes are
 * eligible. Player chat and unknown system text are never collapsed here.
 */
public final class ChatCollapseState {
    private static final Duration DEFAULT_WINDOW = Duration.ofSeconds(8);
    private static final Pattern TIMESTAMP = Pattern.compile("^(?:\\d{2}:\\d{2}|\\[\\d{2}:\\d{2}(?::\\d{2})?])\\s{1,2}");
    private static final Pattern COUNT_SUFFIX = Pattern.compile("\\s+×\\d+$");

    private final long windowMillis;
    private String lastFingerprint = "";
    private long lastSeenMillis = Long.MIN_VALUE;
    private int count;

    public ChatCollapseState() {
        this(DEFAULT_WINDOW);
    }

    ChatCollapseState(Duration window) {
        Objects.requireNonNull(window, "window");
        if (window.isNegative()) throw new IllegalArgumentException("window must not be negative");
        this.windowMillis = window.toMillis();
    }

    public Decision accept(String renderedText, long nowMillis) {
        String baseText = normalize(renderedText);
        if (!eligible(baseText)) {
            clear();
            return new Decision(false, false, 1, baseText);
        }

        String fingerprint = baseText.toLowerCase(Locale.ROOT);
        boolean insideWindow = !lastFingerprint.isEmpty()
                && fingerprint.equals(lastFingerprint)
                && nowMillis - lastSeenMillis <= windowMillis;

        if (!insideWindow) {
            lastFingerprint = fingerprint;
            lastSeenMillis = nowMillis;
            count = 1;
            return new Decision(true, false, 1, baseText);
        }

        lastSeenMillis = nowMillis;
        count++;
        return new Decision(true, true, count, baseText);
    }

    public void clear() {
        lastFingerprint = "";
        lastSeenMillis = Long.MIN_VALUE;
        count = 0;
    }

    static String normalize(String renderedText) {
        String text = Objects.requireNonNullElse(renderedText, "").trim();
        text = TIMESTAMP.matcher(text).replaceFirst("");
        text = COUNT_SUFFIX.matcher(text).replaceFirst("");
        return text.trim();
    }

    static boolean eligible(String text) {
        if (text == null || text.isBlank()) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        if (text.startsWith("⚠ ")) return true;
        if (text.startsWith("Gamemode → ")) return true;
        if (lower.endsWith(" joined") || lower.endsWith(" left")) return true;
        return lower.contains(" has made the advancement ")
                || lower.contains(" has completed the challenge ");
    }

    public record Decision(boolean eligible, boolean collapse, int count, String baseText) {
        public Decision {
            if (count < 1) throw new IllegalArgumentException("count must be at least 1");
            baseText = Objects.requireNonNullElse(baseText, "");
        }

        public String collapsedText() {
            return count <= 1 ? baseText : baseText + " ×" + count;
        }
    }
}
