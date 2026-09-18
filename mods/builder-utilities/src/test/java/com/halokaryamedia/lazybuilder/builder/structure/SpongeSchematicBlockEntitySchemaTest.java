package com.halokaryamedia.lazybuilder.builder.structure;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpongeSchematicBlockEntitySchemaTest {
    @Test
    void importerFlattensBlockEntityDataForRuntimeNbt() throws Exception {
        NbtCompound blockEntity = new NbtCompound();
        blockEntity.putString("Id", "minecraft:chest");
        blockEntity.putIntArray("Pos", new int[]{0, 0, 0});
        NbtCompound data = new NbtCompound();
        data.putString("CustomName", "{\"text\":\"Loot\"}");
        data.putLong("LootTableSeed", 42L);
        blockEntity.put("Data", data);

        NbtCompound schematic = minimalSchematic(blockEntity);
        SpongeSchematicImport imported =
                SpongeSchematicV3Importer.importCompound(schematic);

        StructureBlockEntity result = imported.snapshot().blockEntities().get(0);
        NbtCompound payload = read(result.payload());
        assertEquals("minecraft:chest", payload.getString("Id"));
        assertEquals("{\"text\":\"Loot\"}", payload.getString("CustomName"));
        assertEquals(42L, payload.getLong("LootTableSeed"));
        assertFalse(payload.contains("Data"));
        assertFalse(payload.contains("Pos"));
    }

    private static NbtCompound minimalSchematic(NbtCompound blockEntity) {
        NbtCompound schematic = new NbtCompound();
        schematic.putInt("Version", 3);
        schematic.putInt("DataVersion", 4189);
        schematic.putShort("Width", (short) 1);
        schematic.putShort("Height", (short) 1);
        schematic.putShort("Length", (short) 1);

        NbtCompound blocks = new NbtCompound();
        NbtCompound palette = new NbtCompound();
        palette.putInt("minecraft:chest[facing=north,type=single,waterlogged=false]", 0);
        blocks.put("Palette", palette);
        blocks.putByteArray("Data", new byte[]{0});
        NbtList list = new NbtList();
        list.add(blockEntity);
        blocks.put("BlockEntities", list);
        schematic.put("Blocks", blocks);
        return schematic;
    }

    private static NbtCompound read(byte[] payload) throws Exception {
        try (var input = new java.io.DataInputStream(
                new java.io.ByteArrayInputStream(payload))) {
            return net.minecraft.nbt.NbtIo.readCompound(input);
        }
    }
}
