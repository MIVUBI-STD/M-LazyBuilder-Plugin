package com.halokaryamedia.lazybuilder.performance.shader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderPackCatalogTest {
    @TempDir Path temp;

    @Test
    void discoversDirectoryAndZipPacksDeterministically() throws IOException {
        Path folder = temp.resolve("Builder Pack");
        Files.createDirectories(folder.resolve("shaders"));
        Files.writeString(folder.resolve("shaders/terrain.vsh"), "#version 150\n");
        Files.writeString(folder.resolve("shaders/terrain.fsh"), "#version 150\n");

        Path zip = temp.resolve("Cinematic.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("shaders/terrain.vsh"));
            output.write("#version 150\n".getBytes());
            output.closeEntry();
            output.putNextEntry(new ZipEntry("shaders/terrain.fsh"));
            output.write("#version 150\n".getBytes());
            output.closeEntry();
        }

        Files.writeString(temp.resolve("notes.txt"), "not a pack");
        try (ZipOutputStream output = new ZipOutputStream(
                Files.newOutputStream(temp.resolve("NotAShader.zip")))) {
            output.putNextEntry(new ZipEntry("readme.txt"));
            output.write("no shader stages".getBytes());
            output.closeEntry();
        }

        var packs = new ShaderPackCatalog(temp).scan();

        assertEquals(2, packs.size());
        assertEquals("Builder Pack", packs.get(0).displayName());
        assertEquals(ShaderPackDescriptor.Kind.DIRECTORY, packs.get(0).kind());
        assertEquals("Cinematic", packs.get(1).displayName());
        assertEquals(ShaderPackDescriptor.Kind.ZIP, packs.get(1).kind());
    }
    @Test
    void resolvesNormalizedIdCollisionsDeterministically() throws Exception {
        Path first = temp.resolve("My Shader");
        Path second = temp.resolve("my-shader");
        Files.createDirectories(first.resolve("shaders"));
        Files.createDirectories(second.resolve("shaders"));
        Files.writeString(first.resolve("shaders/terrain.vsh"), "#version 150\nvoid main(){}\n");
        Files.writeString(first.resolve("shaders/terrain.fsh"), "#version 150\nvoid main(){}\n");
        Files.writeString(second.resolve("shaders/terrain.vsh"), "#version 150\nvoid main(){}\n");
        Files.writeString(second.resolve("shaders/terrain.fsh"), "#version 150\nvoid main(){}\n");

        ShaderPackCatalog catalog = new ShaderPackCatalog(temp);
        var packs = catalog.scan();

        assertEquals(2, packs.size());
        assertFalse(packs.get(0).id().equals(packs.get(1).id()));
        assertTrue(packs.stream().allMatch(pack -> pack.id().startsWith("my-shader-")));
    }

    @Test
    void recordsPackLikeEntriesThatAreInvalid() throws Exception {
        Path broken = temp.resolve("BrokenPack");
        Files.createDirectories(broken.resolve("shaders"));
        Files.writeString(broken.resolve("shaders/terrain.vsh"), "#version 150\nvoid main(){}\n");

        ShaderPackCatalog catalog = new ShaderPackCatalog(temp);
        var packs = catalog.scan();

        assertTrue(packs.isEmpty());
        assertEquals(java.util.List.of("BrokenPack"), catalog.invalidEntries());
        assertTrue(catalog.lastScanError().isBlank());
    }
}
