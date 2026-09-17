package com.halokaryamedia.lazybuilder.builder.axiom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class AxiomSplineToolContractTest {
    @Test
    void splineToolRemainsPreviewOnly() {
        assertFalse(AxiomSplineToolContract.WORLD_MUTATION_ENABLED);
    }
}
