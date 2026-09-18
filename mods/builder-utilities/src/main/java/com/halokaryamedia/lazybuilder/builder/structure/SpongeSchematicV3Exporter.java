package com.halokaryamedia.lazybuilder.builder.structure;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Strict Sponge Schematic v3 exporter matching {@link SpongeSchematicV3Importer}. */
public final class SpongeSchematicV3Exporter {
    private static final long MAX_VOLUME = 16_777_216L;

    private SpongeSchematicV3Exporter() {}

    public static void writeCompressed(
            SpongeSchematicImport schematicImport,
            Path path
    ) throws IOException {
        Objects.requireNonNull(schematicImport, "schematicImport");
        Objects.requireNonNull(path, "path");

        StructureSnapshot snapshot = schematicImport.snapshot();
        var bounds = snapshot.localBounds();
        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int length = Math.toIntExact((long) bounds.maxZ() - bounds.minZ() + 1L);
        validateDimension(width, "width");
        validateDimension(height, "height");
        validateDimension(length, "length");

        long volume = Math.multiplyExact(Math.multiplyExact((long) width, height), length);
        if (volume > MAX_VOLUME) {
            throw new IOException("Schematic volume exceeds " + MAX_VOLUME + " blocks");
        }
        int count = Math.toIntExact(volume);

        NbtCompound schematic = new NbtCompound();
        schematic.putInt("Version", 3);
        schematic.putInt("DataVersion", schematicImport.dataVersion());
        schematic.putShort("Width", (short) width);
        schematic.putShort("Height", (short) height);
        schematic.putShort("Length", (short) length);
        schematic.putIntArray("Offset", new int[]{
                Math.addExact(schematicImport.offsetX(), bounds.minX()),
                Math.addExact(schematicImport.offsetY(), bounds.minY()),
                Math.addExact(schematicImport.offsetZ(), bounds.minZ())
        });
        if (schematicImport.hasMetadata()) {
            schematic.put("Metadata", deserialize(schematicImport.metadataPayload()));
        }

        schematic.put("Blocks", writeBlocks(snapshot, bounds.minX(), bounds.minY(), bounds.minZ(),
                width, height, length, count));

        if (!snapshot.biomes().isEmpty()) {
            schematic.put("Biomes", writeBiomes(snapshot, bounds.minX(), bounds.minY(), bounds.minZ(),
                    width, height, length, count));
        }
        if (!snapshot.entities().isEmpty()) {
            schematic.put("Entities", writeEntities(
                    snapshot, bounds.minX(), bounds.minY(), bounds.minZ()));
        }

        NbtCompound root = new NbtCompound();
        root.put("Schematic", schematic);

        Path absolute = path.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent == null) {
            throw new IOException("Schematic export path has no parent directory");
        }
        Files.createDirectories(parent);
        Path staging = Files.createTempFile(
                parent,
                absolute.getFileName().toString() + ".",
                ".tmp"
        );
        boolean published = false;
        try {
            NbtIo.writeCompressed(root, staging);
            try {
                Files.move(
                        staging,
                        absolute,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(
                        staging,
                        absolute,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
            published = true;
        } finally {
            if (!published) {
                Files.deleteIfExists(staging);
            }
        }
    }

    private static NbtCompound writeBlocks(
            StructureSnapshot snapshot,
            int minX,
            int minY,
            int minZ,
            int width,
            int height,
            int length,
            int count
    ) throws IOException {
        Map<LocalKey, String> states = new HashMap<>();
        for (StructureBlock block : snapshot.blocks()) {
            states.put(new LocalKey(
                    block.x() - minX,
                    block.y() - minY,
                    block.z() - minZ
            ), block.blockState());
        }

        LinkedHashMap<String, Integer> palette = new LinkedHashMap<>();
        int[] indexes = new int[count];
        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    String state = states.getOrDefault(
                            new LocalKey(x, y, z), "minecraft:air");
                    indexes[index++] = palette.computeIfAbsent(state, ignored -> palette.size());
                }
            }
        }

        NbtCompound result = new NbtCompound();
        result.put("Palette", palette(palette));
        result.putByteArray("Data", encodeVarInts(indexes));

