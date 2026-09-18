package com.halokaryamedia.lazybuilder.builder.structure;

import net.minecraft.nbt.AbstractNbtNumber;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Strict Sponge Schematic v3 importer.
 *
 * <p>Block states remain canonical resource-location strings. Non-block data is
 * retained as opaque payloads for History extension adapters.</p>
 */
public final class SpongeSchematicV3Importer {
    private static final long MAX_NBT_BYTES = 256L * 1024L * 1024L;
    private static final long MAX_VOLUME = 16_777_216L;
    private static final int MAX_PALETTE = 1_000_000;

    private SpongeSchematicV3Importer() {}

    public static SpongeSchematicImport importCompressed(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        NbtCompound root = NbtIo.readCompressed(path, NbtSizeTracker.of(MAX_NBT_BYTES));
        NbtElement nested = root.get("Schematic");
        NbtCompound schematic = nested instanceof NbtCompound compound ? compound : root;
        return importCompound(schematic);
    }

    static SpongeSchematicImport importCompound(NbtCompound schematic) throws IOException {
        Objects.requireNonNull(schematic, "schematic");

        int version = schematic.getInt("Version");
        if (version != 3) {
            throw new IOException("Unsupported Sponge schematic version: " + version);
        }

        int width = Short.toUnsignedInt(schematic.getShort("Width"));
        int height = Short.toUnsignedInt(schematic.getShort("Height"));
        int length = Short.toUnsignedInt(schematic.getShort("Length"));
        if (width <= 0 || height <= 0 || length <= 0) {
            throw new IOException("Schematic dimensions must be > 0");
        }

        long volume;
        try {
            volume = Math.multiplyExact(Math.multiplyExact((long) width, height), length);
        } catch (ArithmeticException e) {
            throw new IOException("Schematic volume overflow", e);
        }
        if (volume > MAX_VOLUME) {
            throw new IOException("Schematic volume exceeds " + MAX_VOLUME + " blocks");
        }

        int[] offset = schematic.getIntArray("Offset");
        if (offset.length != 0 && offset.length != 3) {
            throw new IOException("Schematic Offset must contain exactly three integers");
        }

        NbtCompound blocks = schematic.getCompound("Blocks");
        NbtCompound blockPalette = blocks.getCompound("Palette");
        byte[] blockData = blocks.getByteArray("Data");
        String[] blockStates = invertPalette(blockPalette, "block");
        int[] blockIndexes = decodeVarInts(blockData, Math.toIntExact(volume), blockStates.length, "block");

        List<StructureBlock> structureBlocks = new ArrayList<>(Math.toIntExact(volume));
        for (int index = 0; index < blockIndexes.length; index++) {
            int x = index % width;
            int z = (index / width) % length;
            int y = index / (width * length);
            structureBlocks.add(new StructureBlock(x, y, z, blockStates[blockIndexes[index]]));
        }

        List<StructureBlockEntity> blockEntities = parseBlockEntities(blocks);
        List<StructureBiomeSample> biomes = parseBiomes(
                schematic, width, height, length, Math.toIntExact(volume));
        List<StructureEntity> entities = parseEntities(schematic);

        byte[] metadata = new byte[0];
        NbtElement metadataElement = schematic.get("Metadata");
        if (metadataElement != null) {
            if (!(metadataElement instanceof NbtCompound metadataCompound)) {
                throw new IOException("Schematic Metadata must be an NBT compound");
            }
            metadata = serialize(metadataCompound);
        }

        return new SpongeSchematicImport(
                new StructureSnapshot(structureBlocks, blockEntities, biomes, entities),
                offset.length == 3 ? offset[0] : 0,
                offset.length == 3 ? offset[1] : 0,
                offset.length == 3 ? offset[2] : 0,
                schematic.getInt("DataVersion"),
                metadata
        );
    }

