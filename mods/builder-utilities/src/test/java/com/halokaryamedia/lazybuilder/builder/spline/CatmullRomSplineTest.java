package com.halokaryamedia.lazybuilder.builder.spline;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CatmullRomSplineTest {
    @Test
    void preservesEndpointsAndProfiles() {
        CatmullRomSpline spline = new CatmullRomSpline(List.of(
                new SplineControlPoint(new BuilderVec3(0, 0, 0), 2, 0),
                new SplineControlPoint(new BuilderVec3(10, 0, 0), 4, 90)
        ));
        assertEquals(new BuilderVec3(0, 0, 0), spline.evaluate(0).position());
        assertEquals(new BuilderVec3(10, 0, 0), spline.evaluate(1).position());
        assertEquals(3.0, spline.evaluate(0.5).radius(), 1e-9);
        assertEquals(45.0, spline.evaluate(0.5).rollDegrees(), 1e-9);
    }

    @Test
    void rejectsDuplicateAdjacentControlPoints() {
        BuilderVec3 point = new BuilderVec3(1, 2, 3);
        assertThrows(IllegalArgumentException.class, () -> new CatmullRomSpline(List.of(
                new SplineControlPoint(point, 1, 0), new SplineControlPoint(point, 1, 0))));
    }
}
