package com.halokaryamedia.lazybuilder.utility;

/**
 * Persisted Utility Manager preferences.
 *
 * Values are intentionally conservative. Settings that visibly change Minecraft
 * behavior remain opt-in; passive convenience defaults may be enabled later only
 * when their owning feature is implemented and reviewed.
 */
public record UtilityPreferences(
        boolean borderlessWindow,
        boolean compactInfo,
        boolean chatTimestamps,
        boolean autoReconnect,
        boolean organizeScreenshotsByProject
) {
    public static UtilityPreferences defaults() {
        return new UtilityPreferences(false, false, false, false, false);
    }
}
