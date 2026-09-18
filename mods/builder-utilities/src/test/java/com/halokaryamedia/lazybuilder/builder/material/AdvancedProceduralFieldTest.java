package com.halokaryamedia.lazybuilder.builder.material;

import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdvancedProceduralFieldTest {
    private static MaterialContext context(int x, int y, int z) {
        return new MaterialContext(x, y, z, "minecraft:stone", new OperationSeed(1234L));
    }

    @Test
    void fractalRidgedAndCellularFieldsAreDeterministicAndBounded() {
        ScalarField fractal = new FractalNoiseField(0.08, 4, 2.0, 0.5, 10L);
        ScalarField ridged = new RidgedNoiseField(fractal, 1.5);
        ScalarField cellular = new CellularNoiseField(0.12, 20L);

        for (int i = -8; i <= 8; i++) {
            MaterialContext ctx = context(i * 3, 70 + i, i * -5);
            double a = fractal.sample(ctx);
            double b = ridged.sample(ctx);
            double c = cellular.sample(ctx);
            assertEquals(a, fractal.sample(ctx));
            assertTrue(a >= 0.0 && a <= 1.0);
            assertTrue(b >= 0.0 && b <= 1.0);
            assertTrue(c >= 0.0 && c <= 1.0);
        }
    }

}
