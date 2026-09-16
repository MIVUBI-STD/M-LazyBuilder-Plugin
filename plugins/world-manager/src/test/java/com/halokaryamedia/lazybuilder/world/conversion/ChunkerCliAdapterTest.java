package com.halokaryamedia.lazybuilder.world.conversion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkerCliAdapterTest {
    @TempDir Path tempDir;

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
                "--inputDirectory --outputFormat --outputDirectory --worldSettings --pruning --converterSettings --keepOriginalNBT"
        ));
        assertFalse(ChunkerCliAdapter.hasRequiredCliContract(
                "--inputDirectory --outputFormat --outputDirectory --worldSettings --pruning --converterSettings"
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
        assertFalse(command.contains("-k"));

        ConverterAdapter.ConversionRequest customized = new ConverterAdapter.ConversionRequest(
                Path.of("/input"),
                Path.of("/output"),
                "bedrock_1_21_80",
                Path.of("/tmp/pruning.json"),
                Path.of("/tmp/world-settings.json"),
                Path.of("/tmp/converter-settings.json")
        );
        List<String> customizedCommand = adapter.buildConversionCommand(
                Path.of("/runtime/converter.jar"), customized);
        assertTrue(customizedCommand.contains("-s"));
        assertTrue(customizedCommand.contains(customized.worldSettings().toString()));
        assertTrue(customizedCommand.contains("-p"));
        assertTrue(customizedCommand.contains(customized.pruningSettings().toString()));
        assertTrue(customizedCommand.contains("-c"));
        assertTrue(customizedCommand.contains(customized.converterSettings().toString()));
        assertFalse(customizedCommand.contains("-k"));
    }

    @Test
    void nativeAreaPruningPreservesOriginalNbtOnlyForCanonicalSameFormat() {
        ChunkerCliAdapter adapter = new ChunkerCliAdapter(
                Path.of("/java/bin/java"),
                2048,
                Duration.ofSeconds(10),
                Duration.ofMinutes(30),
                new OnDemandProcessRunner()
        );

        ConverterAdapter.ConversionRequest nativeArea = new ConverterAdapter.ConversionRequest(
                Path.of("/input"),
                Path.of("/output"),
                "java_1_21_4",
                Path.of("/tmp/pruning.json")
        );
        assertTrue(nativeArea.keepOriginalNbt());
        assertTrue(adapter.buildConversionCommand(Path.of("/runtime/converter.jar"), nativeArea).contains("-k"));

        ConverterAdapter.ConversionRequest crossVersionArea = new ConverterAdapter.ConversionRequest(
                Path.of("/input"),
                Path.of("/output"),
                "java_1_20_6",
                Path.of("/tmp/pruning.json")
        );
        assertFalse(crossVersionArea.keepOriginalNbt());
        assertFalse(adapter.buildConversionCommand(Path.of("/runtime/converter.jar"), crossVersionArea).contains("-k"));
    }

    @Test
    void customDimensionDefinitionsAreDetectedInFolderAndZipDatapacks() throws Exception {
        Path folderWorld = tempDir.resolve("folder-world");
        Path definition = folderWorld.resolve("datapacks/custom/data/example/dimension/moon.json");
        Files.createDirectories(definition.getParent());
        Files.writeString(definition, "{}");
        assertTrue(ChunkerCliAdapter.containsCustomDimensionDefinitions(folderWorld));

        Path zipWorld = tempDir.resolve("zip-world");
        Path datapacks = Files.createDirectories(zipWorld.resolve("datapacks"));
        Path zip = datapacks.resolve("custom.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("data/example/dimension/moon.json"));
            out.write("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.closeEntry();
        }
        assertTrue(ChunkerCliAdapter.containsCustomDimensionDefinitions(zipWorld));
    }

    @Test
    void selectedAreaFailsClosedWhenCustomDimensionsExist() throws Exception {
        Path world = tempDir.resolve("area-world");
        Path definition = world.resolve("datapacks/custom/data/example/dimension/moon.json");
        Files.createDirectories(definition.getParent());
        Files.writeString(definition, "{}");
        Path pruning = Files.writeString(tempDir.resolve("pruning.json"), "{}");

        ConverterAdapter.ConversionRequest request = new ConverterAdapter.ConversionRequest(
                world, tempDir.resolve("output"), "JAVA_1_21_4", pruning);

        IOException failure = assertThrows(IOException.class,
                () -> ChunkerCliAdapter.requireSupportedCustomDimensionShape(request));
        assertTrue(failure.getMessage().contains("Selected Area"));
    }

    @Test
    void fullConversionRequiresIntegratedMetadataForCustomDimensions() throws Exception {
        Path world = tempDir.resolve("full-world");
        Path definition = world.resolve("datapacks/custom/data/example/dimension/moon.json");
        Files.createDirectories(definition.getParent());
        Files.writeString(definition, "{}");
        ConverterAdapter.ConversionRequest request = new ConverterAdapter.ConversionRequest(
                world, tempDir.resolve("output"), "BEDROCK_1_21_80", null);

        assertThrows(IOException.class, () -> ChunkerCliAdapter.requireSupportedCustomDimensionShape(request));

        Files.writeString(world.resolve("custom_dimensions.chunker.json"), "{\"dimensions\":[]}");
        ChunkerCliAdapter.requireSupportedCustomDimensionShape(request);
    }
}
