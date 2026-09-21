package com.halokaryamedia.lazybuilder.performance.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.VertexFormats;
import org.lwjgl.opengl.GL20C;

import java.io.IOException;
import java.util.Map;

/** Compiles and links LazyBuilder-native shader programs on the render thread. */
public final class FirstPartyShaderCompiler {
    private static final int MAX_LOG_CHARS = 8192;

    private FirstPartyShaderCompiler() {
    }

    public static FirstPartyShaderProgram compile(
            ShaderPackSource source,
            ShaderPipelineDefinition.Program program
    ) throws IOException, ShaderCompileException {
        return compile(source, program, Map.of());
    }

    public static FirstPartyShaderProgram compile(
            ShaderPackSource source,
            ShaderPipelineDefinition.Program program,
            Map<String, String> defines
    ) throws IOException, ShaderCompileException {
        ShaderSourcePreprocessor.Result vertex = ShaderSourcePreprocessor.preprocess(
                source,
                program.vertexPath(),
                defines
        );
        ShaderSourcePreprocessor.Result fragment = ShaderSourcePreprocessor.preprocess(
                source,
                program.fragmentPath(),
                defines
        );
        validate(program.name(), vertex.source(), fragment.source());
        return compilePrepared(program.name(), vertex.source(), fragment.source());
    }

    static void validate(
            String programName,
            String vertexSource,
            String fragmentSource
    ) throws IOException {
        if ("terrain".equals(programName)) {
            TerrainShaderContract.validate(vertexSource, fragmentSource);
        } else if ("shadow".equals(programName)) {
            ShadowShaderContract.validate(vertexSource, fragmentSource);
        } else if ("composite".equals(programName) || "final".equals(programName)) {
            PostProcessShaderContract.validate(
                    programName,
                    vertexSource,
                    fragmentSource
            );
        }
    }

    static FirstPartyShaderProgram compilePrepared(
            String programName,
            String vertexSource,
            String fragmentSource
    ) throws ShaderCompileException {
        RenderSystem.assertOnRenderThread();

        int vertexShader = compileStage(
                programName + ":vertex",
                GL20C.GL_VERTEX_SHADER,
                vertexSource
        );
        int fragmentShader = 0;
        int linkedProgram = 0;

        try {
            fragmentShader = compileStage(
                    programName + ":fragment",
                    GL20C.GL_FRAGMENT_SHADER,
                    fragmentSource
            );

            linkedProgram = GL20C.glCreateProgram();
            if (linkedProgram == 0) {
                throw new ShaderCompileException(
                        programName,
                        Stage.LINK,
                        "OpenGL could not allocate a shader program."
                );
            }
            GL20C.glAttachShader(linkedProgram, vertexShader);
            GL20C.glAttachShader(linkedProgram, fragmentShader);

            if ("terrain".equals(programName) || "shadow".equals(programName)) {
                VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL.bindAttributes(linkedProgram);
            }

            GL20C.glLinkProgram(linkedProgram);

            if (GL20C.glGetProgrami(linkedProgram, GL20C.GL_LINK_STATUS) == GL20C.GL_FALSE) {
                String log = trimLog(GL20C.glGetProgramInfoLog(linkedProgram));
                throw new ShaderCompileException(
                        programName,
                        Stage.LINK,
                        log.isBlank() ? "Shader program failed to link." : log
                );
            }

            FirstPartyShaderProgram result = new FirstPartyShaderProgram(programName, linkedProgram);
            prewarmUniforms(result, programName);
            return result;
        } catch (RuntimeException | ShaderCompileException error) {
            if (linkedProgram != 0) GL20C.glDeleteProgram(linkedProgram);
            throw error;
        } finally {
            GL20C.glDeleteShader(vertexShader);
            if (fragmentShader != 0) GL20C.glDeleteShader(fragmentShader);
        }
    }

    private static void prewarmUniforms(
            FirstPartyShaderProgram program,
            String name
    ) {
        if (program == null || name == null) return;

        switch (name) {
            case "shadow" -> program.prewarmUniforms(
                    "LazyBuilderShadowViewProjection",
                    "LazyBuilderModelOffset",
                    "LazyBuilderBlockAtlas",
                    "LazyBuilderShadowAlphaCutoff"
            );
            case "composite", "final" -> program.prewarmUniforms(
                    "LazyBuilderColorTexture",
                    "LazyBuilderDepthTexture",
                    "LazyBuilderGBuffer1",
                    "LazyBuilderGBuffer2",
                    "LazyBuilderShadowTexture",
                    "LazyBuilderShadowViewProjection",
                    "LazyBuilderShadowCenter",
                    "LazyBuilderShadowResolution",
                    "LazyBuilderShadowReady",
                    "LazyBuilderInverseViewProjection",
                    "LazyBuilderCameraPosition",
                    "LazyBuilderResolution",
                    "LazyBuilderTime"
            );
            default -> {
                // Terrain source is linked through Minecraft's ShaderProgram path.
            }
        }
    }

    private static int compileStage(
            String name,
            int type,
            String source
    ) throws ShaderCompileException {
        int shader = GL20C.glCreateShader(type);
        if (shader == 0) {
            throw new ShaderCompileException(
                    name,
                    type == GL20C.GL_VERTEX_SHADER ? Stage.VERTEX : Stage.FRAGMENT,
                    "OpenGL could not allocate a shader object."
            );
        }
        GL20C.glShaderSource(shader, source);
        GL20C.glCompileShader(shader);

        if (GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) == GL20C.GL_FALSE) {
            String log = trimLog(GL20C.glGetShaderInfoLog(shader));
            GL20C.glDeleteShader(shader);
            throw new ShaderCompileException(
                    name,
                    type == GL20C.GL_VERTEX_SHADER ? Stage.VERTEX : Stage.FRAGMENT,
                    log.isBlank() ? "Shader source failed to compile." : log
            );
        }
        return shader;
    }

    private static String trimLog(String log) {
        if (log == null) return "";
        return log.length() <= MAX_LOG_CHARS ? log : log.substring(0, MAX_LOG_CHARS);
    }

    public enum Stage {
        VERTEX,
        FRAGMENT,
        LINK
    }

    public static final class ShaderCompileException extends Exception {
        private final String program;
        private final Stage stage;

        ShaderCompileException(String program, Stage stage, String message) {
            super(message);
            this.program = program;
            this.stage = stage;
        }

        public String program() {
            return program;
        }

        public Stage stage() {
            return stage;
        }
    }
}
