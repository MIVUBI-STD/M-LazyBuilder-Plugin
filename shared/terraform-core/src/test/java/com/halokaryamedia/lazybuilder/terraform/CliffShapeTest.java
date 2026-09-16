package com.halokaryamedia.lazybuilder.terraform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CliffShapeTest {
    private static final CliffSpec SPEC = new CliffSpec(
            10, 64, 20,
            1, 0,
            48, 32, 20,
            123456789L
    );

    @Test
    void sameSeedAndInputsProduceSameField() {
        CliffShape a = SPEC.createField();
        CliffShape b = SPEC.createField();

        for (int x = 10; x <= 58; x += 4) {
            for (int y = 64; y <= 96; y += 4) {
                for (int z = 10; z <= 34; z += 4) {
                    assertEquals(a.sample(x, y, z), b.sample(x, y, z), 0.0);
                }
            }
        }
    }

    @Test
    void keepsAReadableSteepFrontFace() {
        CliffShape shape = SPEC.createField();
        double middleX = SPEC.originX() + SPEC.length() * 0.5;
        double middleY = SPEC.originY() + SPEC.height() * 0.45;

        assertTrue(shape.contains(middleX, middleY, SPEC.originZ() - SPEC.width() * 0.15));
        assertFalse(shape.contains(middleX, middleY, SPEC.originZ() - SPEC.width() * 0.70));
    }

    @Test
    void recedesTowardTheBackInsteadOfMakingASymmetricBlob() {
        CliffShape shape = SPEC.createField();
        double middleX = SPEC.originX() + SPEC.length() * 0.5;
        double highY = SPEC.originY() + SPEC.height() * 0.72;

        assertTrue(shape.contains(middleX, highY, SPEC.originZ()));
        assertFalse(shape.contains(middleX, highY, SPEC.originZ() + SPEC.width() * 0.62));
    }

    @Test
    void excludesPointsBeyondTheDraggedLength() {
        CliffShape shape = SPEC.createField();
        assertFalse(shape.contains(
                SPEC.originX() + SPEC.length() + 2,
                SPEC.originY() + 1,
                SPEC.originZ()
        ));
    }

    @Test
    void exposesConservativeSamplingBounds() {
        CliffShape shape = SPEC.createField();
        ShapeBounds bounds = shape.bounds();

        assertTrue(bounds.contains(SPEC.originX(), SPEC.originY(), SPEC.originZ()));
        assertTrue(bounds.contains(
                SPEC.originX() + SPEC.length(),
                SPEC.originY(),
                SPEC.originZ()
        ));
    }
}
