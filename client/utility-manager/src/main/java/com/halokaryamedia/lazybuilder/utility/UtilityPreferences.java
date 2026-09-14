package com.halokaryamedia.lazybuilder.utility;

/**
 * Persisted Utility Manager preferences.
 *
 * Defaults preserve familiar Minecraft behavior. Passive conveniences that do not
 * introduce a new workflow may default on; visual/automatic behavior remains opt-in.
 */
public record UtilityPreferences(
        boolean borderlessWindow,
        boolean compactInfo,
        boolean extendedChatHistory,
        boolean keepChatDraft,
        boolean chatTimestamps,
        boolean reconnectButton,
        boolean autoReconnect,
        boolean organizeScreenshotsByProject
) {
    public static UtilityPreferences defaults() {
        return new UtilityPreferences(
                false,
                false,
                true,
                true,
                false,
                true,
                false,
                false
        );
    }
}
