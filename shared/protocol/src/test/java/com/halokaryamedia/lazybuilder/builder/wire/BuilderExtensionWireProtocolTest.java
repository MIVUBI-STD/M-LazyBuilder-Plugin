package com.halokaryamedia.lazybuilder.builder.wire;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BuilderExtensionWireProtocolTest {
    @Test
    void capabilityRoundTripPreservesMaskAndLimit() throws Exception {
        var request = new BuilderExtensionWireProtocol.CapabilitiesRequest("cap-1");
        assertEquals(request,
                BuilderExtensionWireProtocol.decodeRequest(
                        BuilderExtensionWireProtocol.encodeRequest(request)));

        var response = new BuilderExtensionWireProtocol.Capabilities(
                "cap-1",
                BuilderExtensionWireProtocol.CAPABILITY_BIOME,
                128
        );
        assertEquals(response,
                BuilderExtensionWireProtocol.decodeResponse(
                        BuilderExtensionWireProtocol.encodeResponse(response)));
        assertTrue(response.supports(BuilderExtensionWireProtocol.CAPABILITY_BIOME));
        assertFalse(response.supports(
                BuilderExtensionWireProtocol.CAPABILITY_BLOCK_ENTITY));
    }

    @Test
    void biomeBatchRoundTripPreservesCompareAndSetStates() throws Exception {
        var batch = new BuilderExtensionWireProtocol.ApplyBiomeBatch(
                "operation",
                "minecraft:overworld",
                List.of(
                        new BuilderExtensionWireProtocol.BiomeMutation(
                                1, 64, -2,
                                "minecraft:plains",
                                "minecraft:forest")
                )
        );
        assertEquals(batch,
                BuilderExtensionWireProtocol.decodeRequest(
                        BuilderExtensionWireProtocol.encodeRequest(batch)));

        var conflict = BuilderExtensionWireProtocol.BatchResult.conflict(
                "operation", 0, 0, "minecraft:desert");
        assertEquals(conflict,
                BuilderExtensionWireProtocol.decodeResponse(
                        BuilderExtensionWireProtocol.encodeResponse(conflict)));
    }

    @Test
    void rejectsOversizedBatchAtConstructionBoundary() {
        var entry = new BuilderExtensionWireProtocol.BiomeMutation(
                0, 0, 0, "minecraft:plains", "minecraft:forest");
        var entries = java.util.Collections.nCopies(
                BuilderExtensionWireProtocol.MAX_BATCH_ENTRIES + 1, entry);
        assertThrows(IllegalArgumentException.class, () ->
                new BuilderExtensionWireProtocol.ApplyBiomeBatch(
                        "too-large", "minecraft:overworld", entries));
    }
    @Test
    void blockEntityBatchRoundTripsBoundedBinaryPayloads() throws Exception {
        var mutation = new BuilderExtensionWireProtocol.BlockEntityMutation(
                -12, 64, 33,
                "minecraft:air",
                "minecraft:chest[facing=north,type=single,waterlogged=false]",
                new byte[0],
                new byte[]{10, 0, 0, 0}
        );
        var request = new BuilderExtensionWireProtocol.ApplyBlockEntityBatch(
                "be-op",
                "minecraft:overworld",
                java.util.List.of(mutation)
        );

        var decoded = (BuilderExtensionWireProtocol.ApplyBlockEntityBatch)
                BuilderExtensionWireProtocol.decodeRequest(
                        BuilderExtensionWireProtocol.encodeRequest(request));
        assertEquals(request.operationId(), decoded.operationId());
        assertEquals(request.dimensionId(), decoded.dimensionId());
        assertEquals(1, decoded.entries().size());
        assertArrayEquals(mutation.afterNbt(), decoded.entries().get(0).afterNbt());

        var response = BuilderExtensionWireProtocol.BlockEntityBatchResult.conflict(
                "be-op", 0, 0, "block entity mismatch");
        var decodedResponse = (BuilderExtensionWireProtocol.BlockEntityBatchResult)
                BuilderExtensionWireProtocol.decodeResponse(
                        BuilderExtensionWireProtocol.encodeResponse(response));
        assertEquals(response, decodedResponse);
    }

}
