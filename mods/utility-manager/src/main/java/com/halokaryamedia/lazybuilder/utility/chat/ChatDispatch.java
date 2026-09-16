package com.halokaryamedia.lazybuilder.utility.chat;

import java.util.Objects;

/** Immutable routing result emitted by UtilityMessageBus. */
public record ChatDispatch(ChatMessage message, ChatRoute route) {
    public ChatDispatch {
        message = Objects.requireNonNull(message, "message");
        route = Objects.requireNonNull(route, "route");
    }
}
