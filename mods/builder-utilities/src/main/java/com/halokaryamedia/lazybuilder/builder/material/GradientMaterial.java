package com.halokaryamedia.lazybuilder.builder.material;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Maps a scalar field to ordered discrete material stops. */
public final class GradientMaterial implements BuilderMaterial {
    public record Stop(double atOrBelow, BuilderMaterial material) {
        public Stop {
            if (!Double.isFinite(atOrBelow)) {
                throw new IllegalArgumentException("stop must be finite");
            }
            Objects.requireNonNull(material, "material");
        }
    }

    private final ScalarField field;
    private final List<Stop> stops;

    public GradientMaterial(ScalarField field, List<Stop> stops) {
        this.field = Objects.requireNonNull(field, "field");
        if (stops == null || stops.isEmpty()) {
            throw new IllegalArgumentException("gradient requires at least one stop");
        }
        this.stops = stops.stream().sorted(Comparator.comparingDouble(Stop::atOrBelow)).toList();
    }

    @Override
    public String resolve(MaterialContext context) {
        double value = field.sample(context);
        for (Stop stop : stops) {
            if (value <= stop.atOrBelow()) {
                return stop.material().resolve(context);
            }
        }
        return stops.get(stops.size() - 1).material().resolve(context);
    }
}
