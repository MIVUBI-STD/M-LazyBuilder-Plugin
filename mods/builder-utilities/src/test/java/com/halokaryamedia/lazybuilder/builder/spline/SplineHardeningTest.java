package com.halokaryamedia.lazybuilder.builder.spline;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SplineHardeningTest {
    @Test
    void rollInterpolatesAcrossWrapByShortestPath() {
        assertEquals(180.0, SplineAngles.lerpDegreesShortest(170.0, -170.0, 0.5), 1.0e-9);
        assertEquals(-180.0, SplineAngles.lerpDegreesShortest(-170.0, 170.0, 0.5), 1.0e-9);
    }

    @Test
    void centripetalSplinePreservesEndpointsAndFiniteTangents() {
        CatmullRomSpline spline = new CatmullRomSpline(List.of(
                point(0, 0, 0),
                point(1, 0, 0),
                point(20, 5, 0),
                point(21, 5, 1)
        ), SplineParameterization.CENTRIPETAL);

        SplineEvaluation start = spline.evaluate(0.0);
        SplineEvaluation end = spline.evaluate(1.0);
        assertEquals(new BuilderVec3(0, 0, 0), start.position());
        assertEquals(new BuilderVec3(21, 5, 1), end.position());
        assertEquals(1.0, start.tangent().length(), 1.0e-6);
        assertEquals(1.0, end.tangent().length(), 1.0e-6);

        for (int i = 0; i <= 100; i++) {
            SplineEvaluation evaluation = spline.evaluate(i / 100.0);
            assertTrue(Double.isFinite(evaluation.position().x()));
            assertTrue(Double.isFinite(evaluation.position().y()));
            assertTrue(Double.isFinite(evaluation.position().z()));
        }
    }

    @Test
    void uniformRemainsDefaultForCompatibility() {
        CatmullRomSpline spline = new CatmullRomSpline(List.of(
                point(0, 0, 0), point(1, 0, 0)
        ));
        assertEquals(SplineParameterization.UNIFORM, spline.parameterization());
    }

    private static SplineControlPoint point(double x, double y, double z) {
        return new SplineControlPoint(new BuilderVec3(x, y, z), 1.0, 0.0);
    }
}
