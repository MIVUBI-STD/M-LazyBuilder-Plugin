package com.halokaryamedia.lazybuilder.performance.rendering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TerrainShaderSourceTransformerTest {
    private static final String SAMPLE = "#version 150\n"
            + "in vec3 Position;\n"
            + "uniform vec3 ModelOffset;\n"
            + "void main() {\n"
            + "    vec3 pos = Position + ModelOffset;\n"
            + "    if (ModelOffset.x > 0.0) pos.x += 1.0;\n"
            + "}\n";

    @Test
    void augmentsOnlyExpectedModelOffsetContract() {
        String transformed = TerrainShaderSourceTransformer.transformSource(true, SAMPLE);

        assertTrue(transformed.contains("#extension GL_ARB_shader_draw_parameters : enable"));
        assertTrue(transformed.contains("uniform LazyBuilderDrawTransforms"));
        assertTrue(transformed.contains("uniform int LazyBuilderDrawBase;"));
        assertTrue(transformed.contains("uniform int LazyBuilderMultiDrawEnabled;"));
        assertTrue(transformed.contains("Position + lazybuilder_model_offset()"));
        assertTrue(transformed.contains("lazybuilder_model_offset().x"));
        assertTrue(transformed.contains("return ModelOffset;"));
    }

    @Test
    void customResourcePackGateLeavesSourceUntouched() {
        assertSame(SAMPLE, TerrainShaderSourceTransformer.transformSource(false, SAMPLE));
    }

    @Test
    void alreadyAugmentedOrUnexpectedSourceIsIdempotent() {
        String transformed = TerrainShaderSourceTransformer.transformSource(true, SAMPLE);
        assertEquals(transformed, TerrainShaderSourceTransformer.transformSource(true, transformed));
        assertEquals("#version 150\nvoid main() {}\n",
                TerrainShaderSourceTransformer.transformSource(true, "#version 150\nvoid main() {}\n"));
    }
}
