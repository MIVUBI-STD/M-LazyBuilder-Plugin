package com.halokaryamedia.lazybuilder.builder.placement;

import com.halokaryamedia.lazybuilder.builder.operation.OperationSeed;
import java.util.List;

public final class WeightedPlacementSource implements PlacementSource {
    public record Entry(String sourceId, double weight) {
        public Entry {
            if (sourceId == null || sourceId.isBlank()) {
                throw new IllegalArgumentException("sourceId must be non-blank");
            }
            if (!Double.isFinite(weight) || weight <= 0.0) {
                throw new IllegalArgumentException("weight must be finite and > 0");
            }
        }
    }

    private final List<Entry> entries;
    private final double[] cumulative;
    private final double total;
    private final long channel;

    public WeightedPlacementSource(List<Entry> entries, long channel) {
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("placement source requires entries");
        }
        this.entries = List.copyOf(entries);
        this.cumulative = new double[entries.size()];
        double running = 0.0;
        for (int i = 0; i < entries.size(); i++) {
            running += entries.get(i).weight();
            if (!Double.isFinite(running)) {
                throw new IllegalArgumentException("total source weight is not finite");
            }
            cumulative[i] = running;
        }
        total = running;
        this.channel = channel;
    }

    @Override
    public String resolve(PlacementPoint point, OperationSeed seed) {
        double choice = seed.sampleUnit(point.x(), point.y(), point.z(), channel ^ point.ordinal()) * total;
        for (int i = 0; i < cumulative.length; i++) {
            if (choice < cumulative[i]) {
                return entries.get(i).sourceId();
            }
        }
        return entries.get(entries.size() - 1).sourceId();
    }
}
