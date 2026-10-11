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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportSettingsWireTest {
    @Test
    void settingsRoundTripWithoutExposingConverterDetails() throws Exception {
        var expected = new ExportSettingsWire.Settings(
                "CREATIVE", "HARD", Map.of("keepinventory", "true", "randomTickSpeed", "3"), true);
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
    void legacyAndWorkspaceDefaultsKeepDifferentCapabilityPaths() throws Exception {
        var legacy = ExportSettingsWire.Settings.inherit();
        var workspace = ExportSettingsWire.Settings.workspaceDefaults();
        assertFalse(legacy.optimizeOutput());
        assertTrue(workspace.optimizeOutput());

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            ExportSettingsWire.write(out, workspace);
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(buffer.toByteArray()))) {
            assertEquals(workspace, ExportSettingsWire.read(in));
        }
    }

    @Test
    void threeArgumentConstructorRemainsLegacyCompatible() {
        var settings = new ExportSettingsWire.Settings("CREATIVE", "HARD", Map.of());
        assertFalse(settings.optimizeOutput());
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
    void rejectsMalformedUtf8InOptionalSettingsAndRuleValues() throws Exception {
        ByteArrayOutputStream options = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(options)) {
            ExportSettingsWire.write(out, new ExportSettingsWire.Settings(
                    "x", "", Map.of(), false));
        }
        byte[] invalidOptional = options.toByteArray();
        invalidOptional[3] = (byte) 0xFF;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(invalidOptional))) {
            assertThrows(IOException.class, () -> ExportSettingsWire.read(in));
        }

        ByteArrayOutputStream rules = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(rules)) {
            ExportSettingsWire.write(out, new ExportSettingsWire.Settings(
                    "", "", Map.of("keepInventory", "x"), false));
        }
        byte[] invalidRule = rules.toByteArray();
        invalidRule[invalidRule.length - 1] = (byte) 0xFF;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(invalidRule))) {
            assertThrows(IOException.class, () -> ExportSettingsWire.read(in));
        }
    }

    @Test
    void rejectsTruncatedPayload() throws Exception {
        byte[] truncated = {1, 0};
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(truncated))) {
            assertThrows(IOException.class, () -> ExportSettingsWire.read(in));
        }
    }
}
