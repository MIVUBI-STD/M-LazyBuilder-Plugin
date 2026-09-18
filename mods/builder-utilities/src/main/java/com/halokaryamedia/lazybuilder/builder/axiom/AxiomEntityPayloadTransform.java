package com.halokaryamedia.lazybuilder.builder.axiom;

import com.halokaryamedia.lazybuilder.builder.structure.EntityPayloadTransform;
import com.halokaryamedia.lazybuilder.builder.structure.StructurePlacement;
import net.minecraft.nbt.AbstractNbtNumber;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Normalizes reusable entity NBT and applies structure yaw mirror/rotation. */
public final class AxiomEntityPayloadTransform implements EntityPayloadTransform {
    @Override
    public byte[] transform(byte[] payload, StructurePlacement placement) {
        try {
            NbtCompound compound = read(payload);
            normalizeAndTransformCompound(compound, placement);
            return write(compound);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid entity template NBT", e);
        }
    }

    static void normalizeAndTransformCompound(
            NbtCompound compound,
            StructurePlacement placement
    ) {
        compound.remove("UUID");
        compound.remove("UUIDMost");
        compound.remove("UUIDLeast");
        compound.remove("Pos");
        compound.remove("pos");

        transformRotationList(compound, "Rotation", placement);
        transformRotationList(compound, "rotation", placement);
    }

    private static void transformRotationList(
            NbtCompound compound,
            String key,
            StructurePlacement placement
    ) {
        NbtList rotation = compound.getList(key, NbtElement.FLOAT_TYPE);
        if (rotation.size() != 2
                || !(rotation.get(0) instanceof AbstractNbtNumber yawNumber)
                || !(rotation.get(1) instanceof AbstractNbtNumber pitchNumber)) {
            return;
        }
        NbtList transformed = new NbtList();
        transformed.add(NbtFloat.of(transformYaw(yawNumber.floatValue(), placement)));
        transformed.add(NbtFloat.of(pitchNumber.floatValue()));
        compound.put(key, transformed);
    }

    static float transformYaw(float yawDegrees, StructurePlacement placement) {
        double radians = Math.toRadians(yawDegrees);
        double x = -Math.sin(radians);
        double z = Math.cos(radians);

        if (placement.mirrorX()) x = -x;
        if (placement.mirrorZ()) z = -z;

        double rx;
        double rz;
        switch (Math.floorMod(placement.quarterTurnsY(), 4)) {
            case 0 -> { rx = x; rz = z; }
            case 1 -> { rx = -z; rz = x; }
            case 2 -> { rx = -x; rz = -z; }
            case 3 -> { rx = z; rz = -x; }
            default -> throw new AssertionError();
        }
        return (float) Math.toDegrees(Math.atan2(-rx, rz));
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
