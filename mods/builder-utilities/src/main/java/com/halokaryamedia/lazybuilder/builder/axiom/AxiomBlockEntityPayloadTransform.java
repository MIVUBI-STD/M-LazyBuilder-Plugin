package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.structure.BlockEntityPayloadTransform;
import com.halokaryamedia.lazybuilder.builder.structure.StructurePlacement;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Canonicalizes block-entity NBT and rotates/mirrors only known embedded entity
 * templates. Root block-entity orientation remains owned by the BlockState transform.
 */
public final class AxiomBlockEntityPayloadTransform implements BlockEntityPayloadTransform {
    @Override
    public byte[] transform(byte[] payload, StructurePlacement placement) {
        try {
            NbtCompound compound = read(payload);
            transformSpawner(compound, placement);
            transformBeehive(compound, placement);
            return AxiomBlockEntityPayloads.canonicalize(write(compound));
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid block entity NBT payload", e);
        }
    }

    private static void transformSpawner(
            NbtCompound root,
            StructurePlacement placement
    ) {
        transformSpawnerData(root, "SpawnData", placement);
        transformSpawnerData(root, "spawn_data", placement);
        transformSpawnPotentials(root, "SpawnPotentials", placement);
        transformSpawnPotentials(root, "spawn_potentials", placement);
    }

    private static void transformSpawnerData(
            NbtCompound root,
            String key,
            StructurePlacement placement
    ) {
        if (!root.contains(key, NbtElement.COMPOUND_TYPE)) return;
        NbtCompound spawnData = root.getCompound(key);
        transformNestedEntity(spawnData, "entity", placement);
        transformNestedEntity(spawnData, "Entity", placement);
    }

    private static void transformSpawnPotentials(
            NbtCompound root,
            String key,
            StructurePlacement placement
    ) {
        if (!root.contains(key, NbtElement.LIST_TYPE)) return;
        NbtList potentials = root.getList(key, NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < potentials.size(); i++) {
            NbtCompound entry = potentials.getCompound(i);
            if (entry.contains("data", NbtElement.COMPOUND_TYPE)) {
                NbtCompound data = entry.getCompound("data");
                transformNestedEntity(data, "entity", placement);
                transformNestedEntity(data, "Entity", placement);
            }
            if (entry.contains("Data", NbtElement.COMPOUND_TYPE)) {
                NbtCompound data = entry.getCompound("Data");
                transformNestedEntity(data, "entity", placement);
                transformNestedEntity(data, "Entity", placement);
            }
        }
    }

    private static void transformBeehive(
            NbtCompound root,
            StructurePlacement placement
    ) {
        transformBeeList(root, "Bees", placement);
        transformBeeList(root, "bees", placement);
    }

    private static void transformBeeList(
            NbtCompound root,
            String key,
            StructurePlacement placement
    ) {
        if (!root.contains(key, NbtElement.LIST_TYPE)) return;
        NbtList bees = root.getList(key, NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < bees.size(); i++) {
            NbtCompound bee = bees.getCompound(i);
            transformNestedEntity(bee, "EntityData", placement);
            transformNestedEntity(bee, "entity_data", placement);
        }
    }

    private static void transformNestedEntity(
            NbtCompound parent,
            String key,
            StructurePlacement placement
    ) {
        if (!parent.contains(key, NbtElement.COMPOUND_TYPE)) return;
        AxiomEntityPayloadTransform.normalizeAndTransformCompound(
                parent.getCompound(key),
                placement
        );
    }

    private static NbtCompound read(byte[] payload) throws IOException {
        try (DataInputStream input =
                     new DataInputStream(new ByteArrayInputStream(payload))) {
            return NbtIo.readCompound(input);
        }
    }

    private static byte[] write(NbtCompound compound) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            NbtIo.writeCompound(compound, output);
        }
        return bytes.toByteArray();
    }
}
