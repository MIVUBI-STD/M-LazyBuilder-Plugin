package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.material.BlockMaterial;
import com.halokaryamedia.lazybuilder.builder.material.MaterialMasks;
import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import com.halokaryamedia.lazybuilder.builder.region.BlockBounds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProceduralTextureMaskPreviewTest {
    @Test
    void previewHonorsExistingStateMask() {
        var points = ProceduralTexturePreview.sampleResolved(
                new BlockBounds(0, 64, 0, 1, 64, 0),
                new OperationSeed(1),
                new BlockMaterial("minecraft:dirt"),
                (x, y, z) -> x == 0 ? "minecraft:stone" : "minecraft:air",
                MaterialMasks.not(MaterialMasks.existingState("minecraft:air"))
        );
        assertEquals(1, points.size());
        assertEquals(0, points.get(0).x());
    }
}
