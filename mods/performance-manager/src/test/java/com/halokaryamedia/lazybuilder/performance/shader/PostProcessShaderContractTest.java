package com.halokaryamedia.lazybuilder.performance.shader;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class PostProcessShaderContractTest {
    private static final String VERTEX = """
            #version 150
            void main() {
                vec2 p = vec2(
                    (gl_VertexID << 1) & 2,
                    gl_VertexID & 2
                );
                gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
            }
            """;

    private static final String FRAGMENT = """
            #version 150
            uniform sampler2D LazyBuilderColorTexture;
            out vec4 fragColor;
            void main() {
                fragColor = vec4(1.0);
            }
            """;

    @Test
    void acceptsFullscreenTriangleProgram() {
        assertDoesNotThrow(() ->
                PostProcessShaderContract.validate("final", VERTEX, FRAGMENT));
    }

    @Test
    void rejectsVertexBufferDependentPostProcess() {
        assertThrows(
                IOException.class,
                () -> PostProcessShaderContract.validate(
                        "final",
                        VERTEX.replace("void main()", "in vec3 Position;\nvoid main()"),
                        FRAGMENT
                )
        );
    }

    @Test
    void rejectsMissingFragmentOutput() {
        assertThrows(
                IOException.class,
                () -> PostProcessShaderContract.validate(
                        "final",
                        VERTEX,
                        FRAGMENT.replace("out vec4 fragColor;", "")
                )
        );
    }
}
