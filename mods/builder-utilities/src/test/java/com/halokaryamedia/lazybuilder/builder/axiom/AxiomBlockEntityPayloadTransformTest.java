package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.structure.StructurePlacement;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class AxiomBlockEntityPayloadTransformTest {
    @Test
    void rotatesSpawnerEmbeddedEntityAndStripsReusableIdentityFields() throws Exception {
        NbtCompound entity = new NbtCompound();
        entity.putString("id", "minecraft:zombie");
        entity.putUuid("UUID", java.util.UUID.randomUUID());
        NbtList rotation = new NbtList();
        rotation.add(NbtFloat.of(0.0f));
        rotation.add(NbtFloat.of(15.0f));
        entity.put("Rotation", rotation);

        NbtCompound spawnData = new NbtCompound();
        spawnData.put("entity", entity);

        NbtCompound root = new NbtCompound();
        root.putString("Id", "minecraft:mob_spawner");
        root.put("SpawnData", spawnData);

        byte[] transformed = new AxiomBlockEntityPayloadTransform().transform(
                write(root),
                new StructurePlacement(0, 0, 0, 1, false, false)
        );

        NbtCompound decoded = read(transformed);
        NbtCompound transformedEntity = decoded
                .getCompound("SpawnData")
                .getCompound("entity");
        assertFalse(transformedEntity.contains("UUID"));
        NbtList transformedRotation =
                transformedEntity.getList("Rotation", net.minecraft.nbt.NbtElement.FLOAT_TYPE);
        assertEquals(2, transformedRotation.size());
        assertEquals(90.0f,
                ((net.minecraft.nbt.AbstractNbtNumber) transformedRotation.get(0)).floatValue(),
                0.001f);
        assertEquals(15.0f,
                ((net.minecraft.nbt.AbstractNbtNumber) transformedRotation.get(1)).floatValue(),
                0.001f);
    }

    @Test
    void leavesUnrelatedBlockEntityPayloadFieldsIntact() throws Exception {
        NbtCompound root = new NbtCompound();
        root.putString("Id", "minecraft:chest");
        root.putString("CustomName", "{\"text\":\"A\"}");
        root.putInt("LootTableSeed", 42);

        byte[] transformed = new AxiomBlockEntityPayloadTransform().transform(
                write(root),
                new StructurePlacement(10, 64, 10, 3, true, true)
        );
        NbtCompound decoded = read(transformed);
        assertEquals("{\"text\":\"A\"}", decoded.getString("CustomName"));
        assertEquals(42, decoded.getInt("LootTableSeed"));
    }

    private static byte[] write(NbtCompound compound) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            NbtIo.writeCompound(compound, output);
        }
        return bytes.toByteArray();
    }

    private static NbtCompound read(byte[] payload) throws Exception {
        try (DataInputStream input =
                     new DataInputStream(new ByteArrayInputStream(payload))) {
            return NbtIo.readCompound(input);
        }
    }
}
