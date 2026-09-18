package com.halokaryamedia.lazybuilder.builder.structure;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class SpongeSchematicV3ImporterTest {
    @Test
    void decodesMultiByteVarInts() throws Exception {
        byte[] data = new byte[]{
                0,
                1,
                (byte) 0x80, 1,
                (byte) 0xff, 1
        };
        assertArrayEquals(
                new int[]{0, 1, 128, 255},
                SpongeSchematicV3Importer.decodeVarInts(data, 4, 256, "block")
        );
    }

    @Test
    void rejectsTruncatedAndTrailingVarInts() {
        assertThrows(IOException.class, () ->
                SpongeSchematicV3Importer.decodeVarInts(
                        new byte[]{(byte) 0x80}, 1, 2, "block"));

        assertThrows(IOException.class, () ->
                SpongeSchematicV3Importer.decodeVarInts(
                        new byte[]{0, 1}, 1, 2, "block"));
    }

    @Test
    void rejectsPaletteReferencesOutsidePalette() {
        assertThrows(IOException.class, () ->
                SpongeSchematicV3Importer.decodeVarInts(
                        new byte[]{2}, 1, 2, "block"));
    }
}
