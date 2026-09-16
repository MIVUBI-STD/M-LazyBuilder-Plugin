package com.halokaryamedia.lazybuilder.utility.chat;

/** Pure routing decisions for normalized Utility Manager chat events. */
public final class ChatRoutingPolicy {
    private ChatRoutingPolicy() {
    }

    public static boolean showInChat(ChatMessageType type) {
        return switch (type) {
            case CHAT, GAME, SYSTEM, WARNING, ERROR -> true;
        };
    }

    public static boolean eligibleForDeduplication(ChatMessageType type) {
        return switch (type) {
            case GAME, SYSTEM, WARNING -> true;
            case CHAT, ERROR -> false;
        };
    }

    public static boolean keepDiagnosticDetail(ChatMessageType type) {
        return switch (type) {
            case WARNING, ERROR -> true;
            case CHAT, GAME, SYSTEM -> false;
        };
    }

    public static boolean showToast(ChatMessageType type) {
        return switch (type) {
            case WARNING, ERROR -> true;
            case CHAT, GAME, SYSTEM -> false;
        };
    }

    public static boolean writeConsole(ChatMessageType type) {
        return switch (type) {
            case SYSTEM, WARNING, ERROR -> true;
            case CHAT, GAME -> false;
        };
    }
}
