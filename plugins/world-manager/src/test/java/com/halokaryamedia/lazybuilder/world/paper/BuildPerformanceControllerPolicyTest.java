package com.halokaryamedia.lazybuilder.world.paper;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Mob;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;


import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    private static final NamespacedKey TEST_LEASE =
            Objects.requireNonNull(NamespacedKey.fromString("lazybuilder:managed_ai_suppressed"));

    private static final class MobFixture {
        boolean hasAi;
        boolean failDisable;
        final Map<NamespacedKey, Byte> data = new HashMap<>();
        final PersistentDataContainer pdc = (PersistentDataContainer) Proxy.newProxyInstance(
                PersistentDataContainer.class.getClassLoader(), new Class<?>[]{PersistentDataContainer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "has" -> data.containsKey(args[0]);
                    case "set" -> { data.put((NamespacedKey) args[0], (Byte) args[2]); yield null; }
                    case "remove" -> { data.remove(args[0]); yield null; }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        final Mob mob = (Mob) Proxy.newProxyInstance(
                Mob.class.getClassLoader(), new Class<?>[]{Mob.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hasAI" -> hasAi;
                    case "setAI" -> {
                        if (failDisable && Boolean.FALSE.equals(args[0])) {
                            throw new IllegalStateException("simulated setAI failure");
                        }
                        hasAi = (boolean) args[0];
                        yield null;
                    }
                    case "getPersistentDataContainer" -> pdc;
                    default -> throw new UnsupportedOperationException(method.getName());
                });

        MobFixture(boolean hasAi) { this.hasAi = hasAi; }
    }

    @Test
    void leasedAiRestoresWithoutTouchingAuthoredNoAi() {
        MobFixture owned = new MobFixture(true);
        assertEquals(SUPPRESS, BuildPerformanceController.applyAiLeaseDecision(owned.mob, TEST_LEASE, true));
        assertFalse(owned.hasAi);
        assertTrue(owned.data.containsKey(TEST_LEASE));
        assertEquals(RESTORE, BuildPerformanceController.applyAiLeaseDecision(owned.mob, TEST_LEASE, false));
        assertTrue(owned.hasAi);
        assertFalse(owned.data.containsKey(TEST_LEASE));

        MobFixture preexistingNoAi = new MobFixture(false);
        assertEquals(NONE, BuildPerformanceController.applyAiLeaseDecision(preexistingNoAi.mob, TEST_LEASE, true));
        assertFalse(preexistingNoAi.hasAi);
        assertFalse(preexistingNoAi.data.containsKey(TEST_LEASE));
    }

    @Test
    void failedSetAiRollsBackOwnershipMarker() {
        MobFixture broken = new MobFixture(true);
        broken.failDisable = true;
        assertThrows(IllegalStateException.class, () ->
                BuildPerformanceController.applyAiLeaseDecision(broken.mob, TEST_LEASE, true));
        assertTrue(broken.hasAi);
        assertFalse(broken.data.containsKey(TEST_LEASE));
    }

    @Test
    void disablingScopeOrLeavingWorldRestoresOnlyOwnedAi() {
        assertEquals(RESTORE, BuildPerformanceController.decideAiAction(true, false, false));
        assertEquals(CLEAR_LEASE, BuildPerformanceController.decideAiAction(true, true, false));
        assertEquals(CLEAR_LEASE, BuildPerformanceController.decideAiAction(true, true, true));
        assertEquals(NONE, BuildPerformanceController.decideAiAction(false, false, false));
    }
}
