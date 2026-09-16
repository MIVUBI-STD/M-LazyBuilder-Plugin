package com.halokaryamedia.lazybuilder.utility.chat;

import java.util.Objects;

/** Immutable normalized chat event before presentation/routing. */
public record ChatMessage(
        ChatMessageType type,
        String source,
        String text,
        String diagnosticDetail
) {
    public ChatMessage {
        type = Objects.requireNonNull(type, "type");
        source = source == null ? "" : source;
        text = Objects.requireNonNullElse(text, "");
        diagnosticDetail = diagnosticDetail == null ? "" : diagnosticDetail;
    }

    public String fingerprint() {
        return type.name() + '\u0000' + source + '\u0000' + text;
    }
}
