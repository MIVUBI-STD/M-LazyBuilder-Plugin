package com.halokaryamedia.lazybuilder.builder.material;

import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WeightedPaletteMaterialTest {
    @Test
    void previewAndCommitResolveIdenticallyFromSameSeedAndPosition() {
        BuilderMaterial palette = new WeightedPaletteMaterial(List.of(
                new WeightedPaletteMaterial.Entry(new BlockMaterial("minecraft:stone"), 7),
                new WeightedPaletteMaterial.Entry(new BlockMaterial("minecraft:andesite"), 3)
        ), 11L);
        MaterialContext preview = new MaterialContext(31, 72, -19, "minecraft:dirt", new OperationSeed(99));
        MaterialContext commit = new MaterialContext(31, 72, -19, "minecraft:dirt", new OperationSeed(99));
        assertEquals(palette.resolve(preview), palette.resolve(commit));
    }

    @Test
    void differentChannelsAreIndependent() {
        var entries = List.of(
                new WeightedPaletteMaterial.Entry(new BlockMaterial("a"), 1),
                new WeightedPaletteMaterial.Entry(new BlockMaterial("b"), 1)
        );
        BuilderMaterial first = new WeightedPaletteMaterial(entries, 1L);
        BuilderMaterial second = new WeightedPaletteMaterial(entries, 2L);
        OperationSeed seed = new OperationSeed(5L);
        boolean differs = false;
        for (int x = 0; x < 128; x++) {
            MaterialContext context = new MaterialContext(x, 0, 0, "old", seed);
            differs |= !first.resolve(context).equals(second.resolve(context));
        }
        assertTrue(differs);
    }
}
