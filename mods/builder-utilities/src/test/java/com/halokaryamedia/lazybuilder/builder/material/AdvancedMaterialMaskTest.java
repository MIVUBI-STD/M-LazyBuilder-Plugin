package com.halokaryamedia.lazybuilder.builder.material;

import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdvancedMaterialMaskTest {
    private static MaterialContext context(int x, int y, int z) {
        return new MaterialContext(x, y, z, "minecraft:stone", new OperationSeed(99L));
    }

    @Test
    void fieldAndHeightRangesComposeDeterministically() {
        ScalarField field = c -> c.x() / 10.0;
        MaterialMask mask = MaterialMasks.and(
                MaterialMasks.fieldBetween(field, 0.2, 0.8),
                MaterialMasks.yBetween(60, 80)
        );
        assertTrue(mask.test(context(5, 64, 0)));
        assertFalse(mask.test(context(1, 64, 0)));
        assertFalse(mask.test(context(5, 90, 0)));
    }

    @Test
    void densityMaskHasPreviewCommitParity() {
        MaterialMask density = MaterialMasks.deterministicDensity(0.35, 123L);
        for (int x = -20; x <= 20; x++) {
            MaterialContext ctx = context(x, 70, x * 2);
            assertEquals(density.test(ctx), density.test(ctx));
        }
    }
}
