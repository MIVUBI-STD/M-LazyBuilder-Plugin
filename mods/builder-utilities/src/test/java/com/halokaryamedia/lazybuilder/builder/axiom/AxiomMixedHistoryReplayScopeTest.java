package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.history.ScopedOperationIds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AxiomMixedHistoryReplayScopeTest {
    @Test
    void allowsHistoryBoundToTheActiveWorld() {
        String operation = ScopedOperationIds.scope("world-a", "builder-edit");
        assertDoesNotThrow(
                () -> AxiomMixedHistoryReplayController.requireReplayScope(operation, "world-a"));
    }

    @Test
    void refusesOtherWorldAndLegacyUnscopedHistoryBeforeLeasing() {
        String otherWorld = ScopedOperationIds.scope("world-a", "builder-edit");
        assertThrows(IllegalStateException.class,
                () -> AxiomMixedHistoryReplayController.requireReplayScope(otherWorld, "world-b"));
        assertThrows(IllegalStateException.class,
                () -> AxiomMixedHistoryReplayController.requireReplayScope("legacy-edit", "world-b"));
    }
}
