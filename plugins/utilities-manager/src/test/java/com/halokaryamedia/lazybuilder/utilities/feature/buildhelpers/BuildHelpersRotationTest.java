package com.halokaryamedia.lazybuilder.utilities.feature.buildhelpers;

import org.bukkit.GameMode;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BuildHelpersRotationTest {
    @Test
    void rotatesCardinalFacesClockwise() {
        assertEquals(BlockFace.EAST, BuildHelpersFeature.clockwise(BlockFace.NORTH));
        assertEquals(BlockFace.SOUTH, BuildHelpersFeature.clockwise(BlockFace.EAST));
        assertEquals(BlockFace.WEST, BuildHelpersFeature.clockwise(BlockFace.SOUTH));
        assertEquals(BlockFace.NORTH, BuildHelpersFeature.clockwise(BlockFace.WEST));
    }

    @Test
    void leavesUnsupportedFacesUnchanged() {
        assertEquals(BlockFace.UP, BuildHelpersFeature.clockwise(BlockFace.UP));
        assertEquals(BlockFace.DOWN, BuildHelpersFeature.clockwise(BlockFace.DOWN));
    }

    @Test
    void interactiveHelpersAcceptOnlyMainHand() {
        assertTrue(BuildHelpersFeature.acceptsHand(EquipmentSlot.HAND));
        assertFalse(BuildHelpersFeature.acceptsHand(EquipmentSlot.OFF_HAND));
    }

    @Test
    void creativeDoubleSlabDoesNotCreateDrops() {
        assertFalse(BuildHelpersFeature.dropsSlabFor(GameMode.CREATIVE));
        assertTrue(BuildHelpersFeature.dropsSlabFor(GameMode.SURVIVAL));
    }
}
