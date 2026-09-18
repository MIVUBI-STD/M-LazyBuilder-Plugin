package com.moulberry.axiomclientapi.service;

import com.moulberry.axiomclientapi.regions.BlockRegion;
import com.moulberry.axiomclientapi.regions.BooleanRegion;

/** Compile-only subset of the MIT-licensed AxiomClientAPI RegionProvider contract. */
public interface RegionProvider {
    BlockRegion createBlock();
    BooleanRegion createBoolean();
}
