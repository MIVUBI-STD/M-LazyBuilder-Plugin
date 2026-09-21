package com.halokaryamedia.lazybuilder.performance.shader;

import com.mojang.blaze3d.systems.RenderSystem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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
    private final String terrainSourceFingerprint;

    private FirstPartyShaderPipeline(
            Map<String, FirstPartyShaderProgram> programs,
            int gbufferAttachments,
            String terrainSourceFingerprint
    ) {
        this.programs = Map.copyOf(programs);
        this.gbufferAttachments = Math.max(0, Math.min(2, gbufferAttachments));
        this.terrainSourceFingerprint = terrainSourceFingerprint == null ? "" : terrainSourceFingerprint;
    }

    public static FirstPartyShaderPipeline compile(ShaderPackSource source)
            throws IOException, FirstPartyShaderCompiler.ShaderCompileException {
        return compile(source, Map.of());
    }

    public static FirstPartyShaderPipeline compile(
            ShaderPackSource source,
            Map<String, String> defines
    ) throws IOException, FirstPartyShaderCompiler.ShaderCompileException {
        return compile(prepare(source, defines));
    }

    public static Prepared prepare(
            ShaderPackSource source,
            Map<String, String> defines
    ) throws IOException {
        ShaderPipelineDefinition.Result definition = ShaderPipelineDefinition.discover(source);
        Map<String, PreparedProgram> programs = new LinkedHashMap<>();

        for (ShaderPipelineDefinition.Program program : definition.programs()) {
            String vertex = ShaderSourcePreprocessor.preprocess(
                    source,
                    program.vertexPath(),
                    defines
            ).source();
            String fragment = ShaderSourcePreprocessor.preprocess(
                    source,
                    program.fragmentPath(),
                    defines
            ).source();
            FirstPartyShaderCompiler.validate(program.name(), vertex, fragment);
            programs.put(
                    program.name(),
                    new PreparedProgram(program.name(), vertex, fragment)
            );
        }

        PreparedProgram terrain = programs.get("terrain");
        if (terrain == null) throw new IOException("Prepared terrain shader is unavailable.");

        return new Prepared(
                Map.copyOf(programs),
                TerrainShaderContract.gbufferAttachmentCount(terrain.fragmentSource()),
                fingerprint(terrain.vertexSource(), terrain.fragmentSource())
        );
    }

    public static FirstPartyShaderPipeline compile(Prepared prepared)
            throws FirstPartyShaderCompiler.ShaderCompileException {
        RenderSystem.assertOnRenderThread();
        if (prepared == null) throw new IllegalArgumentException("Missing prepared shader pipeline");

        Map<String, FirstPartyShaderProgram> compiled = new LinkedHashMap<>();
        try {
            for (PreparedProgram program : prepared.programs().values()) {
                compiled.put(
                        program.name(),
                        FirstPartyShaderCompiler.compilePrepared(
                                program.name(),
                                program.vertexSource(),
                                program.fragmentSource()
                        )
                );
            }
            return new FirstPartyShaderPipeline(
                    compiled,
                    prepared.gbufferAttachments(),
                    prepared.terrainSourceFingerprint()
            );
        } catch (FirstPartyShaderCompiler.ShaderCompileException | RuntimeException error) {
            for (FirstPartyShaderProgram program : compiled.values()) {
                program.close();
            }
            throw error;
        }
    }

    public record Prepared(
            Map<String, PreparedProgram> programs,
            int gbufferAttachments,
            String terrainSourceFingerprint
    ) {
        public Prepared {
            programs = programs == null ? Map.of() : Map.copyOf(programs);
            terrainSourceFingerprint = terrainSourceFingerprint == null
                    ? ""
                    : terrainSourceFingerprint;
        }
    }

    public record PreparedProgram(
            String name,
            String vertexSource,
            String fragmentSource
    ) {
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

    public String terrainSourceFingerprint() {
        return terrainSourceFingerprint;
    }

    public int gbufferAttachments() {
        return gbufferAttachments;
    }

    private static String fingerprint(String vertex, String fragment) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(vertex.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(fragment.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    @Override
    public void close() {
        for (FirstPartyShaderProgram program : programs.values()) {
            program.close();
        }
    }
}
