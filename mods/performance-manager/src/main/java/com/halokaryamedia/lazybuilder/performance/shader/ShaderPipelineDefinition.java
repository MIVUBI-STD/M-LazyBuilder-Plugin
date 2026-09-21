package com.halokaryamedia.lazybuilder.performance.shader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Discovers the deterministic LazyBuilder-native shader stage pairs in a pack. */
public final class ShaderPipelineDefinition {
    private ShaderPipelineDefinition() {
    }

    public static Result discover(ShaderPackSource source) throws IOException {
        List<Program> programs = new ArrayList<>();

        addRequired(source, programs, "terrain");
        addOptional(source, programs, "composite");
        addOptional(source, programs, "final");

        return new Result(List.copyOf(programs));
    }

    private static void addRequired(
            ShaderPackSource source,
            List<Program> programs,
            String name
    ) throws IOException {
        String vertex = path(name, "vsh");
        String fragment = path(name, "fsh");
        if (!source.exists(vertex) || !source.exists(fragment)) {
            throw new IOException("Shader pack is missing required program: " + name);
        }
        programs.add(new Program(name, vertex, fragment, true));
    }

    private static void addOptional(
            ShaderPackSource source,
            List<Program> programs,
            String name
    ) throws IOException {
        String vertex = path(name, "vsh");
        String fragment = path(name, "fsh");
        boolean hasVertex = source.exists(vertex);
        boolean hasFragment = source.exists(fragment);
        if (!hasVertex && !hasFragment) return;
        if (hasVertex != hasFragment) {
            throw new IOException("Shader program must provide both vertex and fragment source: " + name);
        }
        programs.add(new Program(name, vertex, fragment, false));
    }

    private static String path(String name, String extension) {
        return "shaders/" + name + "." + extension;
    }

    public record Program(
            String name,
            String vertexPath,
            String fragmentPath,
            boolean required
    ) {
    }

    public record Result(List<Program> programs) {
        public Result {
            programs = programs == null ? List.of() : List.copyOf(programs);
        }

        public Program terrain() {
            return programs.stream()
                    .filter(program -> "terrain".equals(program.name()))
                    .findFirst()
                    .orElseThrow();
        }
    }
}
