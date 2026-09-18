package com.halokaryamedia.lazybuilder.builder.structure;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SchematicDataVersionAuxiliaryPolicyTest {
    @Test
    void legacyAuxiliaryPayloadsRemainExplicitlyNonMigrated() {
        int current = SchematicDataVersionPolicy.currentDataVersion();
        if (current == 0) return;

        SpongeSchematicImport imported = new SpongeSchematicImport(
                new StructureSnapshot(
                        List.of(new StructureBlock(0, 0, 0, "minecraft:stone")),
                        List.of(),
                        List.of(new StructureBiomeSample(
                                0, 0, 0,
                                "minecraft:plains".getBytes(java.nio.charset.StandardCharsets.UTF_8))),
                        List.of()
                ),
                0, 0, 0, current - 1
        );

        assertEquals(
                SchematicDataVersionPolicy.Compatibility.LEGACY_REQUIRES_VALIDATION,
                SchematicDataVersionPolicy.classify(imported.dataVersion()));
        assertTrue(imported.snapshot().biomeCount() > 0);
    }
}
