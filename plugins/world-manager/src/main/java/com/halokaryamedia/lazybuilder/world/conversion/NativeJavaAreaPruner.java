package com.halokaryamedia.lazybuilder.world.conversion;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lossless Java Anvil slicer for canonical Java 1.21.4 Selected Area exports.
 *
 * <p>The selected chunk payloads are copied sector-for-sector from region, entity,
 * and POI MCA files. No chunk NBT is decoded or re-encoded, so unknown tags,
 * scheduled ticks, structure references and other same-format data survive exactly.
 * Non-chunk world metadata is seeded separately by {@link ChunkerCliAdapter}.</p>
 */
final class NativeJavaAreaPruner {
    private static final int SECTOR_BYTES = 4096;
    private static final int HEADER_BYTES = 8192;
    private static final int CHUNKS_PER_REGION = 32;
    private static final Pattern REGION_FILE = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
    private static final Pattern EXTERNAL_CHUNK_FILE = Pattern.compile("c\\.(-?\\d+)\\.(-?\\d+)\\.mcc");
    private static final Map<String, String> DIMENSION_DIRECTORIES = Map.of(
            "minecraft:overworld", "",
            "minecraft:the_nether", "DIM-1",
            "minecraft:the_end", "DIM1"
    );

    private NativeJavaAreaPruner() {}

    static void exportSelectedArea(Path inputDirectory, Path outputDirectory, Path pruningSettings) throws IOException {
        Path input = Objects.requireNonNull(inputDirectory, "inputDirectory").toAbsolutePath().normalize();
        Path output = Objects.requireNonNull(outputDirectory, "outputDirectory").toAbsolutePath().normalize();
        Area area = readSelectedArea(Objects.requireNonNull(pruningSettings, "pruningSettings"));

        if (ChunkerCliAdapter.containsCustomDimensionDefinitions(input) || Files.isDirectory(input.resolve("dimensions"))) {
            throw new IOException("Selected Area native export does not yet support worlds with custom dimensions safely");
        }

        ChunkerCliAdapter.seedSameFormatOutput(input, output);

        String relativeDimension = DIMENSION_DIRECTORIES.get(area.dimensionId());
        if (relativeDimension == null) {
            throw new IOException("Selected Area native export received an unsupported dimension: " + area.dimensionId());
        }
        Path sourceDimension = relativeDimension.isEmpty() ? input : input.resolve(relativeDimension);
        Path outputDimension = relativeDimension.isEmpty() ? output : output.resolve(relativeDimension);

        copySelectedAnvilDirectory(sourceDimension.resolve("region"), outputDimension.resolve("region"), area);
        copySelectedAnvilDirectory(sourceDimension.resolve("entities"), outputDimension.resolve("entities"), area);
        copySelectedAnvilDirectory(sourceDimension.resolve("poi"), outputDimension.resolve("poi"), area);
    }

    static Area readSelectedArea(Path pruningSettings) throws IOException {
        Path file = pruningSettings.toAbsolutePath().normalize();
        if (!Files.isRegularFile(file)) throw new IOException("Pruning settings are missing: " + file);

        JsonObject root;
        try {
            root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        } catch (RuntimeException invalid) {
            throw new IOException("Pruning settings are invalid", invalid);
        }
        JsonObject configs = root.getAsJsonObject("configs");
        if (configs == null) throw new IOException("Pruning settings have no dimension configs");

        Area selected = null;
        for (Map.Entry<String, JsonElement> entry : configs.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject config = entry.getValue().getAsJsonObject();
            boolean include = config.has("include") && config.get("include").getAsBoolean();
            if (!include) continue;
            JsonArray regions = config.getAsJsonArray("regions");
            if (regions == null || regions.size() != 1 || !regions.get(0).isJsonObject()) {
                throw new IOException("Selected Area pruning must contain exactly one included region");
            }
            if (selected != null) throw new IOException("Selected Area pruning contains more than one included dimension");
            JsonObject region = regions.get(0).getAsJsonObject();
            selected = new Area(
                    entry.getKey(),
                    requiredInt(region, "minChunkX"),
                    requiredInt(region, "minChunkZ"),
                    requiredInt(region, "maxChunkX"),
                    requiredInt(region, "maxChunkZ")
            );
        }
        if (selected == null) throw new IOException("Selected Area pruning contains no included dimension");
        return selected;
    }

    private static int requiredInt(JsonObject object, String key) throws IOException {
        if (!object.has(key)) throw new IOException("Selected Area pruning is missing " + key);
        try {
            return object.get(key).getAsInt();
        } catch (RuntimeException invalid) {
            throw new IOException("Selected Area pruning has invalid " + key, invalid);
        }
    }

