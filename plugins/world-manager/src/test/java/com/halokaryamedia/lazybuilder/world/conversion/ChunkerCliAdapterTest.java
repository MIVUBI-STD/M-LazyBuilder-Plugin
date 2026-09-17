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
    void nativeAreaPreservationRequiresExplicitManagedInputAuthority() {
        ChunkerCliAdapter adapter = new ChunkerCliAdapter(
                Path.of("/java/bin/java"),
                2048,
                Duration.ofSeconds(10),
                Duration.ofMinutes(30),
                new OnDemandProcessRunner()
        );

        ConverterAdapter.ConversionRequest genericNativeArea = new ConverterAdapter.ConversionRequest(
                Path.of("/input"),
                Path.of("/output"),
                "java_1_21_4",
                Path.of("/tmp/pruning.json")
        );
        assertFalse(genericNativeArea.preserveNativeInput());
        assertFalse(genericNativeArea.keepOriginalNbt());
        assertFalse(adapter.buildConversionCommand(
                Path.of("/runtime/converter.jar"), genericNativeArea).contains("-k"));

        ConverterAdapter.ConversionRequest managedNativeArea = new ConverterAdapter.ConversionRequest(
                Path.of("/input"),
                Path.of("/output"),
                "java_1_21_4",
                Path.of("/tmp/pruning.json"),
                null,
                null,
                true
        );
        assertTrue(managedNativeArea.preserveNativeInput());
        assertTrue(managedNativeArea.keepOriginalNbt());
        assertTrue(adapter.buildConversionCommand(
                Path.of("/runtime/converter.jar"), managedNativeArea).contains("-k"));

        ConverterAdapter.ConversionRequest crossVersionArea = new ConverterAdapter.ConversionRequest(
                Path.of("/input"),
                Path.of("/output"),
                "java_1_20_6",
                Path.of("/tmp/pruning.json")
        );
        assertFalse(crossVersionArea.keepOriginalNbt());
        assertFalse(adapter.buildConversionCommand(
                Path.of("/runtime/converter.jar"), crossVersionArea).contains("-k"));
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

    @Test
    void bedrockOutputRequiresConcreteLevelDbData() throws Exception {
        Path output = tempDir.resolve("bedrock-output");
        Files.createDirectories(output.resolve("db"));
        Files.writeString(output.resolve("level.dat"), "level");

        IOException emptyDb = assertThrows(IOException.class,
                () -> ChunkerCliAdapter.validateOutputDirectory(output, "BEDROCK_1_21_80"));
        assertTrue(emptyDb.getMessage().contains("LevelDB"));

        Files.write(output.resolve("db/LOCK"), new byte[0]);
        assertThrows(IOException.class,
                () -> ChunkerCliAdapter.validateOutputDirectory(output, "BEDROCK_1_21_80"));

        Files.writeString(output.resolve("db/CURRENT"), "MANIFEST-000001\n");
        ChunkerCliAdapter.validateOutputDirectory(output, "BEDROCK_1_21_80");
    }

    @Test
    void javaOutputDoesNotRequireChunkDataForValidEmptyWorld() throws Exception {
        Path output = tempDir.resolve("java-output");
        Files.createDirectories(output);
        Files.writeString(output.resolve("level.dat"), "level");

        ChunkerCliAdapter.validateOutputDirectory(output, "JAVA_1_21_4");
    }

    @Test
    void sameFormatSeedCopiesWorldMetadataButNotChunkOwnedData() throws Exception {
        Path input = tempDir.resolve("seed-input");
        Files.createDirectories(input.resolve("region"));
        Files.createDirectories(input.resolve("entities"));
        Files.createDirectories(input.resolve("poi"));
        Files.createDirectories(input.resolve("DIM-1/region"));
        Files.createDirectories(input.resolve("DIM-1/entities"));
        Files.createDirectories(input.resolve("DIM-1/poi"));
        Files.createDirectories(input.resolve("DIM1/region"));
        Files.createDirectories(input.resolve("data"));
        Files.createDirectories(input.resolve("datapacks/example"));
        Files.createDirectories(input.resolve("playerdata"));

        Files.writeString(input.resolve("level.dat"), "level");
        Files.writeString(input.resolve("session.lock"), "lock");
        Files.writeString(input.resolve("data/map_0.dat"), "map");
        Files.writeString(input.resolve("datapacks/example/pack.mcmeta"), "{}");
        Files.writeString(input.resolve("playerdata/player.dat"), "player");
        Files.writeString(input.resolve("region/r.0.0.mca"), "chunk");
        Files.writeString(input.resolve("entities/r.0.0.mca"), "entities");
        Files.writeString(input.resolve("poi/r.0.0.mca"), "poi");
        Files.writeString(input.resolve("DIM-1/region/r.0.0.mca"), "nether");
        Files.writeString(input.resolve("DIM-1/entities/r.0.0.mca"), "nether-entities");
        Files.writeString(input.resolve("DIM-1/poi/r.0.0.mca"), "nether-poi");
        Files.writeString(input.resolve("DIM1/region/r.0.0.mca"), "end");

        Path output = tempDir.resolve("seed-output");
        ChunkerCliAdapter.seedSameFormatOutput(input, output);

        assertTrue(Files.isRegularFile(output.resolve("level.dat")));
        assertTrue(Files.isRegularFile(output.resolve("data/map_0.dat")));
        assertTrue(Files.isRegularFile(output.resolve("datapacks/example/pack.mcmeta")));
        assertTrue(Files.isRegularFile(output.resolve("playerdata/player.dat")));
        assertFalse(Files.exists(output.resolve("session.lock")));
        assertFalse(Files.exists(output.resolve("region")));
        assertFalse(Files.exists(output.resolve("entities")));
        assertFalse(Files.exists(output.resolve("poi")));
        assertFalse(Files.exists(output.resolve("DIM-1/region")));
        assertFalse(Files.exists(output.resolve("DIM-1/entities")));
        assertFalse(Files.exists(output.resolve("DIM-1/poi")));
        assertFalse(Files.exists(output.resolve("DIM1/region")));
    }
}
