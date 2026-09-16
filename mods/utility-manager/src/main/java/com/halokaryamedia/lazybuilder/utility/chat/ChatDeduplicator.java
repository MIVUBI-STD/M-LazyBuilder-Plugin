package com.halokaryamedia.lazybuilder.utility.chat;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Session-scoped duplicate suppression for non-player utility messages.
 * Event-driven only: no tick loop or background worker.
 */
public final class ChatDeduplicator {
    private static final int DEFAULT_MAX_TRACKED_MESSAGES = 256;

    private final long windowMillis;
    private final int maxTrackedMessages;
    private final Map<String, SeenMessage> seen;

    public ChatDeduplicator(Duration window) {
        this(window, DEFAULT_MAX_TRACKED_MESSAGES);
    }

    ChatDeduplicator(Duration window, int maxTrackedMessages) {
        Objects.requireNonNull(window, "window");
        if (window.isNegative()) throw new IllegalArgumentException("window must not be negative");
        if (maxTrackedMessages < 1) throw new IllegalArgumentException("maxTrackedMessages must be at least 1");
        this.windowMillis = window.toMillis();
        this.maxTrackedMessages = maxTrackedMessages;
        this.seen = new LinkedHashMap<>(16, 0.75f, true);
    }

    public Result accept(ChatMessage message, long nowMillis) {
        Objects.requireNonNull(message, "message");

        if (!ChatRoutingPolicy.eligibleForDeduplication(message.type())) {
            return new Result(false, 1);
        }

        String fingerprint = message.fingerprint();
        SeenMessage previous = seen.get(fingerprint);
        if (previous == null || nowMillis - previous.lastSeenMillis() > windowMillis) {
            seen.put(fingerprint, new SeenMessage(nowMillis, 1));
            trimToBound();
            return new Result(false, 1);
        }

        int count = previous.count() + 1;
        seen.put(fingerprint, new SeenMessage(nowMillis, count));
        return new Result(true, count);
    }

    public void clearSession() {
        seen.clear();
    }

    int trackedMessageCount() {
        return seen.size();
    }

    private void trimToBound() {
        while (seen.size() > maxTrackedMessages) {
            String oldest = seen.keySet().iterator().next();
            seen.remove(oldest);
        }
    }

    private record SeenMessage(long lastSeenMillis, int count) {
    }

    public record Result(boolean duplicate, int count) {
        public Result {
            if (count < 1) throw new IllegalArgumentException("count must be at least 1");
        }
    }
}
