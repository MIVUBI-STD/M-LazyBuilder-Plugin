package com.halokaryamedia.lazybuilder.performance.shader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ShaderPackCatalogTest {
    @TempDir Path temp;

    @Test
    void discoversDirectoryAndZipPacksDeterministically() throws IOException {
        Path folder = temp.resolve("Builder Pack");
        Files.createDirectories(folder.resolve("shaders"));
        Files.writeString(folder.resolve("shaders/terrain.vsh"), "#version 150\n");

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
}
