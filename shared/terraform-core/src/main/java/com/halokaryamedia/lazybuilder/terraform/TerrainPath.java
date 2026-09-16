package com.halokaryamedia.lazybuilder.terraform;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Cleaned world-space path shared by Cliff and Ridge. */
public final class TerrainPath {
    private final List<Vec3d> points;
    private final double length;

    public TerrainPath(List<Vec3d> points) {
        Objects.requireNonNull(points, "points");
        if (points.size() < 2) throw new IllegalArgumentException("terrain path requires at least two points");
        this.points = List.copyOf(points);
        double total = 0.0;
        for (int i = 1; i < points.size(); i++) total += points.get(i - 1).distanceTo(points.get(i));
        if (total < 1.0e-6) throw new IllegalArgumentException("terrain path must have measurable length");
        this.length = total;
    }

    public List<Vec3d> points() { return points; }
    public double length() { return length; }

    public static TerrainPath fromEndpoints(Vec3d start, Vec3d end) {
        return new TerrainPath(List.of(start, end));
    }

    /** Removes hand jitter then resamples at a stable world-space interval. */
    public static TerrainPath prepare(List<Vec3d> raw, double spacing) {
        Objects.requireNonNull(raw, "raw");
        if (raw.size() < 2) throw new IllegalArgumentException("raw path requires two points");
        if (!Double.isFinite(spacing) || spacing <= 0.25) throw new IllegalArgumentException("spacing must be > 0.25");

        List<Vec3d> filtered = new ArrayList<>();
        filtered.add(raw.getFirst());
        for (int i = 1; i < raw.size() - 1; i++) {
            if (filtered.getLast().distanceTo(raw.get(i)) >= spacing * 0.45) filtered.add(raw.get(i));
        }
        if (filtered.getLast().distanceTo(raw.getLast()) > 1.0e-6) filtered.add(raw.getLast());

        if (filtered.size() > 2) {
            List<Vec3d> smoothed = new ArrayList<>(filtered.size());
            smoothed.add(filtered.getFirst());
            for (int i = 1; i < filtered.size() - 1; i++) {
                Vec3d a = filtered.get(i - 1);
                Vec3d b = filtered.get(i);
                Vec3d c = filtered.get(i + 1);
                smoothed.add(new Vec3d(
                        a.x() * 0.20 + b.x() * 0.60 + c.x() * 0.20,
                        a.y() * 0.20 + b.y() * 0.60 + c.y() * 0.20,
                        a.z() * 0.20 + b.z() * 0.60 + c.z() * 0.20));
            }
            smoothed.add(filtered.getLast());
            filtered = smoothed;
        }

        List<Vec3d> result = new ArrayList<>();
        result.add(filtered.getFirst());
        double carry = 0.0;
        for (int i = 1; i < filtered.size(); i++) {
            Vec3d a = filtered.get(i - 1);
            Vec3d b = filtered.get(i);
            double segment = a.distanceTo(b);
            if (segment < 1.0e-9) continue;
            double cursor = spacing - carry;
            while (cursor < segment) {
                result.add(Vec3d.lerp(a, b, cursor / segment));
                cursor += spacing;
            }
            carry = Math.max(0.0, segment - (cursor - spacing));
            if (carry >= spacing) carry %= spacing;
        }
        if (result.getLast().distanceTo(filtered.getLast()) > spacing * 0.20) result.add(filtered.getLast());
        if (result.size() < 2) result.add(filtered.getLast());
        return new TerrainPath(result);
    }

    public Vec3d tangentAt(int index) {
        if (index <= 0) return points.get(1).subtract(points.get(0)).horizontalNormalized();
        if (index >= points.size() - 1) return points.getLast().subtract(points.get(points.size() - 2)).horizontalNormalized();
        return points.get(index + 1).subtract(points.get(index - 1)).horizontalNormalized();
    }
}
