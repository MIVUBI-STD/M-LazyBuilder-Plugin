package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.structure.StructureBlock;
import com.halokaryamedia.lazybuilder.builder.structure.StructureBlockEntity;
import com.halokaryamedia.lazybuilder.builder.structure.StructureSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AxiomLossyBlockEntityApplyTest {
    @Test
    void explicitStripCreatesNewSnapshotWithoutMutatingSource() {
        StructureSnapshot source = new StructureSnapshot(
                List.of(new StructureBlock(0, 0, 0, "minecraft:chest")),
                List.of(new StructureBlockEntity(0, 0, 0, new byte[]{1})),
                List.of(),
                List.of()
        );

        StructureSnapshot stripped =
                AxiomStructureAuxiliary.snapshotForApply(source, true);

        assertEquals(1, source.blockEntityCount());
        assertEquals(0, stripped.blockEntityCount());
        assertEquals(source.blocks(), stripped.blocks());
    }
}
