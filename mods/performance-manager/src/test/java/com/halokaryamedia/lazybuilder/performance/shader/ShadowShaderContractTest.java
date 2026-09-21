package com.halokaryamedia.lazybuilder.performance.shader;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShadowShaderContractTest {
    private static final String VERTEX = """
            #version 150
            in vec3 Position;
            uniform mat4 LazyBuilderShadowViewProjection;
            uniform vec3 LazyBuilderModelOffset;
            void main() {
                gl_Position = LazyBuilderShadowViewProjection
                        * vec4(Position + LazyBuilderModelOffset, 1.0);
            }
            """;

    private static final String FRAGMENT = """
            #version 150
            void main() {}
            """;

    @Test
    void acceptsDepthOnlyNativeShadowProgram() {
        assertDoesNotThrow(() -> ShadowShaderContract.validate(VERTEX, FRAGMENT));
    }

    @Test
    void rejectsMissingModelOffset() {
        assertThrows(
                IOException.class,
                () -> ShadowShaderContract.validate(
                        VERTEX.replace("uniform vec3 LazyBuilderModelOffset;", ""),
                        FRAGMENT
                )
        );
    }
    @Test
    void cutoutSupportRequiresExplicitCompleteContract() {
        String vertex = """
                #version 150
                #define LAZYBUILDER_CUTOUT_SHADOWS 1
                in vec3 Position;
                in vec2 UV0;
                out vec2 LazyBuilderShadowTexCoord;
                uniform mat4 LazyBuilderShadowViewProjection;
                uniform vec3 LazyBuilderModelOffset;
                void main() {
                    LazyBuilderShadowTexCoord = UV0;
                    gl_Position = LazyBuilderShadowViewProjection
                            * vec4(Position + LazyBuilderModelOffset, 1.0);
                }
                """;
        String fragment = """
                #version 150
                #define LAZYBUILDER_CUTOUT_SHADOWS 1
                in vec2 LazyBuilderShadowTexCoord;
                uniform sampler2D LazyBuilderBlockAtlas;
                uniform float LazyBuilderShadowAlphaCutoff;
                void main() {
                    if (texture(LazyBuilderBlockAtlas, LazyBuilderShadowTexCoord).a
                            < LazyBuilderShadowAlphaCutoff) discard;
                }
                """;

        assertTrue(ShadowShaderContract.supportsCutout(vertex, fragment));
        assertFalse(ShadowShaderContract.supportsCutout(
                vertex,
                fragment.replace("uniform sampler2D LazyBuilderBlockAtlas;", "")
        ));
        assertFalse(ShadowShaderContract.supportsCutout(
                vertex.replace("#define LAZYBUILDER_CUTOUT_SHADOWS 1", ""),
                fragment
        ));
    }
}
