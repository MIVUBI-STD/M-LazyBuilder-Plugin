package com.moulberry.axiomclientapi.service;

import com.moulberry.axiomclientapi.regions.BlockRegion;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;

/** Compile-only subset of the MIT-licensed AxiomClientAPI ToolService contract. */
public interface ToolService {
    void pushBlockRegionChange(BlockRegion blockRegion);
    BlockHitResult raycastBlock();
    BlockState getActiveBlock();
}
