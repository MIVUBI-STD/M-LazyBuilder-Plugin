package com.halokaryamedia.lazybuilder.utility.chat;

import java.time.Duration;
import java.util.Objects;

/**
 * Single event-driven entry point for LazyBuilder client-facing utility messages.
 * Producers provide semantic message data; presentation code decides how it looks.
 */
public final class UtilityMessageBus {
    private static final Duration DEFAULT_DUPLICATE_WINDOW = Duration.ofSeconds(8);

    private final ChatDeduplicator deduplicator;

    public UtilityMessageBus() {
        this(new ChatDeduplicator(DEFAULT_DUPLICATE_WINDOW));
    }

    UtilityMessageBus(ChatDeduplicator deduplicator) {
        this.deduplicator = Objects.requireNonNull(deduplicator, "deduplicator");
    }

    public DispatchDecision publish(ChatMessage message, long nowMillis) {
        Objects.requireNonNull(message, "message");
        ChatDeduplicator.Result duplicate = deduplicator.accept(message, nowMillis);

        return new DispatchDecision(
                message,
                ChatRoutingPolicy.showInChat(message.type()),
                shouldToast(message.type()) && !duplicate.duplicate(),
                shouldWriteConsole(message.type()),
                duplicate.duplicate(),
                duplicate.count()
        );
    }

    public void clearSession() {
        deduplicator.clearSession();
    }

    private static boolean shouldToast(ChatMessageType type) {
        return switch (type) {
            case WARNING, ERROR -> true;
            case CHAT, GAME, SYSTEM -> false;
        };
    }

    private static boolean shouldWriteConsole(ChatMessageType type) {
        return switch (type) {
            case SYSTEM, WARNING, ERROR -> true;
            case CHAT, GAME -> false;
        };
    }

    public record DispatchDecision(
            ChatMessage message,
            boolean showInChat,
            boolean showToast,
            boolean writeConsole,
            boolean duplicate,
            int duplicateCount
    ) {
        public DispatchDecision {
            message = Objects.requireNonNull(message, "message");
            if (duplicateCount < 1) throw new IllegalArgumentException("duplicateCount must be at least 1");
        }
    }
}
