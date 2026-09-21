package com.halokaryamedia.lazybuilder.performance.shader;

import com.mojang.blaze3d.systems.RenderSystem;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Atomic set of compiled first-party programs.
 *
 * A new pipeline is published only after every declared program compiles and links;
 * the previous pipeline remains usable when a reload fails.
 */
public final class FirstPartyShaderPipeline implements AutoCloseable {
    private final Map<String, FirstPartyShaderProgram> programs;
    private final int gbufferAttachments;

    private FirstPartyShaderPipeline(
            Map<String, FirstPartyShaderProgram> programs,
            int gbufferAttachments
    ) {
        this.programs = Map.copyOf(programs);
        this.gbufferAttachments = Math.max(0, Math.min(2, gbufferAttachments));
    }

    public static FirstPartyShaderPipeline compile(ShaderPackSource source)
            throws IOException, FirstPartyShaderCompiler.ShaderCompileException {
        RenderSystem.assertOnRenderThread();

        ShaderPipelineDefinition.Result definition = ShaderPipelineDefinition.discover(source);
        String terrainFragment = ShaderSourcePreprocessor.preprocess(
                source,
                definition.terrain().fragmentPath()
        ).source();
        int gbufferAttachments = TerrainShaderContract.gbufferAttachmentCount(terrainFragment);
        Map<String, FirstPartyShaderProgram> compiled = new LinkedHashMap<>();

        try {
            for (ShaderPipelineDefinition.Program program : definition.programs()) {
                compiled.put(
                        program.name(),
                        FirstPartyShaderCompiler.compile(source, program)
                );
            }
            return new FirstPartyShaderPipeline(compiled, gbufferAttachments);
        } catch (IOException | FirstPartyShaderCompiler.ShaderCompileException | RuntimeException error) {
            for (FirstPartyShaderProgram program : compiled.values()) {
                program.close();
            }
            throw error;
        }
    }

    public FirstPartyShaderProgram program(String name) {
        return programs.get(name);
    }

    public FirstPartyShaderProgram terrain() {
        FirstPartyShaderProgram terrain = programs.get("terrain");
        if (terrain == null) throw new IllegalStateException("Terrain shader program is unavailable.");
        return terrain;
    }

    public boolean has(String name) {
        return programs.containsKey(name);
    }

    public int gbufferAttachments() {
        return gbufferAttachments;
    }

    @Override
    public void close() {
        for (FirstPartyShaderProgram program : programs.values()) {
            program.close();
        }
    }
}
