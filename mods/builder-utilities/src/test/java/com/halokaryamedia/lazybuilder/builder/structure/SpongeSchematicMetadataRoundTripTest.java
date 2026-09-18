package com.halokaryamedia.lazybuilder.builder.structure;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpongeSchematicMetadataRoundTripTest {
    @TempDir Path tempDir;

    @Test
    void preservesOpaqueMetadataCompoundAcrossExportImport() throws Exception {
        NbtCompound metadata = new NbtCompound();
        metadata.putString("Name", "Builder Test");
        metadata.putString("CustomVendorField", "keep-me");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            NbtIo.writeCompound(metadata, output);
        }

        SpongeSchematicImport original = new SpongeSchematicImport(
                new StructureSnapshot(
                        List.of(new StructureBlock(0, 0, 0, "minecraft:stone")),
                        List.of(), List.of(), List.of()),
                1, 2, 3, 4189, bytes.toByteArray());

        Path file = tempDir.resolve("metadata.schem");
        SpongeSchematicV3Exporter.writeCompressed(original, file);
        SpongeSchematicImport decoded = SpongeSchematicV3Importer.importCompressed(file);

        assertTrue(decoded.hasMetadata());
        assertArrayEquals(original.metadataPayload(), decoded.metadataPayload());
        assertEquals(1, decoded.offsetX());
        assertEquals(2, decoded.offsetY());
        assertEquals(3, decoded.offsetZ());
    }
}
