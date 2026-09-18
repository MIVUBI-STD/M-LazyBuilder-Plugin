package com.halokaryamedia.lazybuilder.builder.material;

import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FieldMaterialTest {
    @Test
    void conditionalMaterialUsesFieldThreshold() {
        ScalarField height = new AxisGradientField(AxisGradientField.Axis.Y, 0, 100);
        MaterialContext low = new MaterialContext(
                0, 10, 0, "old", new OperationSeed(1));
        MaterialContext high = new MaterialContext(
                0, 90, 0, "old", new OperationSeed(1));

        BuilderMaterial conditional = new ConditionalMaterial(
                height,
                0.5,
                new BlockMaterial("upper"),
                new BlockMaterial("lower"));
        assertEquals("lower", conditional.resolve(low));
        assertEquals("upper", conditional.resolve(high));
    }

    @Test
    void valueNoiseIsDeterministicAndBounded() {
        ScalarField noise = new ValueNoiseField(0.125, 44L);
        OperationSeed seed = new OperationSeed(1234L);
        for (int x = -20; x <= 20; x++) {
            MaterialContext context = new MaterialContext(x, 64, -x, "old", seed);
            double first = noise.sample(context);
            double second = noise.sample(context);
            assertEquals(first, second);
            assertTrue(first >= 0.0 && first < 1.0);
        }
    }
}
