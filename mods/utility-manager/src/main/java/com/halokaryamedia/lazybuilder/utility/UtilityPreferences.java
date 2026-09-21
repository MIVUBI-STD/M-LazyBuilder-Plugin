package com.halokaryamedia.lazybuilder.utility;

/**
 * Persisted Utility Manager preferences.
 *
 * Only implemented features belong here. This keeps the config surface small and
 * prevents dormant options from implying behavior that does not exist yet.
 */
public record UtilityPreferences(
        boolean borderlessWindow,
        boolean extendedChatHistory,
        boolean keepChatDraft,
        boolean chatSearch,
        boolean chatTimestamps,
        boolean hideChatSigningIndicators,
        boolean hideChatReportButton,
        boolean suppressNarrator,
        boolean reconnectButton,
        boolean contextualScreenshotNames,
        boolean instantCreativeSearch,
        boolean compactDebugHud
) {
    public static UtilityPreferences defaults() {
        return new UtilityPreferences(
                false,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                false,
                true,
                true
        );
    }

    public UtilityPreferences withSuppressNarrator(boolean enabled) {
        return new UtilityPreferences(
                borderlessWindow,
                extendedChatHistory,
                keepChatDraft,
                chatSearch,
                chatTimestamps,
                hideChatSigningIndicators,
                hideChatReportButton,
                enabled,
                reconnectButton,
                contextualScreenshotNames,
                instantCreativeSearch,
                compactDebugHud
        );
    }

    public UtilityPreferences withContextualScreenshotNames(boolean enabled) {
        return copy(enabled, instantCreativeSearch, compactDebugHud);
    }

    public UtilityPreferences withInstantCreativeSearch(boolean enabled) {
        return copy(contextualScreenshotNames, enabled, compactDebugHud);
    }

    public UtilityPreferences withCompactDebugHud(boolean enabled) {
        return copy(contextualScreenshotNames, instantCreativeSearch, enabled);
    }

    private UtilityPreferences copy(
            boolean contextualScreenshotNames,
            boolean instantCreativeSearch,
            boolean compactDebugHud
    ) {
        return new UtilityPreferences(
                borderlessWindow,
                extendedChatHistory,
                keepChatDraft,
                chatSearch,
                chatTimestamps,
                hideChatSigningIndicators,
                hideChatReportButton,
                suppressNarrator,
                reconnectButton,
                contextualScreenshotNames,
                instantCreativeSearch,
                compactDebugHud
        );
    }
}
