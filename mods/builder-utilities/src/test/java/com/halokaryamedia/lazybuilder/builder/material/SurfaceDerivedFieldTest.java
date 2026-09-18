package com.halokaryamedia.lazybuilder.builder.material;

import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SurfaceDerivedFieldTest {
    private static final MaterialContext CONTEXT = new MaterialContext(0, 64, 0, "minecraft:stone", new OperationSeed(1));

    @Test
    void slopeIsZeroForFlatSurfaceAndTracksIncline() {
        SurfaceSlopeField flat = new SurfaceSlopeField((x, z) -> 64.0, 1);
        SurfaceSlopeField incline = new SurfaceSlopeField((x, z) -> x, 1);
        assertEquals(0.0, flat.sample(CONTEXT), 1.0e-9);
        assertEquals(45.0, incline.sample(CONTEXT), 1.0e-9);
    }

    @Test
    void curvatureDistinguishesPeakValleyAndPlane() {
        SurfaceCurvatureField flat = new SurfaceCurvatureField((x, z) -> 0.0, 1, 4.0);
        SurfaceCurvatureField peak = new SurfaceCurvatureField((x, z) -> (x == 0 && z == 0) ? 1.0 : 0.0, 1, 4.0);
        SurfaceCurvatureField valley = new SurfaceCurvatureField((x, z) -> (x == 0 && z == 0) ? -1.0 : 0.0, 1, 4.0);
        assertEquals(0.0, flat.sample(CONTEXT), 1.0e-9);
        assertTrue(peak.sample(CONTEXT) > 0.0);
        assertTrue(valley.sample(CONTEXT) < 0.0);
    }

}
