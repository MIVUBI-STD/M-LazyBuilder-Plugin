package com.halokaryamedia.lazybuilder.performance.shader;

import java.io.IOException;

/** Minimal native contract for LazyBuilder composite/final fullscreen-triangle programs. */
public final class PostProcessShaderContract {
    private PostProcessShaderContract() {
    }

    public static void validate(
            String program,
            String vertexSource,
            String fragmentSource
    ) throws IOException {
        String name = program == null || program.isBlank() ? "post-process" : program;

        if (vertexSource == null || !vertexSource.stripLeading().startsWith("#version")) {
            throw new IOException(name + " vertex shader is missing #version");
        }
        if (!vertexSource.contains("gl_VertexID")) {
            throw new IOException(name + " vertex shader must generate its fullscreen triangle from gl_VertexID");
        }
        if (vertexSource.contains("in vec")
                || vertexSource.contains("in ivec")
                || vertexSource.contains("in uvec")) {
            throw new IOException(name + " vertex shader must not require vertex-buffer attributes");
        }

        if (fragmentSource == null || !fragmentSource.stripLeading().startsWith("#version")) {
            throw new IOException(name + " fragment shader is missing #version");
        }
        if (!fragmentSource.contains("out vec4 fragColor")) {
            throw new IOException(name + " fragment shader must expose out vec4 fragColor");
        }
    }
}
