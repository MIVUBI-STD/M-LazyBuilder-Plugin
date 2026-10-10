package com.halokaryamedia.lazybuilder.world.paper;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BuildPerformanceControllerPolicyTest {
    private static final BuildPerformanceController.AiAction NONE = BuildPerformanceController.AiAction.NONE;
    private static final BuildPerformanceController.AiAction SUPPRESS = BuildPerformanceController.AiAction.SUPPRESS;
    private static final BuildPerformanceController.AiAction RESTORE = BuildPerformanceController.AiAction.RESTORE;
    private static final BuildPerformanceController.AiAction CLEAR_LEASE = BuildPerformanceController.AiAction.CLEAR_LEASE;

    @Test
    void noAllowlistMeansNoImplicitWorldScope() {
        assertEquals(Set.of(), BuildPerformanceController.normalizeEnabledWorldFolders(null));
        assertEquals(Set.of(), BuildPerformanceController.normalizeEnabledWorldFolders(List.of()));
        assertEquals(Set.of("my-build", "shared-project"),
                BuildPerformanceController.normalizeEnabledWorldFolders(List.of("my-build", "shared-project", "my-build")));
    }

    @Test
    void malformedOrGlobalScopeFailsClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> BuildPerformanceController.normalizeEnabledWorldFolders("my-build"));
        assertThrows(IllegalArgumentException.class,
                () -> BuildPerformanceController.normalizeEnabledWorldFolders(List.of("*")));
        assertThrows(IllegalArgumentException.class,
                () -> BuildPerformanceController.normalizeEnabledWorldFolders(List.of(" my-build")));
        assertThrows(IllegalArgumentException.class,
                () -> BuildPerformanceController.normalizeEnabledWorldFolders(List.of(42)));
        assertThrows(IllegalArgumentException.class,
                () -> BuildPerformanceController.normalizeEnabledWorldFolders(List.of("world\nother")));
    }

    @Test
    void originalNoAiStateIsNotClaimedOrSilentlyEnabled() {
        assertEquals(NONE, BuildPerformanceController.decideAiAction(false, false, false));
        assertEquals(NONE, BuildPerformanceController.decideAiAction(false, false, true));
        assertEquals(NONE, BuildPerformanceController.decideAiAction(false, true, false));
    }

    @Test
    void suppressionClaimsOnlyAiThatWasEnabled() {
        assertEquals(SUPPRESS, BuildPerformanceController.decideAiAction(false, true, true));
        assertEquals(NONE, BuildPerformanceController.decideAiAction(true, false, true));
    }

    @Test
    void disablingScopeOrLeavingWorldRestoresOnlyOwnedAi() {
        assertEquals(RESTORE, BuildPerformanceController.decideAiAction(true, false, false));
        assertEquals(CLEAR_LEASE, BuildPerformanceController.decideAiAction(true, true, false));
        assertEquals(CLEAR_LEASE, BuildPerformanceController.decideAiAction(true, true, true));
        assertEquals(NONE, BuildPerformanceController.decideAiAction(false, false, false));
    }
}
