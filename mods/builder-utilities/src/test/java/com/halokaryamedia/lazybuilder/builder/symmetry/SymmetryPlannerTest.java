package com.halokaryamedia.lazybuilder.builder.symmetry;

import com.halokaryamedia.lazybuilder.builder.spline.BuilderVec3;
import com.halokaryamedia.lazybuilder.builder.spline.SplineFrame;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SymmetryPlannerTest {
    @Test
    void reflectionMirrorsAcrossPlaneAndReversesOrientation() {
        BuilderTransform reflection = SymmetryPlanner.reflection(
                new BuilderVec3(0, 0, 0), new BuilderVec3(1, 0, 0)).get(1);
        BuilderVec3 result = reflection.transformPoint(new BuilderVec3(3, 2, -4));
        assertEquals(-3.0, result.x(), 1.0e-9);
        assertEquals(2.0, result.y(), 1.0e-9);
        assertEquals(-4.0, result.z(), 1.0e-9);
        assertTrue(reflection.orientationReversing());
    }

    @Test
    void rotationalCopiesAreEvenlyDistributedAroundAxis() {
        List<BuilderTransform> transforms = SymmetryPlanner.rotational(
                new BuilderVec3(0, 0, 0), new BuilderVec3(0, 1, 0), 4);
        BuilderVec3 quarter = transforms.get(1).transformPoint(new BuilderVec3(1, 0, 0));
        assertEquals(0.0, quarter.x(), 1.0e-9);
        assertEquals(-1.0, quarter.z(), 1.0e-9);
        assertFalse(transforms.get(1).orientationReversing());
    }

    @Test
    void rejectsShearEvenWhenDeterminantIsOne() {
        assertThrows(IllegalArgumentException.class, () -> new BuilderTransform(
                1, 1, 0,
                0, 1, 0,
                0, 0, 1,
                new BuilderVec3(0, 0, 0)));
    }

    @Test
    void rejectsZeroLengthRotationAxis() {
        assertThrows(IllegalArgumentException.class, () -> BuilderTransform.rotation(
                new BuilderVec3(0, 0, 0), new BuilderVec3(0, 0, 0), Math.PI));
    }

    @Test
    void reflectionTransformsSplineFrameWithoutLosingUnitAxes() {
        SplineFrame frame = new SplineFrame(
                new BuilderVec3(1, 0, 0), new BuilderVec3(0, 1, 0), new BuilderVec3(0, 0, 1));
        SplineFrame reflected = BuilderTransform.reflection(
                new BuilderVec3(0, 0, 0), new BuilderVec3(1, 0, 0)).transformFrame(frame);
        assertEquals(1.0, reflected.tangent().length(), 1.0e-9);
        assertEquals(1.0, reflected.normal().length(), 1.0e-9);
        assertEquals(1.0, reflected.binormal().length(), 1.0e-9);
        assertEquals(0.0, reflected.tangent().dot(reflected.normal()), 1.0e-9);
    }

    @Test
    void translationalSymmetryIncludesIdentityAndOffsets() {
        List<BuilderTransform> transforms = SymmetryPlanner.translational(new BuilderVec3(5, 0, 0), 3);
        assertEquals(new BuilderVec3(0, 0, 0), transforms.get(0).transformPoint(new BuilderVec3(0, 0, 0)));
        assertEquals(new BuilderVec3(10, 0, 0), transforms.get(2).transformPoint(new BuilderVec3(0, 0, 0)));
    }

    @Test
    void replicationIsBoundedAndDeterministic() {
        List<BuilderTransform> transforms = SymmetryPlanner.rotational(
                new BuilderVec3(0, 0, 0), new BuilderVec3(0, 1, 0), 3);
        List<SymmetryInstance<String>> first = SymmetryReplicator.replicate(List.of("a", "b"), transforms);
        assertEquals(first, SymmetryReplicator.replicate(List.of("a", "b"), transforms));
        assertEquals(6, first.size());
    }
}
