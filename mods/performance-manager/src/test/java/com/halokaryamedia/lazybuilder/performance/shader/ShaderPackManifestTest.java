package com.halokaryamedia.lazybuilder.performance.shader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderPackManifestTest {
    @TempDir Path temp;

    @Test
    void parsesTypedOptionsAndBuildsSanitizedDefines() throws Exception {
        Files.createDirectories(temp.resolve("shaders"));
        Files.writeString(temp.resolve("shaders/terrain.vsh"), "#version 150\nvoid main(){}\n");
        Files.writeString(temp.resolve("shaders/terrain.fsh"), "#version 150\nvoid main(){}\n");
        Files.writeString(temp.resolve("shader.properties"), """
                name=Studio Pack
                author=MIVUBI
                description=Test shader
                option.shadows.type=boolean
                option.shadows.label=Shadows
                option.shadows.default=true
                option.exposure.type=float
                option.exposure.label=Exposure
                option.exposure.default=1.0
                option.exposure.min=0.5
                option.exposure.max=2.0
                option.exposure.step=0.25
                option.steps.type=int
                option.steps.default=4
                option.steps.min=1
                option.steps.max=8
                option.steps.step=1
                """);

        ShaderPackDescriptor descriptor = new ShaderPackDescriptor(
                "studio-pack",
                "Studio Pack",
                temp,
                ShaderPackDescriptor.Kind.DIRECTORY
        );
        ShaderPackManifest manifest = ShaderPackManifest.load(
                ShaderPackSource.open(descriptor),
                "Fallback"
        );

        assertEquals("Studio Pack", manifest.name());
        assertEquals("MIVUBI", manifest.author());
        assertEquals(3, manifest.options().size());

        ShaderRuntimePreferences preferences = ShaderRuntimePreferences.defaults()
                .withOption("studio-pack", "shadows", "false")
                .withOption("studio-pack", "exposure", "9.0")
                .withOption("studio-pack", "steps", "6");

        Map<String, String> defines = manifest.defines("studio-pack", preferences);
        assertEquals("0", defines.get("LB_OPT_SHADOWS"));
        assertEquals("2.0", defines.get("LB_OPT_EXPOSURE"));
        assertEquals("6", defines.get("LB_OPT_STEPS"));
    }

    @Test
    void invalidOptionDefinitionsAreIgnored() throws Exception {
        Files.createDirectories(temp.resolve("shaders"));
        Files.writeString(temp.resolve("shaders/terrain.vsh"), "#version 150\nvoid main(){}\n");
        Files.writeString(temp.resolve("shaders/terrain.fsh"), "#version 150\nvoid main(){}\n");
        Files.writeString(temp.resolve("shader.properties"), """
                option.bad.type=float
                option.bad.default=1
                option.bad.min=0
                option.bad.max=2
                option.bad.step=0
                option.good.type=boolean
                option.good.default=true
                """);

        ShaderPackDescriptor descriptor = new ShaderPackDescriptor(
                "pack",
                "Pack",
                temp,
                ShaderPackDescriptor.Kind.DIRECTORY
        );
        ShaderPackManifest manifest = ShaderPackManifest.load(
                ShaderPackSource.open(descriptor),
                "Pack"
        );

        assertEquals(1, manifest.options().size());
        assertEquals("good", manifest.options().getFirst().id());
        assertTrue(manifest.defines("pack", ShaderRuntimePreferences.defaults())
                .containsKey("LB_OPT_GOOD"));
    }
}
