package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.structure.StructurePlacement;
import net.minecraft.nbt.AbstractNbtNumber;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AxiomEntityPayloadTransformMotionTest {
    @Test
    void rotatesAndMirrorsMotionWithPlacementTransform() throws Exception {
        NbtCompound entity = new NbtCompound();
        entity.putString("Id", "minecraft:arrow");
        NbtList motion = new NbtList();
        motion.add(NbtDouble.of(1.0));
        motion.add(NbtDouble.of(0.25));
        motion.add(NbtDouble.of(2.0));
        entity.put("Motion", motion);

        byte[] encoded = write(entity);
        byte[] transformed = new AxiomEntityPayloadTransform().transform(
                encoded,
                new StructurePlacement(0, 0, 0, 1, true, false)
        );
        NbtList result = read(transformed).getList("Motion", NbtElement.DOUBLE_TYPE);

        // mirror X: (1,2)->(-1,2), then CW90: (-z,x)->(-2,-1)
        assertEquals(-2.0, ((AbstractNbtNumber) result.get(0)).doubleValue(), 1e-9);
        assertEquals(0.25, ((AbstractNbtNumber) result.get(1)).doubleValue(), 1e-9);
        assertEquals(-1.0, ((AbstractNbtNumber) result.get(2)).doubleValue(), 1e-9);
    }

    private static byte[] write(NbtCompound compound) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            NbtIo.writeCompound(compound, out);
        }
        return bytes.toByteArray();
    }

    private static NbtCompound read(byte[] payload) throws Exception {
        try (DataInputStream in =
                     new DataInputStream(new ByteArrayInputStream(payload))) {
            return NbtIo.readCompound(in);
        }
    }
}
