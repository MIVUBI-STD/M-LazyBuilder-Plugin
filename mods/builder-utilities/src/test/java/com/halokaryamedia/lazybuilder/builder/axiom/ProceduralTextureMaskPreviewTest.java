package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.material.MaterialMasks;
import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import com.halokaryamedia.lazybuilder.builder.region.BlockBounds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProceduralTextureMaskPreviewTest {
    @Test
    void previewHonorsExistingStateMask() {
        var points = ProceduralTexturePreview.sample(
                new BlockBounds(0, 64, 0, 1, 64, 0),
                new OperationSeed(1),
                context -> 1.0,
                0.5,
                (x, y, z) -> x == 0 ? "minecraft:stone" : "minecraft:air",
                MaterialMasks.not(MaterialMasks.existingState("minecraft:air"))
        );
        assertEquals(1, points.size());
        assertEquals(0, points.get(0).x());
    }
}
