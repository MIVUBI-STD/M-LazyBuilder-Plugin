package com.halokaryamedia.lazybuilder.utilities.feature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns Utilities-Manager feature lifecycle without coupling features to each other.
 */
public final class UtilityFeatureRegistry {
    private final Map<String, UtilityFeature> features = new LinkedHashMap<>();
    private final List<UtilityFeature> enabledFeatures = new ArrayList<>();

    public void register(UtilityFeature feature) {
        if (feature == null) {
            throw new IllegalArgumentException("feature must not be null");
        }
        String id = feature.id();
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("feature id must not be blank");
        }
        if (features.putIfAbsent(id, feature) != null) {
            throw new IllegalArgumentException("duplicate feature id: " + id);
        }
    }

    public void enable(String id) {
        UtilityFeature feature = requireFeature(id);
        if (enabledFeatures.contains(feature)) {
            return;
        }
        feature.enable();
        enabledFeatures.add(feature);
    }

    public void disable(String id) {
        UtilityFeature feature = requireFeature(id);
        if (!enabledFeatures.remove(feature)) {
            return;
        }
        feature.disable();
    }

    public void disableAll() {
        for (int index = enabledFeatures.size() - 1; index >= 0; index--) {
            enabledFeatures.get(index).disable();
        }
        enabledFeatures.clear();
    }

    public boolean isEnabled(String id) {
        return enabledFeatures.contains(requireFeature(id));
    }

    public List<String> registeredFeatureIds() {
        return Collections.unmodifiableList(new ArrayList<>(features.keySet()));
    }

    private UtilityFeature requireFeature(String id) {
        UtilityFeature feature = features.get(id);
        if (feature == null) {
            throw new IllegalArgumentException("unknown feature id: " + id);
        }
        return feature;
    }
}
