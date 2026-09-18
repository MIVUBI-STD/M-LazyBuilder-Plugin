package com.halokaryamedia.lazybuilder.builder.structure;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpongeSchematicEntitySchemaTest {
    @Test
    void importerFlattensSpecDataIntoInternalVanillaPayload() throws Exception {
        NbtCompound entity = new NbtCompound();
        entity.putString("Id", "minecraft:creeper");
        NbtList pos = new NbtList();
        pos.add(NbtDouble.of(1.5));
        pos.add(NbtDouble.of(2.0));
        pos.add(NbtDouble.of(3.5));
        entity.put("Pos", pos);

        NbtCompound data = new NbtCompound();
        NbtList rotation = new NbtList();
        rotation.add(NbtFloat.of(90.0f));
        rotation.add(NbtFloat.of(10.0f));
        data.put("Rotation", rotation);
        data.putInt("Fuse", 30);
        entity.put("Data", data);

        NbtCompound schematic = minimalSchematic(entity);
        SpongeSchematicImport imported =
                SpongeSchematicV3Importer.importCompound(schematic);

        StructureEntity result = imported.snapshot().entities().get(0);
        NbtCompound payload = TestNbt.read(result.payload());
        assertEquals("minecraft:creeper", payload.getString("Id"));
        assertEquals(30, payload.getInt("Fuse"));
        assertEquals(2, payload.getList("Rotation", NbtElement.FLOAT_TYPE).size());
        assertFalse(payload.contains("Data"));
        assertFalse(payload.contains("Pos"));
    }

    private static NbtCompound minimalSchematic(NbtCompound entity) {
        NbtCompound schematic = new NbtCompound();
        schematic.putInt("Version", 3);
        schematic.putInt("DataVersion", 4189);
        schematic.putShort("Width", (short) 1);
        schematic.putShort("Height", (short) 1);
        schematic.putShort("Length", (short) 1);

        NbtCompound blocks = new NbtCompound();
        NbtCompound palette = new NbtCompound();
        palette.putInt("minecraft:air", 0);
        blocks.put("Palette", palette);
        blocks.putByteArray("Data", new byte[]{0});
        schematic.put("Blocks", blocks);

        NbtList entities = new NbtList();
        entities.add(entity);
        schematic.put("Entities", entities);
        return schematic;
    }

    private static final class TestNbt {
        static NbtCompound read(byte[] payload) throws Exception {
            try (var input = new java.io.DataInputStream(
                    new java.io.ByteArrayInputStream(payload))) {
                return net.minecraft.nbt.NbtIo.readCompound(input);
            }
        }
    }
}
