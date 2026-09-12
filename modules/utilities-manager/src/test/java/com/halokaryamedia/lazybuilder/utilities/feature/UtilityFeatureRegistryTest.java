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

    private static final class SharedRecordingFeature implements UtilityFeature {
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
}
