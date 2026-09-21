package com.halokaryamedia.lazybuilder.performance.shader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ShaderPackSourceSessionTest {
    @TempDir Path temp;

    @Test
    void directorySessionCachesSourceForOnePreparationGeneration() throws Exception {
        Path pack = temp.resolve("pack");
        Files.createDirectories(pack.resolve("shaders"));
        Path source = pack.resolve("shaders/common.glsl");
        Files.writeString(source, "const int VALUE = 1;\n");

        ShaderPackDescriptor descriptor = new ShaderPackDescriptor(
                "pack",
                "Pack",
                pack,
                ShaderPackDescriptor.Kind.DIRECTORY
        );

        try (ShaderPackSource.Session session = ShaderPackSource.openSession(descriptor)) {
            assertEquals(
                    "const int VALUE = 1;\n",
                    session.readText("shaders/common.glsl")
            );

            Files.writeString(source, "const int VALUE = 2;\n");

            assertEquals(
                    "const int VALUE = 1;\n",
                    session.readText("shaders/common.glsl"),
                    "a single preparation session must see one stable source snapshot"
            );
        }

        try (ShaderPackSource.Session session = ShaderPackSource.openSession(descriptor)) {
            assertEquals(
                    "const int VALUE = 2;\n",
                    session.readText("shaders/common.glsl"),
                    "a new preparation generation should see the updated file"
            );
        }
    }
}
