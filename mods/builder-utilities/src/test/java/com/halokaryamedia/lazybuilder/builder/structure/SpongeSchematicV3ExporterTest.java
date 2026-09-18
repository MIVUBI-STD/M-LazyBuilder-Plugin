package com.halokaryamedia.lazybuilder.builder.structure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpongeSchematicV3ExporterTest {
    @Test
    void varintEncoderRoundTripsThroughImporterDecoder() throws Exception {
        int[] values = {0, 1, 127, 128, 255, 16384, 999999};
        byte[] encoded = SpongeSchematicV3Exporter.encodeVarInts(values);
        assertArrayEquals(
                values,
                SpongeSchematicV3Importer.decodeVarInts(
                        encoded, values.length, 1_000_000, "test")
        );
    }

    @Test
    void varintEncoderRejectsNegativeValues() {
        assertThrows(IllegalArgumentException.class,
                () -> SpongeSchematicV3Exporter.encodeVarInts(new int[]{-1}));
    }
}
