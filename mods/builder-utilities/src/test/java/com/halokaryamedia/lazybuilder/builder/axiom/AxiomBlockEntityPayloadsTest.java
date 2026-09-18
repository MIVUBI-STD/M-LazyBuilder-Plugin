package com.halokaryamedia.lazybuilder.builder.axiom;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class AxiomBlockEntityPayloadsTest {
    @Test
    void canonicalizationNormalizesOnlyRootIdentityAndPosition() throws Exception {
        NbtCompound nested = new NbtCompound();
        nested.putString("id", "minecraft:zombie");
        nested.putInt("x", 7);
        nested.putInt("y", 8);
        nested.putInt("z", 9);
        nested.putIntArray("Pos", new int[]{1, 2, 3});

        NbtCompound root = new NbtCompound();
        root.putString("id", "minecraft:mob_spawner");
        root.putInt("x", 100);
        root.putInt("y", 64);
        root.putInt("z", -20);
        root.put("Nested", nested);

        NbtCompound decoded = read(AxiomBlockEntityPayloads.canonicalize(write(root)));

        assertEquals("minecraft:mob_spawner", decoded.getString("Id"));
        assertFalse(decoded.contains("id"));
        assertFalse(decoded.contains("x"));
        assertFalse(decoded.contains("y"));
        assertFalse(decoded.contains("z"));

        NbtCompound nestedDecoded = decoded.getCompound("Nested");
        assertEquals("minecraft:zombie", nestedDecoded.getString("id"));
        assertEquals(7, nestedDecoded.getInt("x"));
        assertEquals(8, nestedDecoded.getInt("y"));
        assertEquals(9, nestedDecoded.getInt("z"));
        assertArrayEquals(new int[]{1, 2, 3}, nestedDecoded.getIntArray("Pos"));
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
