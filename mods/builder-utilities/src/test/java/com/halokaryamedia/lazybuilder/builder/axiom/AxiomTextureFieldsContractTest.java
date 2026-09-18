package com.halokaryamedia.lazybuilder.builder.axiom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AxiomTextureFieldsContractTest {
    @Test
    void exposesAllUserFacingFieldNames() {
        assertEquals("Fractal", AxiomTextureFields.name(0));
        assertEquals("Slope", AxiomTextureFields.name(1));
        assertEquals("Curvature", AxiomTextureFields.name(2));
        assertEquals("Flow", AxiomTextureFields.name(3));
        assertEquals("Light", AxiomTextureFields.name(4));
        assertEquals("Ridged", AxiomTextureFields.name(5));
        assertEquals("Cellular", AxiomTextureFields.name(6));
    }
}
