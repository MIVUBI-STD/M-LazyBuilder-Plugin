package com.halokaryamedia.lazybuilder.performance.shader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderPipelineDefinitionTest {
    @TempDir Path temp;

    @Test
    void terrainIsRequiredAndCompositeFinalAreOptional() throws IOException {
        Path pack = temp.resolve("pack");
        Files.createDirectories(pack.resolve("shaders"));
        Files.writeString(pack.resolve("shaders/terrain.vsh"), "#version 150\n");
        Files.writeString(pack.resolve("shaders/terrain.fsh"), "#version 150\n");
        Files.writeString(pack.resolve("shaders/final.vsh"), "#version 150\n");
        Files.writeString(pack.resolve("shaders/final.fsh"), "#version 150\n");

        ShaderPackSource source = ShaderPackSource.open(new ShaderPackDescriptor(
                "pack", "Pack", pack, ShaderPackDescriptor.Kind.DIRECTORY
        ));

        ShaderPipelineDefinition.Result result = ShaderPipelineDefinition.discover(source);

        assertEquals(2, result.programs().size());
        assertEquals("terrain", result.terrain().name());
        assertTrue(result.programs().stream().anyMatch(program -> "final".equals(program.name())));
    }

    @Test
    void rejectsHalfDefinedOptionalProgram() throws IOException {
        Path pack = temp.resolve("broken");
        Files.createDirectories(pack.resolve("shaders"));
        Files.writeString(pack.resolve("shaders/terrain.vsh"), "#version 150\n");
        Files.writeString(pack.resolve("shaders/terrain.fsh"), "#version 150\n");
        Files.writeString(pack.resolve("shaders/composite.vsh"), "#version 150\n");

        ShaderPackSource source = ShaderPackSource.open(new ShaderPackDescriptor(
                "broken", "Broken", pack, ShaderPackDescriptor.Kind.DIRECTORY
        ));

        assertThrows(IOException.class, () -> ShaderPipelineDefinition.discover(source));
    }
}
