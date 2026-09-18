package com.halokaryamedia.lazybuilder.builder.axiom;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Canonical deterministic block-entity NBT used by History and the Paper bridge. */
public final class AxiomBlockEntityPayloads {
    private AxiomBlockEntityPayloads() {}

    public static byte[] capture(ClientWorld world, int x, int y, int z) throws IOException {
        Objects.requireNonNull(world, "world");
        BlockEntity blockEntity = world.getBlockEntity(new BlockPos(x, y, z));
        if (blockEntity == null) return new byte[0];

        NbtCompound payload = blockEntity.createNbt(world.getRegistryManager());
        var id = Registries.BLOCK_ENTITY_TYPE.getId(blockEntity.getType());
        if (id == null) {
            throw new IOException("Unregistered block entity at " + x + "," + y + "," + z);
        }
        payload.putString("Id", id.toString());
        return encodeCanonical(payload);
    }

    public static byte[] canonicalize(byte[] payload) throws IOException {
        Objects.requireNonNull(payload, "payload");
        if (payload.length == 0) return new byte[0];
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            return encodeCanonical(NbtIo.readCompound(input));
        }
    }

    private static byte[] encodeCanonical(NbtCompound source) throws IOException {
        NbtCompound normalized = canonicalRootCompound(source);
        String id = normalized.getString("Id");
        if (id.isBlank()) {
            throw new IOException("Block entity payload is missing Id");
        }

        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream output = new DataOutputStream(bytes)) {
            NbtIo.writeCompound(normalized, output);
            return bytes.toByteArray();
        }
    }

    private static NbtCompound canonicalRootCompound(NbtCompound source) {
        NbtCompound result = new NbtCompound();
        String id = source.getString("Id");
        if (id.isBlank()) id = source.getString("id");

        List<String> keys = new ArrayList<>(source.getKeys());
        Collections.sort(keys);
        for (String key : keys) {
            if (key.equals("Id") || key.equals("id")
                    || key.equals("x") || key.equals("y") || key.equals("z")
                    || key.equals("Pos") || key.equals("pos")) {
                continue;
            }
            NbtElement value = source.get(key);
            if (value != null) result.put(key, canonicalNestedElement(value));
        }
        if (!id.isBlank()) result.putString("Id", id);
        return result;
    }

    /**
     * Nested NBT is canonicalized only by deterministic key order. Nested key names
     * and position-like fields are semantic payload and must not be rewritten.
     */
    private static NbtCompound canonicalNestedCompound(NbtCompound source) {
        NbtCompound result = new NbtCompound();
        List<String> keys = new ArrayList<>(source.getKeys());
        Collections.sort(keys);
        for (String key : keys) {
            NbtElement value = source.get(key);
            if (value != null) result.put(key, canonicalNestedElement(value));
        }
        return result;
    }

    private static NbtElement canonicalNestedElement(NbtElement element) {
        if (element instanceof NbtCompound compound) {
            return canonicalNestedCompound(compound);
        }
        if (element instanceof NbtList list) {
            NbtList result = new NbtList();
            for (NbtElement value : list) {
                result.add(canonicalNestedElement(value));
            }
            return result;
        }
        return element.copy();
    }
}
