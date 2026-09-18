package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.material.BlockMaterial;
import com.halokaryamedia.lazybuilder.builder.material.MaterialMasks;
import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import com.halokaryamedia.lazybuilder.builder.region.BlockBounds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProceduralTexturePreviewScaleTest {
    @Test
    void largeRegionsUseDecimatedPreviewInsteadOfRejectingApplySize() {
        BlockBounds bounds = new BlockBounds(0, 0, 0, 999, 9, 999);
        assertTrue(ProceduralTexturePreview.isDecimated(bounds));

        var preview = ProceduralTexturePreview.sampleResolved(
                bounds,
                new OperationSeed(1),
                new BlockMaterial("minecraft:stone"),
                (x, y, z) -> "minecraft:air",
                MaterialMasks.all()
        );
        assertFalse(preview.isEmpty());
        assertTrue(preview.size() <= ProceduralTexturePreview.MAX_PREVIEW_VOXELS);
    }

    @Test
    void smallRegionsRemainExactPreview() {
        BlockBounds bounds = new BlockBounds(0, 0, 0, 9, 9, 9);
        assertFalse(ProceduralTexturePreview.isDecimated(bounds));
        assertEquals(1000, ProceduralTexturePreview.sampleResolved(
                bounds,
                new OperationSeed(1),
                new BlockMaterial("minecraft:stone"),
                (x, y, z) -> "minecraft:air",
                MaterialMasks.all()
        ).size());
    }
}
