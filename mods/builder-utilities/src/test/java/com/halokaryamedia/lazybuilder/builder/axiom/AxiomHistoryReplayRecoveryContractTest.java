package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.operation.RecoverableActiveOperation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AxiomHistoryReplayRecoveryContractTest {
    @Test
    void historyReplayParticipatesInRuntimeWorldExitRecovery() {
        assertTrue(RecoverableActiveOperation.class.isAssignableFrom(
                AxiomMixedHistoryReplayController.class));
    }
}
