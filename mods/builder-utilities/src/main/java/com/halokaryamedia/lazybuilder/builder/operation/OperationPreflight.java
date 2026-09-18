package com.halokaryamedia.lazybuilder.builder.operation;

/** Overflow-safe preflight helpers shared by high-volume Builder tools. */
public final class OperationPreflight {
    private OperationPreflight() {}

    public static long requireAtMost(long value, long maximum, String label) {
        if (value < 0) throw new IllegalArgumentException(label + " must be >= 0");
        if (maximum < 0) throw new IllegalArgumentException("maximum must be >= 0");
        if (value > maximum) {
            throw new IllegalArgumentException(
                    label + " exceeds limit " + maximum + ": " + value);
        }
        return value;
    }

    public static long multiply(long left, long right, String label) {
        if (left < 0 || right < 0) {
            throw new IllegalArgumentException(label + " factors must be >= 0");
        }
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(label + " overflow", e);
        }
    }

    public static long estimateBytes(long changes, long bytesPerChange, String label) {
        if (bytesPerChange <= 0) {
            throw new IllegalArgumentException("bytesPerChange must be > 0");
        }
        return Math.max(1L, multiply(changes, bytesPerChange, label));
    }
}
