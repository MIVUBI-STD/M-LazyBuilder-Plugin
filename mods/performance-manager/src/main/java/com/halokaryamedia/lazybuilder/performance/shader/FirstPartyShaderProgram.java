package com.halokaryamedia.lazybuilder.performance.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL20C;

/** Render-thread-owned OpenGL program created by the first-party shader compiler. */
public final class FirstPartyShaderProgram implements AutoCloseable {
    private final String name;
    private int programId;

    FirstPartyShaderProgram(String name, int programId) {
        this.name = name;
        this.programId = programId;
    }

    public String name() {
        return name;
    }

    public int programId() {
        return programId;
    }

    public boolean isClosed() {
        return programId == 0;
    }

    public void bind() {
        RenderSystem.assertOnRenderThread();
        if (programId == 0) throw new IllegalStateException("Shader program is closed: " + name);
        GL20C.glUseProgram(programId);
    }

    public static void unbind() {
        RenderSystem.assertOnRenderThread();
        GL20C.glUseProgram(0);
    }

    public int uniformLocation(String uniform) {
        RenderSystem.assertOnRenderThread();
        if (programId == 0) return -1;
        return GL20C.glGetUniformLocation(programId, uniform);
    }

    @Override
    public void close() {
        if (programId == 0) return;
        if (!RenderSystem.isOnRenderThread()) {
            int id = programId;
            programId = 0;
            RenderSystem.recordRenderCall(() -> GL20C.glDeleteProgram(id));
            return;
        }

        GL20C.glDeleteProgram(programId);
        programId = 0;
    }
}
