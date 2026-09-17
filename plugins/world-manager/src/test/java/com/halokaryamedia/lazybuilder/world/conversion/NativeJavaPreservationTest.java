package com.halokaryamedia.lazybuilder.world.conversion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeJavaPreservationTest {
    @TempDir Path tempDir;

    @Test
    void fullNativeCopyPreservesChunkOwnedBytesAcrossVanillaDimensions() throws Exception {
        Path input = tempDir.resolve("input");
        byte[] overworldRegion = payload(8192, 11);
        byte[] overworldEntities = payload(8192, 17);
        byte[] overworldPoi = payload(8192, 23);
        byte[] netherRegion = payload(8192, 31);
        byte[] endRegion = payload(8192, 47);

        Files.createDirectories(input.resolve("region"));
        Files.createDirectories(input.resolve("entities"));
        Files.createDirectories(input.resolve("poi"));
        Files.createDirectories(input.resolve("DIM-1/region"));
        Files.createDirectories(input.resolve("DIM1/region"));
        Files.createDirectories(input.resolve("data"));
        Files.write(input.resolve("level.dat"), payload(64, 3));
        Files.write(input.resolve("session.lock"), payload(16, 5));
        Files.write(input.resolve("data/map_0.dat"), payload(128, 7));
        Files.write(input.resolve("region/r.0.0.mca"), overworldRegion);
        Files.write(input.resolve("entities/r.0.0.mca"), overworldEntities);
        Files.write(input.resolve("poi/r.0.0.mca"), overworldPoi);
        Files.write(input.resolve("DIM-1/region/r.-1.0.mca"), netherRegion);
        Files.write(input.resolve("DIM1/region/r.2.-3.mca"), endRegion);

        Path output = tempDir.resolve("output");
        ChunkerCliAdapter.copyFullSameFormatOutput(input, output);

        assertArrayEquals(overworldRegion, Files.readAllBytes(output.resolve("region/r.0.0.mca")));
        assertArrayEquals(overworldEntities, Files.readAllBytes(output.resolve("entities/r.0.0.mca")));
        assertArrayEquals(overworldPoi, Files.readAllBytes(output.resolve("poi/r.0.0.mca")));
        assertArrayEquals(netherRegion, Files.readAllBytes(output.resolve("DIM-1/region/r.-1.0.mca")));
        assertArrayEquals(endRegion, Files.readAllBytes(output.resolve("DIM1/region/r.2.-3.mca")));
        assertTrue(Files.isRegularFile(output.resolve("data/map_0.dat")));
        assertFalse(Files.exists(output.resolve("session.lock")));
    }

    @Test
    void metadataOnlySeedCannotExposeChunkOwnedDataToMetadataConversion() throws Exception {
        Path input = tempDir.resolve("metadata-input");
        Files.createDirectories(input.resolve("region"));
        Files.createDirectories(input.resolve("entities"));
        Files.createDirectories(input.resolve("poi"));
        Files.createDirectories(input.resolve("DIM-1/region"));
        Files.write(input.resolve("level.dat"), payload(128, 13));
        Files.write(input.resolve("level.dat_old"), payload(64, 19));
        Files.write(input.resolve("region/r.0.0.mca"), payload(8192, 29));
        Files.write(input.resolve("entities/r.0.0.mca"), payload(8192, 37));
        Files.write(input.resolve("poi/r.0.0.mca"), payload(8192, 41));
        Files.write(input.resolve("DIM-1/region/r.0.0.mca"), payload(8192, 43));

        Path metadata = tempDir.resolve("metadata-seed");
        ChunkerCliAdapter.seedMetadataOnlyInput(input, metadata);

        assertArrayEquals(Files.readAllBytes(input.resolve("level.dat")),
                Files.readAllBytes(metadata.resolve("level.dat")));
        assertArrayEquals(Files.readAllBytes(input.resolve("level.dat_old")),
                Files.readAllBytes(metadata.resolve("level.dat_old")));
        assertFalse(Files.exists(metadata.resolve("region")));
        assertFalse(Files.exists(metadata.resolve("entities")));
        assertFalse(Files.exists(metadata.resolve("poi")));
        assertFalse(Files.exists(metadata.resolve("DIM-1")));
    }

    @Test
    void runtimeFreeNativeEligibilityRequiresExplicitManagedNativeInput() {
        ConverterAdapter.ConversionRequest managedFull = new ConverterAdapter.ConversionRequest(
                Path.of("/input"), Path.of("/output"), "JAVA_1_21_4",
                null, null, null, true);
        ConverterAdapter.ConversionRequest unmanagedFull = new ConverterAdapter.ConversionRequest(
                Path.of("/input"), Path.of("/output"), "JAVA_1_21_4",
                null, null, null, false);
        ConverterAdapter.ConversionRequest managedBedrock = new ConverterAdapter.ConversionRequest(
                Path.of("/input"), Path.of("/output"), "BEDROCK_1_21_80",
                null, null, null, false);

        assertTrue(ChunkerCliAdapter.canUseRuntimeFreeNativePath(managedFull));
        assertFalse(ChunkerCliAdapter.canUseRuntimeFreeNativePath(unmanagedFull));
        assertFalse(ChunkerCliAdapter.canUseRuntimeFreeNativePath(managedBedrock));
    }

    private static byte[] payload(int size, int seed) {
        byte[] bytes = new byte[size];
        for (int index = 0; index < size; index++) {
            bytes[index] = (byte) ((seed + index * 31) & 0xFF);
        }
        return bytes;
    }
}
