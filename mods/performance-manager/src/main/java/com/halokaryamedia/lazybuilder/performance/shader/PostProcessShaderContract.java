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
        String vertexCode = ShaderSourceSyntax.codeOnly(vertexSource);
        String fragmentCode = ShaderSourceSyntax.codeOnly(fragmentSource);

        if (!ShaderSourceSyntax.startsWithVersion(vertexSource)) {
            throw new IOException(name + " vertex shader is missing #version");
        }
        if (!vertexCode.contains("gl_VertexID")) {
            throw new IOException(name + " vertex shader must generate its fullscreen triangle from gl_VertexID");
        }
        if (vertexCode.contains("in vec")
                || vertexCode.contains("in ivec")
                || vertexCode.contains("in uvec")) {
            throw new IOException(name + " vertex shader must not require vertex-buffer attributes");
        }

        if (!ShaderSourceSyntax.startsWithVersion(fragmentSource)) {
            throw new IOException(name + " fragment shader is missing #version");
        }
        if (!fragmentCode.contains("out vec4 fragColor")) {
            throw new IOException(name + " fragment shader must expose out vec4 fragColor");
        }
    }
}
