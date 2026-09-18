package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.structure.StructurePlacement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AxiomEntityPayloadTransformTest {
    @Test
    void yawFollowsQuarterTurnsAndMirrors() {
        assertEquals(
                90.0f,
                AxiomEntityPayloadTransform.transformYaw(
                        0.0f,
                        new StructurePlacement(0, 0, 0, 1, false, false)),
                0.001f
        );
        assertEquals(
                180.0f,
                Math.abs(AxiomEntityPayloadTransform.transformYaw(
                        0.0f,
                        new StructurePlacement(0, 0, 0, 0, false, true))),
                0.001f
        );
        assertEquals(
                0.0f,
                AxiomEntityPayloadTransform.transformYaw(
                        0.0f,
                        new StructurePlacement(0, 0, 0, 0, true, false)),
                0.001f
        );
    }
}
