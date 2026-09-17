package com.halokaryamedia.lazybuilder.builder.spline;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SplineSamplerTest {
    @Test
    void parallelTransportFramesStayOrthonormalAcrossCurve() {
        CatmullRomSpline spline = new CatmullRomSpline(List.of(
                new SplineControlPoint(new BuilderVec3(0, 0, 0), 1, 0),
                new SplineControlPoint(new BuilderVec3(5, 3, 2), 2, 20),
                new SplineControlPoint(new BuilderVec3(10, 1, 8), 1.5, 45),
                new SplineControlPoint(new BuilderVec3(15, 5, 12), 1, 90)
        ));
        List<SplineSample> samples = SplineSampler.sample(spline, 16);
        assertEquals(49, samples.size());
        for (SplineSample sample : samples) {
            SplineFrame frame = sample.frame();
            assertEquals(1.0, frame.tangent().length(), 1e-9);
            assertEquals(1.0, frame.normal().length(), 1e-9);
            assertEquals(1.0, frame.binormal().length(), 1e-9);
            assertEquals(0.0, frame.tangent().dot(frame.normal()), 1e-9);
            assertEquals(0.0, frame.tangent().dot(frame.binormal()), 1e-9);
            assertEquals(0.0, frame.normal().dot(frame.binormal()), 1e-9);
        }
    }

    @Test
    void straightSplineDoesNotFlipFrames() {
        CatmullRomSpline spline = new CatmullRomSpline(List.of(
                new SplineControlPoint(new BuilderVec3(0, 0, 0), 1, 0),
                new SplineControlPoint(new BuilderVec3(0, 0, 10), 1, 0),
                new SplineControlPoint(new BuilderVec3(0, 0, 20), 1, 0)
        ));
        List<SplineSample> samples = SplineSampler.sample(spline, 8);
        for (int i = 1; i < samples.size(); i++) {
            assertTrue(samples.get(i - 1).frame().normal().dot(samples.get(i).frame().normal()) > 0.99);
        }
    }
}
