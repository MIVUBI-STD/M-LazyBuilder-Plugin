package com.halokaryamedia.lazybuilder.builder.material;

import java.util.List;
import java.util.Objects;

public final class WeightedPaletteMaterial implements BuilderMaterial {
    public record Entry(BuilderMaterial material, double weight) {
        public Entry {
            Objects.requireNonNull(material, "material");
            if (!Double.isFinite(weight) || weight <= 0.0) {
                throw new IllegalArgumentException("weight must be finite and > 0");
            }
        }
    }

    private final List<Entry> entries;
    private final double[] cumulativeWeights;
    private final double totalWeight;
    private final long channel;

    public WeightedPaletteMaterial(List<Entry> entries, long channel) {
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("weighted palette requires at least one entry");
        }
        this.entries = List.copyOf(entries);
        this.cumulativeWeights = new double[entries.size()];
        double running = 0.0;
        for (int i = 0; i < entries.size(); i++) {
            running += entries.get(i).weight();
            if (!Double.isFinite(running)) {
                throw new IllegalArgumentException("total palette weight is not finite");
            }
            cumulativeWeights[i] = running;
        }
        this.totalWeight = running;
        this.channel = channel;
    }

    @Override
    public String resolve(MaterialContext context) {
        double choice = context.seed().sampleUnit(context.x(), context.y(), context.z(), channel) * totalWeight;
        for (int i = 0; i < cumulativeWeights.length; i++) {
            if (choice < cumulativeWeights[i]) {
                return entries.get(i).material().resolve(context);
            }
        }
        return entries.get(entries.size() - 1).material().resolve(context);
    }

    public List<Entry> entries() {
        return entries;
    }
}
