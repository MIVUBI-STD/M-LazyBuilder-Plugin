package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.structure.StructureBlock;
import com.halokaryamedia.lazybuilder.builder.structure.StructureBlockEntity;
import com.halokaryamedia.lazybuilder.builder.structure.StructureSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AxiomStructureCapabilityMatrixTest {
    @Test
    void blockEntityPayloadRemainsPreserveOnlyWithoutPretendingAuthority() {
        StructureSnapshot snapshot = new StructureSnapshot(
                List.of(new StructureBlock(0, 0, 0, "minecraft:chest")),
                List.of(new StructureBlockEntity(0, 0, 0, new byte[]{1})),
                List.of(),
                List.of()
        );

        var report = AxiomStructureCapabilityMatrix.current(snapshot);
        assertEquals(
                AxiomStructureCapabilityMatrix.PayloadSupport.PRESERVE_ONLY,
                report.blockEntities());
        assertFalse(report.canApplyLosslessly());
        assertTrue(report.blockerSummary().contains("BLOCK_ENTITY"));
    }
}
