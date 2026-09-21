package com.halokaryamedia.lazybuilder.performance.shader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderSourcePreprocessorTest {
    @TempDir Path temp;

    @Test
    void expandsRelativeIncludesInsidePack() throws IOException {
        Path pack = temp.resolve("pack");
        Files.createDirectories(pack.resolve("shaders/lib"));
        Files.writeString(
                pack.resolve("shaders/main.vsh"),
                "#version 150\n#include \"lib/common.glsl\"\nvoid main() {}\n"
        );
        Files.writeString(
                pack.resolve("shaders/lib/common.glsl"),
                "vec3 lazybuilder_test() { return vec3(1.0); }\n"
        );

        ShaderPackDescriptor descriptor = new ShaderPackDescriptor(
                "pack",
                "Pack",
                pack,
                ShaderPackDescriptor.Kind.DIRECTORY
        );

        ShaderSourcePreprocessor.Result result = ShaderSourcePreprocessor.preprocess(
                ShaderPackSource.open(descriptor),
                "shaders/main.vsh"
        );

        assertTrue(result.source().contains("lazybuilder_test"));
        assertTrue(result.dependencies().contains("shaders/main.vsh"));
        assertTrue(result.dependencies().contains("shaders/lib/common.glsl"));
    }

    @Test
    void rejectsPathTraversal() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ShaderPackSource.normalizeRelativePath("../outside.glsl")
        );
    }

    @Test
    void rejectsIncludeCycles() throws IOException {
        Path pack = temp.resolve("cycle");
        Files.createDirectories(pack.resolve("shaders"));
        Files.writeString(pack.resolve("shaders/a.glsl"), "#include \"b.glsl\"\n");
        Files.writeString(pack.resolve("shaders/b.glsl"), "#include \"a.glsl\"\n");

        ShaderPackDescriptor descriptor = new ShaderPackDescriptor(
                "cycle",
                "Cycle",
                pack,
                ShaderPackDescriptor.Kind.DIRECTORY
        );

        assertThrows(
                IOException.class,
                () -> ShaderSourcePreprocessor.preprocess(
                        ShaderPackSource.open(descriptor),
                        "shaders/a.glsl"
                )
        );
    }
    @Test
    void injectsDefinesImmediatelyAfterVersionInStableOrder() throws Exception {
        Path pack = temp.resolve("define-pack");
        Files.createDirectories(pack.resolve("shaders"));
        Files.writeString(
                pack.resolve("shaders/terrain.vsh"),
                "#version 150\nfloat a = float(LB_OPT_ALPHA);\nvoid main(){}\n"
        );

        ShaderPackDescriptor descriptor = new ShaderPackDescriptor(
                "define-pack",
                "Define Pack",
                pack,
                ShaderPackDescriptor.Kind.DIRECTORY
        );

        ShaderSourcePreprocessor.Result result = ShaderSourcePreprocessor.preprocess(
                ShaderPackSource.open(descriptor),
                "shaders/terrain.vsh",
                java.util.Map.of(
                        "LB_OPT_ZETA", "2",
                        "LB_OPT_ALPHA", "1"
                )
        );

        String source = result.source();
        int version = source.indexOf("#version 150");
        int alpha = source.indexOf("#define LB_OPT_ALPHA 1");
        int zeta = source.indexOf("#define LB_OPT_ZETA 2");
        int body = source.indexOf("void main");

        assertTrue(version >= 0);
        assertTrue(alpha > version);
        assertTrue(zeta < 0);
        assertTrue(body > alpha);
    }
}
