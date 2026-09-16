package com.halokaryamedia.lazybuilder.world.conversion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeJavaAreaPrunerTest {
    private static final int SECTOR_BYTES = 4096;
    private static final int HEADER_BYTES = 8192;

    @TempDir Path tempDir;

    @Test
    void selectedNegativeChunkIsCopiedSectorForSectorAndUnselectedChunkIsAbsent() throws Exception {
        Path input = tempDir.resolve("input");
        Files.createDirectories(input);
        Files.writeString(input.resolve("level.dat"), "level");
        Files.createDirectories(input.resolve("data"));
        Files.writeString(input.resolve("data/map_1.dat"), "map");

        byte[] selectedPayload = sectorPayload((byte) 0x31);
        byte[] rejectedPayload = sectorPayload((byte) 0x72);
        Path region = input.resolve("region/r.-1.-1.mca");
        writeRegion(region, 0, selectedPayload, 111, 1, rejectedPayload, 222);

        Path entities = input.resolve("entities/r.-1.-1.mca");
        byte[] selectedEntityPayload = sectorPayload((byte) 0x45);
        writeRegion(entities, 0, selectedEntityPayload, 333, 1, sectorPayload((byte) 0x46), 444);

        Path poi = input.resolve("poi/r.-1.-1.mca");
        byte[] selectedPoiPayload = sectorPayload((byte) 0x50);
        writeRegion(poi, 0, selectedPoiPayload, 555, 1, sectorPayload((byte) 0x51), 666);

        Files.write(region.getParent().resolve("c.-32.-32.mcc"), new byte[]{1, 2, 3});
        Files.write(region.getParent().resolve("c.-31.-32.mcc"), new byte[]{4, 5, 6});

        Path pruning = pruning("minecraft:overworld", -32, -32, -32, -32);
        Path output = tempDir.resolve("output");
        NativeJavaAreaPruner.exportSelectedArea(input, output, pruning);

        assertTrue(Files.isRegularFile(output.resolve("level.dat")));
        assertTrue(Files.isRegularFile(output.resolve("data/map_1.dat")));

        Path outputRegion = output.resolve("region/r.-1.-1.mca");
        assertTrue(Files.isRegularFile(outputRegion));
        assertArrayEquals(selectedPayload, readChunkSector(outputRegion, 0));
        assertEquals(111, readTimestamp(outputRegion, 0));
        assertEquals(0, readLocation(outputRegion, 1));
        assertFalse(containsPayload(outputRegion, rejectedPayload));

        Path outputEntities = output.resolve("entities/r.-1.-1.mca");
        assertArrayEquals(selectedEntityPayload, readChunkSector(outputEntities, 0));
        assertEquals(0, readLocation(outputEntities, 1));

        Path outputPoi = output.resolve("poi/r.-1.-1.mca");
        assertArrayEquals(selectedPoiPayload, readChunkSector(outputPoi, 0));
        assertEquals(0, readLocation(outputPoi, 1));

        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(output.resolve("region/c.-32.-32.mcc")));
        assertFalse(Files.exists(output.resolve("region/c.-31.-32.mcc")));
    }

    @Test
    void onlySelectedDimensionReceivesChunkOwnedData() throws Exception {
        Path input = tempDir.resolve("dimensions-input");
        Files.createDirectories(input);
        Files.writeString(input.resolve("level.dat"), "level");
        writeRegion(input.resolve("region/r.0.0.mca"), 0, sectorPayload((byte) 1), 1, 1, sectorPayload((byte) 2), 2);
        writeRegion(input.resolve("DIM-1/region/r.0.0.mca"), 0, sectorPayload((byte) 3), 3, 1, sectorPayload((byte) 4), 4);
        writeRegion(input.resolve("DIM1/region/r.0.0.mca"), 0, sectorPayload((byte) 5), 5, 1, sectorPayload((byte) 6), 6);

        Path output = tempDir.resolve("dimensions-output");
        NativeJavaAreaPruner.exportSelectedArea(
                input, output, pruning("minecraft:the_nether", 0, 0, 0, 0));

        assertFalse(Files.exists(output.resolve("region")));
        assertTrue(Files.isRegularFile(output.resolve("DIM-1/region/r.0.0.mca")));
        assertFalse(Files.exists(output.resolve("DIM1/region")));
        assertArrayEquals(sectorPayload((byte) 3), readChunkSector(output.resolve("DIM-1/region/r.0.0.mca"), 0));
    }

    @Test
    void pruningParserRequiresExactlyOneIncludedDimension() throws Exception {
        Path pruning = tempDir.resolve("bad-pruning.json");
        Files.writeString(pruning, "{\"configs\":{" +
                "\"minecraft:overworld\":{\"include\":true,\"regions\":[{\"minChunkX\":0,\"minChunkZ\":0,\"maxChunkX\":0,\"maxChunkZ\":0}]}," +
                "\"minecraft:the_nether\":{\"include\":true,\"regions\":[{\"minChunkX\":0,\"minChunkZ\":0,\"maxChunkX\":0,\"maxChunkZ\":0}]}" +
                "}}");

        org.junit.jupiter.api.Assertions.assertThrows(
                java.io.IOException.class, () -> NativeJavaAreaPruner.readSelectedArea(pruning));
    }

    private Path pruning(String dimension, int minX, int minZ, int maxX, int maxZ) throws Exception {
        Path file = tempDir.resolve("pruning-" + dimension.substring(dimension.indexOf(':') + 1) + ".json");
        String selected = "{\"include\":true,\"regions\":[{\"minChunkX\":" + minX
                + ",\"minChunkZ\":" + minZ + ",\"maxChunkX\":" + maxX + ",\"maxChunkZ\":" + maxZ + "}]}";
        String excluded = "{\"include\":false,\"regions\":[{\"minChunkX\":-2147483648,\"minChunkZ\":-2147483648,"
                + "\"maxChunkX\":2147483647,\"maxChunkZ\":2147483647}]}";
        String json = "{\"configs\":{" +
                "\"minecraft:overworld\":" + (dimension.equals("minecraft:overworld") ? selected : excluded) + "," +
                "\"minecraft:the_nether\":" + (dimension.equals("minecraft:the_nether") ? selected : excluded) + "," +
                "\"minecraft:the_end\":" + (dimension.equals("minecraft:the_end") ? selected : excluded) +
                "}}";
        Files.writeString(file, json);
        return file;
    }

    private static void writeRegion(
            Path file,
            int firstIndex,
            byte[] firstPayload,
            int firstTimestamp,
            int secondIndex,
            byte[] secondPayload,
            int secondTimestamp
    ) throws Exception {
        Files.createDirectories(file.getParent());
        try (RandomAccessFile region = new RandomAccessFile(file.toFile(), "rw")) {
            region.setLength(4L * SECTOR_BYTES);
            writeLocation(region, firstIndex, 2, 1);
            writeLocation(region, secondIndex, 3, 1);
            writeTimestamp(region, firstIndex, firstTimestamp);
            writeTimestamp(region, secondIndex, secondTimestamp);
            region.seek(2L * SECTOR_BYTES);
            region.write(firstPayload);
            region.seek(3L * SECTOR_BYTES);
            region.write(secondPayload);
        }
    }

    private static void writeLocation(RandomAccessFile file, int index, int sector, int count) throws Exception {
        file.seek((long) index * 4L);
        file.writeInt((sector << 8) | count);
    }

    private static void writeTimestamp(RandomAccessFile file, int index, int timestamp) throws Exception {
        file.seek(SECTOR_BYTES + (long) index * 4L);
        file.writeInt(timestamp);
    }

    private static int readLocation(Path file, int index) throws Exception {
        try (RandomAccessFile region = new RandomAccessFile(file.toFile(), "r")) {
            region.seek((long) index * 4L);
            return region.readInt();
        }
    }

    private static int readTimestamp(Path file, int index) throws Exception {
        try (RandomAccessFile region = new RandomAccessFile(file.toFile(), "r")) {
            region.seek(SECTOR_BYTES + (long) index * 4L);
            return region.readInt();
        }
    }

    private static byte[] readChunkSector(Path file, int index) throws Exception {
        try (RandomAccessFile region = new RandomAccessFile(file.toFile(), "r")) {
            region.seek((long) index * 4L);
            int location = region.readInt();
            int sector = (location >>> 8) & 0x00FF_FFFF;
            int count = location & 0xFF;
            byte[] output = new byte[count * SECTOR_BYTES];
            region.seek((long) sector * SECTOR_BYTES);
            region.readFully(output);
            return output;
        }
    }

    private static boolean containsPayload(Path file, byte[] payload) throws Exception {
        byte[] bytes = Files.readAllBytes(file);
        outer:
        for (int i = HEADER_BYTES; i <= bytes.length - payload.length; i++) {
            for (int j = 0; j < payload.length; j++) {
                if (bytes[i + j] != payload[j]) continue outer;
            }
            return true;
        }
        return false;
    }

    private static byte[] sectorPayload(byte marker) {
        byte[] payload = new byte[SECTOR_BYTES];
        Arrays.fill(payload, marker);
        return payload;
    }
}
