package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.material.BlockMaterial;
import com.halokaryamedia.lazybuilder.builder.material.ConditionalMaterial;
import com.halokaryamedia.lazybuilder.builder.material.ExistingBlockMaterial;
import com.halokaryamedia.lazybuilder.builder.material.MaterialMasks;
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

        var selected = ProceduralTexturePreview.sampleResolved(
                bounds,
                new OperationSeed(1L),
                new ConditionalMaterial(
                        field,
                        0.5,
                        new BlockMaterial("minecraft:stone"),
                        ExistingBlockMaterial.INSTANCE),
                (x, y, z) -> "minecraft:air",
                MaterialMasks.all()
        );

        assertEquals(2, selected.size());
        assertEquals(2, selected.get(0).x());
        assertEquals(3, selected.get(1).x());
    }
}
