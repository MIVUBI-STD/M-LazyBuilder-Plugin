package com.halokaryamedia.lazybuilder.world.export;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExportSettingsWireTest {
    @Test
    void settingsRoundTripWithoutExposingConverterSettings() throws Exception {
        var expected = new ExportSettingsWire.Settings(
                "CREATIVE", "HARD", Map.of("keepinventory", "true", "randomTickSpeed", "3"));
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            ExportSettingsWire.write(out, expected);
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(buffer.toByteArray()))) {
            assertEquals(expected, ExportSettingsWire.read(in));
            assertEquals(0, in.available());
        }
    }

    @Test
    void inheritRoundTripsAsEmptyOverrides() throws Exception {
        var expected = ExportSettingsWire.Settings.inherit();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            ExportSettingsWire.write(out, expected);
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(buffer.toByteArray()))) {
            assertEquals(expected, ExportSettingsWire.read(in));
        }
    }

    @Test
    void rejectsTooManyRulesAndInvalidNames() {
        Map<String, String> rules = new LinkedHashMap<>();
        for (int i = 0; i <= ExportSettingsWire.MAX_RULES; i++) rules.put("rule" + i, "true");
        assertThrows(IllegalArgumentException.class,
                () -> new ExportSettingsWire.Settings("", "", rules));
        assertThrows(IllegalArgumentException.class,
                () -> new ExportSettingsWire.Settings("", "", Map.of("bad rule", "true")));
    }

    @Test
    void rejectsTruncatedPayload() throws Exception {
        byte[] truncated = {0, 1};
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(truncated))) {
            assertThrows(IOException.class, () -> ExportSettingsWire.read(in));
        }
    }
}
