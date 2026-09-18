package com.moulberry.axiomclientapi.regions;

import net.minecraft.block.BlockState;

/** Compile-only subset of the MIT-licensed AxiomClientAPI BlockRegion contract. */
public interface BlockRegion {
    void addBlock(int x, int y, int z, BlockState block);
}
