package com.halokaryamedia.lazybuilder.performance.shader;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
