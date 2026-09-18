package com.halokaryamedia.lazybuilder.builder.axiom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AxiomCompatibilityContractTest {
    @Test
    void publishesTheReviewedMinorCompatibilityWindow() {
        assertEquals(">=5.3.0 <5.5.0", AxiomCompatibility.SUPPORTED_RANGE);
    }

    @Test
    void summaryIncludesInstalledAndSupportedIdentity() {
        assertEquals(
                "Axiom 5.4.2 | supported >=5.3.0 <5.5.0",
                new AxiomCompatibility("5.4.2", AxiomCompatibility.SUPPORTED_RANGE).summary());
    }
}
