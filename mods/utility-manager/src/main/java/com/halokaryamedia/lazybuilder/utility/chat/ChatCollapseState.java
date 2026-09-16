package com.halokaryamedia.lazybuilder.utility.chat;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Consecutive duplicate tracker used by the vanilla ChatHud adapter.
 *
 * Eligibility is explicitly prepared by a trusted Utility/Fabric adapter before
 * ChatHud sees the line. Repeated arbitrary system text and player chat therefore
 * cannot become collapsible merely because their visible text looks similar.
 */
public final class ChatCollapseState {
    private static final Duration DEFAULT_WINDOW = Duration.ofSeconds(8);
    private static final Pattern TIMESTAMP = Pattern.compile("^(?:\\d{2}:\\d{2}|\\[\\d{2}:\\d{2}(?::\\d{2})?])\\s{1,2}");
    private static final Pattern COUNT_SUFFIX = Pattern.compile("\\s+×\\d+$");

    private final long windowMillis;
    private String preparedFingerprint = "";
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

    /** Marks exactly one upcoming rendered line as eligible for collapse. */
    public void prepareEligible(String renderedText) {
        String baseText = normalize(renderedText);
        preparedFingerprint = fingerprint(baseText);
    }

    public Decision accept(String renderedText, long nowMillis) {
        String baseText = normalize(renderedText);
        String fingerprint = fingerprint(baseText);
        boolean eligible = !preparedFingerprint.isEmpty() && fingerprint.equals(preparedFingerprint);
        preparedFingerprint = "";

        if (!eligible) {
            clearSequence();
            return new Decision(false, false, 1, baseText);
        }

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
        preparedFingerprint = "";
        clearSequence();
    }

    private void clearSequence() {
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

    private static String fingerprint(String text) {
        return text == null || text.isBlank() ? "" : text.toLowerCase(Locale.ROOT);
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
