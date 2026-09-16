package com.halokaryamedia.lazybuilder.world.conversion;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkerCliAdapterTest {
    @Test
    void parsesVersionAndSupportedFormatsFromVerifiedCliShapes() throws Exception {
        assertEquals("1.20.0", ChunkerCliAdapter.parseRuntimeVersion("1.20.0\n"));

        List<String> formats = ChunkerCliAdapter.parseSupportedFormats(
                "Invalid value, should be one of the following values: INPUT, JAVA_1_21_4, BEDROCK_1_21_80, JAVA_1_20_6"
        );
        assertEquals(List.of("BEDROCK_1_21_80", "JAVA_1_20_6", "JAVA_1_21_4"), formats);
    }

    @Test
    void requiredCliContractChecksExportCustomizationOptions() {
        assertTrue(ChunkerCliAdapter.hasRequiredCliContract(
                "--inputDirectory --outputFormat --outputDirectory --worldSettings --pruning --converterSettings"
        ));
        assertFalse(ChunkerCliAdapter.hasRequiredCliContract(
                "--inputDirectory --outputFormat --outputDirectory --pruning"
        ));
    }

    @Test
    void conversionCommandAddsOnlySelectedSettingsInputs() {
        ChunkerCliAdapter adapter = new ChunkerCliAdapter(
                Path.of("/java/bin/java"),
                2048,
                Duration.ofSeconds(10),
                Duration.ofMinutes(30),
                new OnDemandProcessRunner()
        );
        ConverterAdapter.ConversionRequest entireWorld = new ConverterAdapter.ConversionRequest(
                Path.of("/input"), Path.of("/output"), "java_1_21_4", null
        );
        List<String> command = adapter.buildConversionCommand(Path.of("/runtime/converter.jar"), entireWorld);
        assertTrue(command.contains("-Xmx2048m"));
        assertTrue(command.contains("JAVA_1_21_4"));
        assertFalse(command.contains("-s"));
        assertFalse(command.contains("-p"));
        assertFalse(command.contains("-c"));

        Path pruningSettings = Path.of("/tmp/pruning.json");
        Path worldSettings = Path.of("/tmp/world-settings.json");
        Path converterSettings = Path.of("/tmp/converter-settings.json");
        ConverterAdapter.ConversionRequest customized = new ConverterAdapter.ConversionRequest(
                Path.of("/input"),
                Path.of("/output"),
                "bedrock_1_21_80",
                pruningSettings,
                worldSettings,
                converterSettings
        );
        List<String> customizedCommand = adapter.buildConversionCommand(
                Path.of("/runtime/converter.jar"), customized);
        assertTrue(customizedCommand.contains("-s"));
        assertTrue(customizedCommand.contains(worldSettings.toString()));
        assertTrue(customizedCommand.contains("-p"));
        assertTrue(customizedCommand.contains(pruningSettings.toString()));
        assertTrue(customizedCommand.contains("-c"));
        assertTrue(customizedCommand.contains(converterSettings.toString()));
    }
}
