package com.halokaryamedia.lazybuilder.builder.symmetry;

import com.halokaryamedia.lazybuilder.builder.spline.BuilderVec3;
import com.halokaryamedia.lazybuilder.builder.spline.SplineFrame;

import java.util.Objects;

/**
 * Immutable affine transform used to replicate Builder plans without mutating world state.
 */
public record BuilderTransform(
        double m00, double m01, double m02,
        double m10, double m11, double m12,
        double m20, double m21, double m22,
        BuilderVec3 translation
) {
    private static final double EPSILON = 1.0e-9;
    private static final double ORTHONORMAL_TOLERANCE = 1.0e-6;

    public BuilderTransform {
        if (!finite(m00, m01, m02, m10, m11, m12, m20, m21, m22)) {
            throw new IllegalArgumentException("transform matrix must be finite");
        }
        Objects.requireNonNull(translation, "translation");
        validateOrthonormal(m00,m01,m02,m10,m11,m12,m20,m21,m22);
        double determinant = determinant(m00, m01, m02, m10, m11, m12, m20, m21, m22);
        if (Math.abs(Math.abs(determinant) - 1.0) > ORTHONORMAL_TOLERANCE) {
            throw new IllegalArgumentException("BuilderTransform determinant must be +/-1; determinant=" + determinant);
        }
    }

    public static BuilderTransform identity() {
        return new BuilderTransform(1, 0, 0, 0, 1, 0, 0, 0, 1, new BuilderVec3(0, 0, 0));
    }

    public static BuilderTransform translation(BuilderVec3 offset) {
        Objects.requireNonNull(offset, "offset");
        return new BuilderTransform(1, 0, 0, 0, 1, 0, 0, 0, 1, offset);
    }

    public static BuilderTransform rotation(BuilderVec3 origin, BuilderVec3 axis, double radians) {
        Objects.requireNonNull(origin, "origin");
        BuilderVec3 n = requireDirection(axis, "axis");
        if (!Double.isFinite(radians)) throw new IllegalArgumentException("radians must be finite");
        double x = n.x(), y = n.y(), z = n.z();
        double c = Math.cos(radians), s = Math.sin(radians), t = 1.0 - c;
        double m00 = t*x*x + c,   m01 = t*x*y - s*z, m02 = t*x*z + s*y;
        double m10 = t*x*y + s*z, m11 = t*y*y + c,   m12 = t*y*z - s*x;
        double m20 = t*x*z - s*y, m21 = t*y*z + s*x, m22 = t*z*z + c;
        BuilderTransform linear = new BuilderTransform(m00,m01,m02,m10,m11,m12,m20,m21,m22,new BuilderVec3(0,0,0));
        BuilderVec3 translation = origin.subtract(linear.transformVector(origin));
        return new BuilderTransform(m00,m01,m02,m10,m11,m12,m20,m21,m22,translation);
    }

    public static BuilderTransform reflection(BuilderVec3 pointOnPlane, BuilderVec3 planeNormal) {
        Objects.requireNonNull(pointOnPlane, "pointOnPlane");
        BuilderVec3 n = requireDirection(planeNormal, "planeNormal");
        double x = n.x(), y = n.y(), z = n.z();
        double m00 = 1 - 2*x*x, m01 = -2*x*y,     m02 = -2*x*z;
        double m10 = -2*y*x,     m11 = 1 - 2*y*y, m12 = -2*y*z;
        double m20 = -2*z*x,     m21 = -2*z*y,     m22 = 1 - 2*z*z;
        BuilderTransform linear = new BuilderTransform(m00,m01,m02,m10,m11,m12,m20,m21,m22,new BuilderVec3(0,0,0));
        BuilderVec3 translation = pointOnPlane.subtract(linear.transformVector(pointOnPlane));
        return new BuilderTransform(m00,m01,m02,m10,m11,m12,m20,m21,m22,translation);
    }

    public BuilderVec3 transformPoint(BuilderVec3 point) {
        Objects.requireNonNull(point, "point");
        return transformVector(point).add(translation);
    }

    public BuilderVec3 transformVector(BuilderVec3 vector) {
        Objects.requireNonNull(vector, "vector");
        return new BuilderVec3(
                m00*vector.x() + m01*vector.y() + m02*vector.z(),
                m10*vector.x() + m11*vector.y() + m12*vector.z(),
                m20*vector.x() + m21*vector.y() + m22*vector.z()
        );
    }

    public SplineFrame transformFrame(SplineFrame frame) {
        Objects.requireNonNull(frame, "frame");
        return new SplineFrame(
                transformVector(frame.tangent()).normalized(),
                transformVector(frame.normal()).normalized(),
                transformVector(frame.binormal()).normalized()
        );
    }

    public boolean orientationReversing() {
        return determinant(m00,m01,m02,m10,m11,m12,m20,m21,m22) < -EPSILON;
    }

    private static BuilderVec3 requireDirection(BuilderVec3 vector, String label) {
        Objects.requireNonNull(vector, label);
        if (vector.lengthSquared() <= EPSILON * EPSILON) {
            throw new IllegalArgumentException(label + " must be non-zero");
        }
        return vector.normalized();
    }

    private static void validateOrthonormal(double a,double b,double c,double d,double e,double f,double g,double h,double i) {
        double r0 = a*a+b*b+c*c;
        double r1 = d*d+e*e+f*f;
        double r2 = g*g+h*h+i*i;
        double d01 = a*d+b*e+c*f;
        double d02 = a*g+b*h+c*i;
        double d12 = d*g+e*h+f*i;
        if (Math.abs(r0-1.0) > ORTHONORMAL_TOLERANCE
                || Math.abs(r1-1.0) > ORTHONORMAL_TOLERANCE
                || Math.abs(r2-1.0) > ORTHONORMAL_TOLERANCE
                || Math.abs(d01) > ORTHONORMAL_TOLERANCE
                || Math.abs(d02) > ORTHONORMAL_TOLERANCE
                || Math.abs(d12) > ORTHONORMAL_TOLERANCE) {
            throw new IllegalArgumentException("BuilderTransform matrix must be orthonormal");
        }
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }

    private static double determinant(double a,double b,double c,double d,double e,double f,double g,double h,double i) {
        return a*(e*i-f*h)-b*(d*i-f*g)+c*(d*h-e*g);
    }
}
