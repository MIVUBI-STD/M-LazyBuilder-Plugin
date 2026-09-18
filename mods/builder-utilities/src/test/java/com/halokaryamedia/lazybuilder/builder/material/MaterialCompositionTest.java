package com.halokaryamedia.lazybuilder.builder.material;

import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaterialCompositionTest {
    @Test
    void existingMaterialAndMasksComposeWithoutChangingOwnership() {
        MaterialContext stone = new MaterialContext(1, 64, 2, "minecraft:stone", new OperationSeed(1));
        MaterialContext dirt = new MaterialContext(1, 64, 2, "minecraft:dirt", new OperationSeed(1));
        assertEquals("minecraft:stone", ExistingBlockMaterial.INSTANCE.resolve(stone));

        MaterialMask replaceable = MaterialMasks.existingStates(Set.of("minecraft:stone", "minecraft:andesite"));
        MaterialMask high = MaterialMasks.fieldAtOrAbove(new AxisGradientField(AxisGradientField.Axis.Y, 0, 100), 0.5);
        MaterialMask combined = MaterialMasks.and(replaceable, high);
        assertTrue(combined.test(stone));
        assertFalse(combined.test(dirt));
        assertTrue(MaterialMasks.not(combined).test(dirt));
    }

    @Test
    void fieldBlendIsDeterministicAndHonorsEndpoints() {
        MaterialContext context = new MaterialContext(4, 5, 6, "old", new OperationSeed(99));
        BuilderMaterial low = new BlockMaterial("low");
        BuilderMaterial high = new BlockMaterial("high");
        assertEquals("low", new FieldBlendMaterial(c -> 0.0, low, high, 7).resolve(context));
        assertEquals("high", new FieldBlendMaterial(c -> 1.0, low, high, 7).resolve(context));

        BuilderMaterial mixed = new FieldBlendMaterial(c -> 0.5, low, high, 7);
        assertEquals(mixed.resolve(context), mixed.resolve(context));
    }
}
