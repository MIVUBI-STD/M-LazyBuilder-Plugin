package com.halokaryamedia.lazybuilder.utilities;

import com.halokaryamedia.lazybuilder.builder.wire.BuilderExtensionWireProtocol;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PaperBuilderExtensionPayloadAdapterTest {
    @Test
    void blockEntityRollbackMutationSwapsBeforeAndAfterExactly() {
        var original = new BuilderExtensionWireProtocol.BlockEntityMutation(
                1, 64, -2,
                "minecraft:chest[facing=north,type=single,waterlogged=false]",
                "minecraft:barrel[facing=up,open=false]",
                new byte[]{1, 2, 3},
                new byte[]{4, 5, 6}
        );

        var reverse = PaperBuilderExtensionPayloadAdapter.reverseBlockEntityMutation(original);
        assertEquals(original.x(), reverse.x());
        assertEquals(original.y(), reverse.y());
        assertEquals(original.z(), reverse.z());
        assertEquals(original.afterBlockState(), reverse.beforeBlockState());
        assertEquals(original.beforeBlockState(), reverse.afterBlockState());
        assertArrayEquals(original.afterNbt(), reverse.beforeNbt());
        assertArrayEquals(original.beforeNbt(), reverse.afterNbt());
    }

}
