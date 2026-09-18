package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.material.BlockMaterial;
import com.halokaryamedia.lazybuilder.builder.material.ConditionalMaterial;
import com.halokaryamedia.lazybuilder.builder.material.ExistingBlockMaterial;
import com.halokaryamedia.lazybuilder.builder.material.MaterialMasks;
import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import com.halokaryamedia.lazybuilder.builder.region.BlockBounds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProceduralTexturePreviewTest {
    @Test
    void previewIsDeterministicForSameSeedAndSettings() {
        BlockBounds bounds = new BlockBounds(0, 0, 0, 15, 3, 15);
        var material = new ConditionalMaterial(
                ProceduralTexturePreview.field(0.1, 4),
                0.55,
                new BlockMaterial("minecraft:stone"),
                ExistingBlockMaterial.INSTANCE);
        var first = ProceduralTexturePreview.sampleResolved(
                bounds, new OperationSeed(99L), material,
                (x, y, z) -> "minecraft:air", MaterialMasks.all());
        var second = ProceduralTexturePreview.sampleResolved(
                bounds, new OperationSeed(99L), material,
                (x, y, z) -> "minecraft:air", MaterialMasks.all());
        assertEquals(first, second);
        assertFalse(first.isEmpty());
    }

}
