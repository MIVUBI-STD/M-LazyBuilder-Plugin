package com.halokaryamedia.lazybuilder.performance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BackgroundResourcePolicyTest {
    private final PerformancePreferences defaults = PerformancePreferences.defaults();

    @Test
    void focusedClientKeepsUserLimit() {
        assertEquals(144, BackgroundResourcePolicy.targetLimit(144, defaults, true, false));
    }

    @Test
    void unfocusedClientUsesConfiguredCapWithoutRaisingUserLimit() {
        assertEquals(30, BackgroundResourcePolicy.targetLimit(144, defaults, false, false));
        assertEquals(20, BackgroundResourcePolicy.targetLimit(20, defaults, false, false));
    }

    @Test
    void minimizedClientUsesStricterCap() {
        assertEquals(10, BackgroundResourcePolicy.targetLimit(144, defaults, false, true));
        assertEquals(8, BackgroundResourcePolicy.targetLimit(8, defaults, false, true));
    }

    @Test
    void disabledPolicyLeavesUserLimitUntouched() {
        PerformancePreferences disabled = new PerformancePreferences(false, 30, 10);
        assertEquals(75, BackgroundResourcePolicy.targetLimit(75, disabled, false, true));
    }

    @Test
    void invalidPreferenceLimitsAreSanitized() {
        PerformancePreferences invalid = new PerformancePreferences(true, 1, 999);
        assertEquals(30, BackgroundResourcePolicy.targetLimit(120, invalid, false, false));
        assertEquals(10, BackgroundResourcePolicy.targetLimit(120, invalid, false, true));
    }
}
