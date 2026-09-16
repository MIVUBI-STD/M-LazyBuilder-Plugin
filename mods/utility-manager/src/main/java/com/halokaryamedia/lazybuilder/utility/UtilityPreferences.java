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
                false,
                true,
                true
        );
    }
}
