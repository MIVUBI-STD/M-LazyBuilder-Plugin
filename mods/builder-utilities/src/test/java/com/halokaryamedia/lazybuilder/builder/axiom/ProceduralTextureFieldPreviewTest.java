package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.material.ScalarField;
import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import com.halokaryamedia.lazybuilder.builder.region.BlockBounds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProceduralTextureFieldPreviewTest {
    @Test
    void genericFieldPreviewUsesExactThresholdContract() {
        BlockBounds bounds = new BlockBounds(0, 0, 0, 3, 0, 0);
        ScalarField field = context -> context.x() / 3.0;

        var selected = ProceduralTexturePreview.sample(
                bounds,
                new OperationSeed(1L),
                field,
                0.5
        );

        assertEquals(2, selected.size());
        assertEquals(2, selected.get(0).x());
        assertEquals(3, selected.get(1).x());
    }
}
