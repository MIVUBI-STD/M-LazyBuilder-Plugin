package com.halokaryamedia.lazybuilder.performance.shader;

import java.io.IOException;

/** Native depth-only shadow program contract for Minecraft terrain geometry. */
public final class ShadowShaderContract {
    private ShadowShaderContract() {
    }

    public static void validate(String vertexSource, String fragmentSource) throws IOException {
        String vertexCode = ShaderSourceSyntax.codeOnly(vertexSource);
        String fragmentCode = ShaderSourceSyntax.codeOnly(fragmentSource);
        if (!ShaderSourceSyntax.startsWithVersion(vertexSource)) {
            throw new IOException("shadow vertex shader is missing #version");
        }
        require(vertexSource, "in vec3 Position", "shadow vertex attribute Position");
        require(
                vertexSource,
                "uniform mat4 LazyBuilderShadowViewProjection",
                "shadow uniform LazyBuilderShadowViewProjection"
        );
        require(
                vertexSource,
                "uniform vec3 LazyBuilderModelOffset",
                "shadow uniform LazyBuilderModelOffset"
        );

        if (!ShaderSourceSyntax.startsWithVersion(fragmentSource)) {
            throw new IOException("shadow fragment shader is missing #version");
        }
        if (!fragmentCode.contains("void main")) {
            throw new IOException("shadow fragment shader must provide main()");
        }
    }

    public static boolean supportsCutout(
            String vertexSource,
            String fragmentSource
    ) {
        String vertex = ShaderSourceSyntax.codeOnly(vertexSource);
        String fragment = ShaderSourceSyntax.codeOnly(fragmentSource);
        return (vertexSource != null && vertexSource.contains("#define LAZYBUILDER_CUTOUT_SHADOWS 1"))
                && (fragmentSource != null && fragmentSource.contains("#define LAZYBUILDER_CUTOUT_SHADOWS 1"))
                && vertex.contains("in vec2 UV0")
                && vertex.contains("out vec2 LazyBuilderShadowTexCoord")
                && fragment.contains("in vec2 LazyBuilderShadowTexCoord")
                && fragment.contains("uniform sampler2D LazyBuilderBlockAtlas")
                && fragment.contains("uniform float LazyBuilderShadowAlphaCutoff");
    }

    private static void require(String source, String token, String label) throws IOException {
        if (!source.contains(token)) throw new IOException("Native " + label + " is missing");
    }
}