        if (!snapshot.blockEntities().isEmpty()) {
            NbtList list = new NbtList();
            for (StructureBlockEntity blockEntity : snapshot.blockEntities()) {
                NbtCompound payload = deserialize(blockEntity.payload());
                String id = payload.getString("Id");
                if (id.isBlank()) id = payload.getString("id");
                if (id.isBlank()) {
                    throw new IOException("BlockEntity payload has no Id");
                }

                NbtCompound entry = new NbtCompound();
                entry.putString("Id", id);
                entry.putIntArray("Pos", new int[]{
                        blockEntity.x() - minX,
                        blockEntity.y() - minY,
                        blockEntity.z() - minZ
                });

                NbtCompound data = new NbtCompound();
                for (String key : payload.getKeys()) {
                    if (key.equals("Id") || key.equals("id")
                            || key.equals("Pos") || key.equals("pos")
                            || key.equals("x") || key.equals("y") || key.equals("z")
                            || key.equals("Data")) {
                        continue;
                    }
                    var value = payload.get(key);
                    if (value != null) data.put(key, value.copy());
                }

                var nestedData = payload.get("Data");
                if (nestedData instanceof NbtCompound nested) {
                    for (String key : nested.getKeys()) {
                        var value = nested.get(key);
                        if (value == null) continue;
                        var existing = data.get(key);
                        if (existing != null && !existing.equals(value)) {
                            throw new IOException(
                                    "BlockEntity payload contains conflicting flat/Data field: "
                                            + key);
                        }
                        data.put(key, value.copy());
                    }
                }
                if (!data.isEmpty()) entry.put("Data", data);
                list.add(entry);
            }
            result.put("BlockEntities", list);
        }
        return result;
    }

    private static NbtCompound writeBiomes(
            StructureSnapshot snapshot,
            int minX,
            int minY,
            int minZ,
            int width,
            int height,
            int length,
            int count
    ) throws IOException {
        if (snapshot.biomes().size() != count) {
            throw new IOException(
                    "Sponge v3 biome export requires complete 3D biome coverage: expected "
                            + count + ", got " + snapshot.biomes().size());
        }

        Map<LocalKey, String> biomes = new HashMap<>();
        for (StructureBiomeSample sample : snapshot.biomes()) {
            LocalKey key = new LocalKey(
                    sample.x() - minX,
                    sample.y() - minY,
                    sample.z() - minZ);
            String id = new String(sample.payload(), StandardCharsets.UTF_8);
            if (id.isBlank()) throw new IOException("Biome payload must contain a non-blank resource id");
            if (biomes.put(key, id) != null) {
                throw new IOException("Duplicate biome sample during export");
            }
        }

        LinkedHashMap<String, Integer> palette = new LinkedHashMap<>();
        int[] indexes = new int[count];
        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    String biome = biomes.get(new LocalKey(x, y, z));
                    if (biome == null) {
                        throw new IOException("Missing biome sample at " + x + "," + y + "," + z);
                    }
                    indexes[index++] = palette.computeIfAbsent(
                            biome, ignored -> palette.size());
                }
            }
        }

        NbtCompound result = new NbtCompound();
        result.put("Palette", palette(palette));
        result.putByteArray("Data", encodeVarInts(indexes));
        return result;
    }

    private static NbtList writeEntities(
            StructureSnapshot snapshot,
            int minX,
            int minY,
            int minZ
    ) throws IOException {
        NbtList list = new NbtList();
        for (StructureEntity entity : snapshot.entities()) {
            NbtCompound payload = deserialize(entity.payload());
            String id = payload.getString("Id");
            if (id.isBlank()) id = payload.getString("id");
            if (id.isBlank()) {
                throw new IOException("Entity payload has no Id");
            }

            NbtCompound entry = new NbtCompound();
            entry.putString("Id", id);

            NbtList pos = new NbtList();
            pos.add(NbtDouble.of(entity.x() - minX));
            pos.add(NbtDouble.of(entity.y() - minY));
            pos.add(NbtDouble.of(entity.z() - minZ));
            entry.put("Pos", pos);

            NbtCompound data = new NbtCompound();
            for (String key : payload.getKeys()) {
                if (key.equals("Id") || key.equals("id")
                        || key.equals("Pos") || key.equals("pos")
                        || key.equals("Data")) {
                    continue;
                }
                var value = payload.get(key);
                if (value != null) data.put(key, value.copy());
            }

            // Backward compatibility for legacy internal payloads that still
            // contain a nested Data compound.
            var nestedData = payload.get("Data");
            if (nestedData instanceof NbtCompound nested) {
                for (String key : nested.getKeys()) {
                    var value = nested.get(key);
                    if (value == null) continue;
                    var existing = data.get(key);
                    if (existing != null && !existing.equals(value)) {
                        throw new IOException(
                                "Entity payload contains conflicting flat/Data field: " + key);
                    }
                    data.put(key, value.copy());
                }
            }
            if (!data.isEmpty()) entry.put("Data", data);
            list.add(entry);
        }
        return list;
    }

    private static NbtCompound palette(LinkedHashMap<String, Integer> entries) {
        NbtCompound palette = new NbtCompound();
        entries.forEach(palette::putInt);
        return palette;
    }

    static byte[] encodeVarInts(int[] values) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int value : values) {
            if (value < 0) throw new IllegalArgumentException("varint values must be >= 0");
            int remaining = value;
            do {
                int current = remaining & 0x7f;
                remaining >>>= 7;
                if (remaining != 0) current |= 0x80;
                output.write(current);
            } while (remaining != 0);
        }
        return output.toByteArray();
    }

    private static NbtCompound deserialize(byte[] payload) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            return NbtIo.readCompound(input);
        }
    }

    private static void validateDimension(int value, String label) throws IOException {
        if (value <= 0 || value > 0xffff) {
            throw new IOException("Schematic " + label + " must be in 1..65535");
        }
    }

    private record LocalKey(int x, int y, int z) {}
}
