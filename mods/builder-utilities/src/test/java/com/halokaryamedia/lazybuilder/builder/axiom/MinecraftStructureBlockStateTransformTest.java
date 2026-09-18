package com.halokaryamedia.lazybuilder.builder.axiom;

import net.minecraft.util.BlockRotation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinecraftStructureBlockStateTransformTest {
    @Test
    void quarterTurnsMapToMinecraftRotations() {
        assertEquals(BlockRotation.NONE, MinecraftStructureBlockStateTransform.rotation(0));
        assertEquals(BlockRotation.CLOCKWISE_90, MinecraftStructureBlockStateTransform.rotation(1));
        assertEquals(BlockRotation.CLOCKWISE_180, MinecraftStructureBlockStateTransform.rotation(2));
        assertEquals(BlockRotation.COUNTERCLOCKWISE_90, MinecraftStructureBlockStateTransform.rotation(3));
        assertEquals(BlockRotation.COUNTERCLOCKWISE_90, MinecraftStructureBlockStateTransform.rotation(-1));
    }
}
