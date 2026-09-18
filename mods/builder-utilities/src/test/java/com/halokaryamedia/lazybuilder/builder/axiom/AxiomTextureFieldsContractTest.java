package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.material.MaterialContext;
import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AxiomTextureFieldsContractTest {
    @Test
    void exposesStableModeNames() {
        assertEquals("Noise", AxiomTextureFields.name(0));
        assertEquals("Slope", AxiomTextureFields.name(1));
        assertEquals("Curvature", AxiomTextureFields.name(2));
        assertEquals("Flow", AxiomTextureFields.name(3));
        assertEquals("Light", AxiomTextureFields.name(4));
        assertEquals("Unknown", AxiomTextureFields.name(99));
    }
}
