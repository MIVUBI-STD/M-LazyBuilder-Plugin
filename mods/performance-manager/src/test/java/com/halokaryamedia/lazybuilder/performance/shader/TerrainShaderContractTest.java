package com.halokaryamedia.lazybuilder.performance.shader;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class TerrainShaderContractTest {
    private static final String VERTEX = """
            #version 150
            in vec3 Position;
            in vec4 Color;
            in vec2 UV0;
            in ivec2 UV2;
            in vec3 Normal;
            uniform sampler2D Sampler2;
            uniform mat4 ModelViewMat;
            uniform mat4 ProjMat;
            uniform vec3 ModelOffset;
            uniform int FogShape;
            out float vertexDistance;
            out vec4 vertexColor;
            out vec2 texCoord0;
            void main() {
                vertexDistance = 0.0;
                vertexColor = Color;
                texCoord0 = UV0;
                gl_Position = ProjMat * ModelViewMat * vec4(Position + ModelOffset, 1.0);
            }
            """;

    private static final String FRAGMENT = """
            #version 150
            uniform sampler2D Sampler0;
            uniform vec4 ColorModulator;
            uniform float FogStart;
            uniform float FogEnd;
            uniform vec4 FogColor;
            in float vertexDistance;
            in vec4 vertexColor;
            in vec2 texCoord0;
            out vec4 fragColor;
            void main() {
                fragColor = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
            }
            """;

    @Test
    void acceptsMinecraftCompatibleNativeTerrainContract() {
        assertDoesNotThrow(() -> TerrainShaderContract.validate(VERTEX, FRAGMENT));
    }

    @Test
    void rejectsMissingModelOffsetContract() {
        assertThrows(
                IOException.class,
                () -> TerrainShaderContract.validate(
                        VERTEX.replace("uniform vec3 ModelOffset;", ""),
                        FRAGMENT
                )
        );
    }

    @Test
    void rejectsMismatchedFragmentVaryingContract() {
        assertThrows(
                IOException.class,
                () -> TerrainShaderContract.validate(
                        VERTEX,
                        FRAGMENT.replace("in vec2 texCoord0;", "")
                )
        );
    }

    @Test
    void detectsNativeGBufferOutputs() {
        String one = FRAGMENT + "\nlayout(location = 1) out vec4 LazyBuilderGBuffer1;\n";
        String two = one + "layout(location=2) out vec4 LazyBuilderGBuffer2;\n";

        org.junit.jupiter.api.Assertions.assertEquals(
                1,
                TerrainShaderContract.gbufferAttachmentCount(one)
        );
        org.junit.jupiter.api.Assertions.assertEquals(
                2,
                TerrainShaderContract.gbufferAttachmentCount(two)
        );
    }
}