    private static List<StructureBlockEntity> parseBlockEntities(NbtCompound blocks)
            throws IOException {
        NbtList list = blocks.getList("BlockEntities", NbtElement.COMPOUND_TYPE);
        List<StructureBlockEntity> result = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            NbtCompound entry = list.getCompound(i);
            String id = entry.getString("Id");
            if (id.isBlank()) {
                throw new IOException("BlockEntity Id must be a non-blank resource location");
            }
            int[] pos = entry.getIntArray("Pos");
            if (pos.length != 3) {
                throw new IOException("BlockEntity Pos must contain exactly three integers");
            }
            result.add(new StructureBlockEntity(
                    pos[0], pos[1], pos[2], serialize(entry)));
        }
        return List.copyOf(result);
    }

    private static List<StructureBiomeSample> parseBiomes(
            NbtCompound schematic,
            int width,
            int height,
            int length,
            int volume
    ) throws IOException {
        NbtElement biomesElement = schematic.get("Biomes");
        if (!(biomesElement instanceof NbtCompound biomes) || biomes.isEmpty()) {
            return List.of();
        }

        String[] palette = invertPalette(biomes.getCompound("Palette"), "biome");
        int[] indexes = decodeVarInts(biomes.getByteArray("Data"), volume, palette.length, "biome");
        List<StructureBiomeSample> result = new ArrayList<>(volume);

        for (int index = 0; index < indexes.length; index++) {
            int x = index % width;
            int z = (index / width) % length;
            int y = index / (width * length);
            result.add(new StructureBiomeSample(
                    x, y, z,
                    palette[indexes[index]].getBytes(StandardCharsets.UTF_8)
            ));
        }
        return List.copyOf(result);
    }

    private static List<StructureEntity> parseEntities(NbtCompound schematic)
            throws IOException {
        NbtList list = schematic.getList("Entities", NbtElement.COMPOUND_TYPE);
        List<StructureEntity> result = new ArrayList<>(list.size());

        for (int i = 0; i < list.size(); i++) {
            NbtCompound entry = list.getCompound(i);
            String id = entry.getString("Id");
            if (id.isBlank()) {
                throw new IOException("Entity Id must be a non-blank resource location");
            }
            NbtList position = entry.getList("Pos", NbtElement.DOUBLE_TYPE);
            if (position.size() != 3) {
                throw new IOException("Entity Pos must contain exactly three doubles");
            }
            result.add(new StructureEntity(
                    number(position.get(0), "entity x"),
                    number(position.get(1), "entity y"),
                    number(position.get(2), "entity z"),
                    serialize(entry)
            ));
        }
        return List.copyOf(result);
    }

    private static double number(NbtElement element, String label) throws IOException {
        if (!(element instanceof AbstractNbtNumber number)) {
            throw new IOException(label + " is not numeric");
        }
        return number.doubleValue();
    }

    private static String[] invertPalette(NbtCompound palette, String label) throws IOException {
        if (palette.isEmpty()) throw new IOException("Schematic " + label + " palette is empty");
        if (palette.getSize() > MAX_PALETTE) {
            throw new IOException("Schematic " + label + " palette exceeds " + MAX_PALETTE);
        }

        String[] result = new String[palette.getSize()];
        for (String state : palette.getKeys()) {
            int index = palette.getInt(state);
            if (index < 0 || index >= result.length) {
                throw new IOException("Schematic " + label + " palette index out of range: " + index);
            }
            if (result[index] != null) {
                throw new IOException("Duplicate Schematic " + label + " palette index: " + index);
            }
            if (state == null || state.isBlank()) {
                throw new IOException("Schematic " + label + " palette contains blank id");
            }
            result[index] = state;
        }
        for (int i = 0; i < result.length; i++) {
            if (result[i] == null) {
                throw new IOException("Schematic " + label + " palette is sparse at index " + i);
            }
        }
        return result;
    }

    static int[] decodeVarInts(
            byte[] data,
            int expectedCount,
            int paletteSize,
            String label
    ) throws IOException {
        Objects.requireNonNull(data, "data");
        if (expectedCount < 0) throw new IllegalArgumentException("expectedCount must be >= 0");
        if (paletteSize <= 0) throw new IllegalArgumentException("paletteSize must be > 0");

        int[] result = new int[expectedCount];
        int inputIndex = 0;

        for (int outputIndex = 0; outputIndex < expectedCount; outputIndex++) {
            int value = 0;
            int shift = 0;
            while (true) {
                if (inputIndex >= data.length) {
                    throw new IOException("Truncated Schematic " + label + " varint data");
                }
                int current = data[inputIndex++] & 0xff;
                value |= (current & 0x7f) << shift;
                if ((current & 0x80) == 0) break;
                shift += 7;
                if (shift >= 35) {
                    throw new IOException("Oversized Schematic " + label + " varint");
                }
            }
            if (value < 0 || value >= paletteSize) {
                throw new IOException(
                        "Schematic " + label + " palette reference out of range: " + value);
            }
            result[outputIndex] = value;
        }

        if (inputIndex != data.length) {
            throw new IOException("Schematic " + label + " data has trailing varints");
        }
        return result;
    }

    private static byte[] serialize(NbtCompound compound) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            NbtIo.writeCompound(compound, output);
        }
        return bytes.toByteArray();
    }
}
