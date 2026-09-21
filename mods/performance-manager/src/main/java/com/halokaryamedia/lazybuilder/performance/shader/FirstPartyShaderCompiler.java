package com.halokaryamedia.lazybuilder.performance.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.VertexFormats;
import org.lwjgl.opengl.GL20C;

import java.io.IOException;

/** Compiles and links LazyBuilder-native shader programs on the render thread. */
public final class FirstPartyShaderCompiler {
    private static final int MAX_LOG_CHARS = 8192;

    private FirstPartyShaderCompiler() {
    }

    public static FirstPartyShaderProgram compile(
            ShaderPackSource source,
            ShaderPipelineDefinition.Program program
    ) throws IOException, ShaderCompileException {
        RenderSystem.assertOnRenderThread();

        ShaderSourcePreprocessor.Result vertex = ShaderSourcePreprocessor.preprocess(
                source,
                program.vertexPath()
        );
        ShaderSourcePreprocessor.Result fragment = ShaderSourcePreprocessor.preprocess(
                source,
                program.fragmentPath()
        );

        if ("terrain".equals(program.name())) {
            TerrainShaderContract.validate(vertex.source(), fragment.source());
        } else if ("shadow".equals(program.name())) {
            ShadowShaderContract.validate(vertex.source(), fragment.source());
        } else if ("composite".equals(program.name()) || "final".equals(program.name())) {
            PostProcessShaderContract.validate(
                    program.name(),
                    vertex.source(),
                    fragment.source()
            );
        }

        int vertexShader = compileStage(
                program.name() + ":vertex",
                GL20C.GL_VERTEX_SHADER,
                vertex.source()
        );
        int fragmentShader = 0;
        int linkedProgram = 0;

        try {
            fragmentShader = compileStage(
                    program.name() + ":fragment",
                    GL20C.GL_FRAGMENT_SHADER,
                    fragment.source()
            );

            linkedProgram = GL20C.glCreateProgram();
            GL20C.glAttachShader(linkedProgram, vertexShader);
            GL20C.glAttachShader(linkedProgram, fragmentShader);

            if ("terrain".equals(program.name()) || "shadow".equals(program.name())) {
                VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL.bindAttributes(linkedProgram);
            }

            GL20C.glLinkProgram(linkedProgram);

            if (GL20C.glGetProgrami(linkedProgram, GL20C.GL_LINK_STATUS) == GL20C.GL_FALSE) {
                String log = trimLog(GL20C.glGetProgramInfoLog(linkedProgram));
                throw new ShaderCompileException(
                        program.name(),
                        Stage.LINK,
                        log.isBlank() ? "Shader program failed to link." : log
                );
            }

            return new FirstPartyShaderProgram(program.name(), linkedProgram);
        } catch (RuntimeException | ShaderCompileException error) {
            if (linkedProgram != 0) GL20C.glDeleteProgram(linkedProgram);
            throw error;
        } finally {
            GL20C.glDeleteShader(vertexShader);
            if (fragmentShader != 0) GL20C.glDeleteShader(fragmentShader);
        }
    }

    private static int compileStage(
            String name,
            int type,
            String source
    ) throws ShaderCompileException {
        int shader = GL20C.glCreateShader(type);
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
