package com.halokaryamedia.lazybuilder.builder.axiom;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AxiomSplineToolContractTest {
    @Test void splineToolUsesRecoverySafeWorldMutationPath() {
        assertTrue(AxiomSplineToolContract.WORLD_MUTATION_ENABLED);
        assertEquals(
                SplinePreviewVoxelizer.MAX_MUTATION_VOXELS,
                AxiomSplineToolContract.MAX_MUTATION_VOXELS);
        assertTrue(
                AxiomSplineToolContract.MAX_MUTATION_VOXELS
                        > SplinePreviewVoxelizer.MAX_PREVIEW_VOXELS);
    }
}
