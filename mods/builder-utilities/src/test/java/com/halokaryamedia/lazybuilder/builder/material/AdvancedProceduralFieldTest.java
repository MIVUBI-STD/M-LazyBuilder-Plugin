package com.halokaryamedia.lazybuilder.builder.material;

import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdvancedProceduralFieldTest {
    private static MaterialContext context(int x, int y, int z) {
        return new MaterialContext(x, y, z, "minecraft:stone", new OperationSeed(12345L));
    }

    @Test
    void fractalAndRidgedNoiseAreDeterministicAndBounded() {
        ScalarField fractal = new FractalNoiseField(0.07, 5, 2.0, 0.5, 11L);
        ScalarField ridged = new RidgedNoiseField(fractal, 2.0);
        MaterialContext context = context(40, 72, -18);

        double a = fractal.sample(context);
        double b = fractal.sample(context);
        assertEquals(a, b);
        assertTrue(a >= 0.0 && a <= 1.0);

        double ridge = ridged.sample(context);
        assertTrue(ridge >= 0.0 && ridge <= 1.0);
    }

    @Test
    void cellularNoiseIsDeterministicAndBounded() {
        ScalarField cells = new CellNoiseField(0.1, 99L);
        double first = cells.sample(context(-31, 4, 88));
        double second = cells.sample(context(-31, 4, 88));
        assertEquals(first, second);
        assertTrue(first >= 0.0 && first <= 1.0);
    }

    @Test
    void lightFieldNormalizesVanillaRange() {
        ScalarField field = new LightLevelField((x, y, z) -> 12);
        assertEquals(0.8, field.sample(context(0, 0, 0)), 1.0e-9);
        assertThrows(IllegalArgumentException.class,
                () -> new LightLevelField((x, y, z) -> 16).sample(context(0, 0, 0)));
    }

    @Test
    void surfaceFlowDetectsDownhillAlignment() {
        SurfaceHeightFieldSource eastRising = (x, z) -> x;
        ScalarField eastFlow = new SurfaceFlowField(eastRising, 1, 1.0, 0.0);
        ScalarField westFlow = new SurfaceFlowField(eastRising, 1, -1.0, 0.0);

        assertEquals(0.0, eastFlow.sample(context(0, 0, 0)), 1.0e-9);
        assertEquals(1.0, westFlow.sample(context(0, 0, 0)), 1.0e-9);
    }

    @Test
    void scalarFieldCompositionCombinesSources() {
        ScalarField constantA = ignored -> 0.25;
        ScalarField constantB = ignored -> 0.5;
        assertEquals(0.75, ScalarFields.add(constantA, constantB).sample(context(0, 0, 0)), 1.0e-9);
        assertEquals(0.125, ScalarFields.multiply(constantA, constantB).sample(context(0, 0, 0)), 1.0e-9);
        assertEquals(0.75, ScalarFields.invert01(constantA).sample(context(0, 0, 0)), 1.0e-9);
    }
}
