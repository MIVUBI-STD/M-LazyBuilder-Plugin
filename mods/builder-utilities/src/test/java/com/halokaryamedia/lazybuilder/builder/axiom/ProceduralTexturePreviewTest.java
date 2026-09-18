package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import com.halokaryamedia.lazybuilder.builder.region.BlockBounds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProceduralTexturePreviewTest {
    @Test
    void previewIsDeterministicForSameSeedAndSettings() {
        BlockBounds bounds = new BlockBounds(0, 0, 0, 15, 3, 15);
        var first = ProceduralTexturePreview.sample(
                bounds, new OperationSeed(99L), 0.1, 4, 0.55);
        var second = ProceduralTexturePreview.sample(
                bounds, new OperationSeed(99L), 0.1, 4, 0.55);
        assertEquals(first, second);
        assertFalse(first.isEmpty());
    }

    @Test
    void previewRejectsOversizedCandidateVolume() {
        BlockBounds huge = new BlockBounds(0, 0, 0, 1000, 1000, 1);
        assertThrows(IllegalArgumentException.class, () ->
                ProceduralTexturePreview.sample(
                        huge, new OperationSeed(1L), 0.1, 3, 0.5));
    }
}
