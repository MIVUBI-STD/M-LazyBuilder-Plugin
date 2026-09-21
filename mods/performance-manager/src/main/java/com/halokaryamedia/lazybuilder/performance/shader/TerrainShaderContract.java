package com.halokaryamedia.lazybuilder.performance.shader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Source-level compatibility gate for LazyBuilder-native Minecraft 1.21.4 terrain shaders.
 *
 * The native format intentionally uses Minecraft's stable terrain attribute/uniform
 * names so ShaderProgram.create can bind the existing terrain VertexFormat and
 * render-type uniform definitions without a second material schema.
 */
public final class TerrainShaderContract {
    private static final Pattern GBUFFER_1 = Pattern.compile(
            "layout\\s*\\(\\s*location\\s*=\\s*1\\s*\\)\\s*out\\s+vec4\\s+LazyBuilderGBuffer1\\s*;"
    );
    private static final Pattern GBUFFER_2 = Pattern.compile(
            "layout\\s*\\(\\s*location\\s*=\\s*2\\s*\\)\\s*out\\s+vec4\\s+LazyBuilderGBuffer2\\s*;"
    );

    private TerrainShaderContract() {
    }

    public static void validate(String vertexSource, String fragmentSource) throws IOException {
        String vertexCode = ShaderSourceSyntax.codeOnly(vertexSource);
        String fragmentCode = ShaderSourceSyntax.codeOnly(fragmentSource);
        List<String> missing = new ArrayList<>();

        requireVersion("vertex", vertexSource, missing);
        require(vertexSource, "in vec3 Position", "vertex attribute Position", missing);
        require(vertexSource, "in vec4 Color", "vertex attribute Color", missing);
        require(vertexSource, "in vec2 UV0", "vertex attribute UV0", missing);
        require(vertexSource, "in ivec2 UV2", "vertex attribute UV2", missing);
        require(vertexSource, "in vec3 Normal", "vertex attribute Normal", missing);
        require(vertexSource, "uniform sampler2D Sampler2", "vertex sampler Sampler2", missing);
        require(vertexSource, "uniform mat4 ModelViewMat", "vertex uniform ModelViewMat", missing);
        require(vertexSource, "uniform mat4 ProjMat", "vertex uniform ProjMat", missing);
        require(vertexSource, "uniform vec3 ModelOffset", "vertex uniform ModelOffset", missing);
        require(vertexSource, "uniform int FogShape", "vertex uniform FogShape", missing);
        require(vertexSource, "out float vertexDistance", "vertex varying vertexDistance", missing);
        require(vertexSource, "out vec4 vertexColor", "vertex varying vertexColor", missing);
        require(vertexSource, "out vec2 texCoord0", "vertex varying texCoord0", missing);

        requireVersion("fragment", fragmentSource, missing);
        require(fragmentSource, "uniform sampler2D Sampler0", "fragment sampler Sampler0", missing);
        require(fragmentSource, "uniform vec4 ColorModulator", "fragment uniform ColorModulator", missing);
        require(fragmentSource, "uniform float FogStart", "fragment uniform FogStart", missing);
        require(fragmentSource, "uniform float FogEnd", "fragment uniform FogEnd", missing);
        require(fragmentSource, "uniform vec4 FogColor", "fragment uniform FogColor", missing);
        require(fragmentSource, "in float vertexDistance", "fragment varying vertexDistance", missing);
        require(fragmentSource, "in vec4 vertexColor", "fragment varying vertexColor", missing);
        require(fragmentSource, "in vec2 texCoord0", "fragment varying texCoord0", missing);
        require(fragmentSource, "out vec4 fragColor", "fragment output fragColor", missing);

        int gbufferAttachments = gbufferAttachmentCount(fragmentCode);
        if (GBUFFER_2.matcher(fragmentCode).find()
                && gbufferAttachments < 2) {
            missing.add("GBuffer2 requires GBuffer1");
        }

        if (!missing.isEmpty()) {
            throw new IOException(
                    "Native terrain shader contract is incomplete: " + String.join(", ", missing)
            );
        }
    }

    public static int gbufferAttachmentCount(String fragmentSource) {
        String source = fragmentSource == null ? "" : fragmentSource;
        boolean first = GBUFFER_1.matcher(source).find();
        boolean second = GBUFFER_2.matcher(source).find();
        if (first && second) return 2;
        if (first) return 1;
        return 0;
    }

    private static void requireVersion(
            String stage,
            String source,
            List<String> missing
    ) {
        if (!ShaderSourceSyntax.startsWithVersion(source)) {
            missing.add(stage + " #version");
        }
    }

    private static void require(
            String source,
            String token,
            String label,
            List<String> missing
    ) {
        if (source == null || !source.contains(token)) missing.add(label);
    }
}
