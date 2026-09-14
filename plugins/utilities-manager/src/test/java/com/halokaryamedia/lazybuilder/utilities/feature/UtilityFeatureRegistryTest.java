package com.halokaryamedia.lazybuilder.utilities.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class UtilityFeatureRegistryTest {

    @Test
    void enablesAndDisablesFeaturesIndependently() {
        UtilityFeatureRegistry registry = new UtilityFeatureRegistry();
        RecordingFeature movement = new RecordingFeature("movement");
        RecordingFeature safety = new RecordingFeature("world-safety");

        registry.register(movement);
        registry.register(safety);
        registry.enable("movement");

        assertTrue(registry.isEnabled("movement"));
        assertFalse(registry.isEnabled("world-safety"));
        assertEquals(List.of("enable"), movement.events);
        assertTrue(safety.events.isEmpty());

        registry.disable("movement");

        assertFalse(registry.isEnabled("movement"));
        assertEquals(List.of("enable", "disable"), movement.events);
    }

    @Test
    void rejectsDuplicateFeatureIds() {
        UtilityFeatureRegistry registry = new UtilityFeatureRegistry();
        registry.register(new RecordingFeature("movement"));

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(new RecordingFeature("movement"))
        );
    }

    @Test
    void disablesEnabledFeaturesInReverseOrder() {
        List<String> events = new ArrayList<>();
        UtilityFeatureRegistry registry = new UtilityFeatureRegistry();
        registry.register(new SharedRecordingFeature("a", events));
        registry.register(new SharedRecordingFeature("b", events));

        registry.enable("a");
        registry.enable("b");
        events.clear();

        registry.disableAll();

        assertEquals(List.of("disable:b", "disable:a"), events);
    }

    @Test
    void failedSingleDisableKeepsFeatureMarkedEnabled() {
        UtilityFeatureRegistry registry = new UtilityFeatureRegistry();
        FailingFeature feature = new FailingFeature("movement");
        registry.register(feature);
        registry.enable("movement");

        assertThrows(IllegalStateException.class, () -> registry.disable("movement"));

        assertTrue(registry.isEnabled("movement"));
    }

    @Test
    void disableAllContinuesAfterOneFeatureFails() {
        List<String> events = new ArrayList<>();
        UtilityFeatureRegistry registry = new UtilityFeatureRegistry();
        registry.register(new SharedRecordingFeature("a", events));
        registry.register(new SharedFailingFeature("b", events));
        registry.register(new SharedRecordingFeature("c", events));
        registry.enable("a");
        registry.enable("b");
        registry.enable("c");
        events.clear();

        IllegalStateException failure = assertThrows(IllegalStateException.class, registry::disableAll);

        assertEquals(List.of("disable:c", "disable:b", "disable:a"), events);
        assertFalse(registry.isEnabled("a"));
        assertTrue(registry.isEnabled("b"));
        assertFalse(registry.isEnabled("c"));
        assertEquals(1, failure.getSuppressed().length);
    }

    private static final class RecordingFeature implements UtilityFeature {
        private final String id;
        private final List<String> events = new ArrayList<>();

        private RecordingFeature(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public void enable() {
            events.add("enable");
        }

        @Override
        public void disable() {
            events.add("disable");
        }
    }

    private static final class FailingFeature implements UtilityFeature {
        private final String id;

        private FailingFeature(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public void enable() { }

        @Override
        public void disable() {
            throw new IllegalStateException("disable failed: " + id);
        }
    }

    private static class SharedRecordingFeature implements UtilityFeature {
        private final String id;
        private final List<String> events;

        private SharedRecordingFeature(String id, List<String> events) {
            this.id = id;
            this.events = events;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public void enable() {
            events.add("enable:" + id);
        }

        @Override
        public void disable() {
            events.add("disable:" + id);
        }
    }

    private static final class SharedFailingFeature extends SharedRecordingFeature {
        private SharedFailingFeature(String id, List<String> events) {
            super(id, events);
        }

        @Override
        public void disable() {
            super.disable();
            throw new IllegalStateException("disable failed");
        }
    }
}