    private static void copySelectedAnvilDirectory(Path sourceDirectory, Path outputDirectory, Area area) throws IOException {
        if (Files.notExists(sourceDirectory)) return;
        if (!Files.isDirectory(sourceDirectory) || Files.isSymbolicLink(sourceDirectory)) {
            throw new IOException("Anvil source directory is unsafe: " + sourceDirectory);
        }

        try (var children = Files.list(sourceDirectory)) {
            for (Path source : children.toList()) {
                if (Files.isSymbolicLink(source) || !Files.isRegularFile(source)) {
                    throw new IOException("Unexpected entry in Anvil directory: " + source.getFileName());
                }
                String name = source.getFileName().toString();
                Matcher regionMatcher = REGION_FILE.matcher(name);
                if (regionMatcher.matches()) {
                    int regionX = parseCoordinate(regionMatcher.group(1), name);
                    int regionZ = parseCoordinate(regionMatcher.group(2), name);
                    copySelectedRegionFile(source, outputDirectory.resolve(name), regionX, regionZ, area);
                    continue;
                }
                Matcher externalMatcher = EXTERNAL_CHUNK_FILE.matcher(name);
                if (externalMatcher.matches()) {
                    int chunkX = parseCoordinate(externalMatcher.group(1), name);
                    int chunkZ = parseCoordinate(externalMatcher.group(2), name);
                    if (area.contains(chunkX, chunkZ)) {
                        Files.createDirectories(outputDirectory);
                        Files.copy(source, outputDirectory.resolve(name), StandardCopyOption.COPY_ATTRIBUTES);
                    }
                    continue;
                }
                throw new IOException("Unsupported file in Anvil directory: " + name);
            }
        }
    }

    private static int parseCoordinate(String value, String fileName) throws IOException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException invalid) {
            throw new IOException("Invalid Anvil coordinate in " + fileName, invalid);
        }
    }

    private static void copySelectedRegionFile(
            Path source,
            Path target,
            int regionX,
            int regionZ,
            Area area
    ) throws IOException {
        if (Files.size(source) < HEADER_BYTES) {
            throw new IOException("Anvil region file is truncated: " + source.getFileName());
        }
        if (!area.intersectsRegion(regionX, regionZ)) return;

        Files.createDirectories(target.getParent());
        boolean wroteAny = false;
        try (RandomAccessFile input = new RandomAccessFile(source.toFile(), "r");
             RandomAccessFile output = new RandomAccessFile(target.toFile(), "rw")) {
            output.setLength(HEADER_BYTES);
            int nextSector = 2;

            for (int index = 0; index < 1024; index++) {
                int localX = index & 31;
                int localZ = (index >>> 5) & 31;
                int chunkX = Math.addExact(Math.multiplyExact(regionX, CHUNKS_PER_REGION), localX);
                int chunkZ = Math.addExact(Math.multiplyExact(regionZ, CHUNKS_PER_REGION), localZ);
                if (!area.contains(chunkX, chunkZ)) continue;

                input.seek((long) index * 4L);
                int location = input.readInt();
                int sourceSector = (location >>> 8) & 0x00FF_FFFF;
                int sectorCount = location & 0xFF;
                if (sourceSector == 0 || sectorCount == 0) continue;

                long sourceOffset = Math.multiplyExact((long) sourceSector, SECTOR_BYTES);
                long byteCount = Math.multiplyExact((long) sectorCount, SECTOR_BYTES);
                if (sourceOffset < HEADER_BYTES || sourceOffset + byteCount > input.length()) {
                    throw new IOException("Anvil chunk location is invalid in " + source.getFileName()
                            + " at chunk " + chunkX + "," + chunkZ);
                }
                if (nextSector > 0x00FF_FFFF) {
                    throw new IOException("Pruned Anvil region exceeded location-table limits");
                }

                byte[] payload = new byte[Math.toIntExact(byteCount)];
                input.seek(sourceOffset);
                input.readFully(payload);
                output.seek((long) nextSector * SECTOR_BYTES);
                output.write(payload);

                output.seek((long) index * 4L);
                output.writeInt((nextSector << 8) | sectorCount);
                input.seek(SECTOR_BYTES + (long) index * 4L);
                int timestamp = input.readInt();
                output.seek(SECTOR_BYTES + (long) index * 4L);
                output.writeInt(timestamp);

                nextSector = Math.addExact(nextSector, sectorCount);
                wroteAny = true;
            }
            output.setLength((long) nextSector * SECTOR_BYTES);
        } catch (ArithmeticException overflow) {
            Files.deleteIfExists(target);
            throw new IOException("Anvil coordinate or size exceeded supported range", overflow);
        } catch (IOException failure) {
            Files.deleteIfExists(target);
            throw failure;
        }

        if (!wroteAny) Files.deleteIfExists(target);
    }

    record Area(String dimensionId, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
        Area {
            Objects.requireNonNull(dimensionId, "dimensionId");
            if (minChunkX > maxChunkX || minChunkZ > maxChunkZ) {
                throw new IllegalArgumentException("Invalid Selected Area chunk bounds");
            }
        }

        boolean contains(int chunkX, int chunkZ) {
            return chunkX >= minChunkX && chunkX <= maxChunkX
                    && chunkZ >= minChunkZ && chunkZ <= maxChunkZ;
        }

        boolean intersectsRegion(int regionX, int regionZ) {
            long firstX = (long) regionX * CHUNKS_PER_REGION;
            long firstZ = (long) regionZ * CHUNKS_PER_REGION;
            long lastX = firstX + CHUNKS_PER_REGION - 1L;
            long lastZ = firstZ + CHUNKS_PER_REGION - 1L;
            return lastX >= minChunkX && firstX <= maxChunkX
                    && lastZ >= minChunkZ && firstZ <= maxChunkZ;
        }
    }
}
