package com.halokaryamedia.lazybuilder.builder.axiom;

import net.minecraft.nbt.AbstractNbtNumber;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import com.halokaryamedia.lazybuilder.builder.wire.BuilderExtensionWireProtocol;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/** Converts canonical captured entity NBT into Paper EntitySnapshot SNBT + rotation. */
public record AxiomEntityTemplate(
        String snbt,
        float yaw,
        float pitch
) {
    public AxiomEntityTemplate {
        if (snbt == null || snbt.isBlank()) {
            throw new IllegalArgumentException("entity snapshot SNBT must be non-blank");
        }
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("entity rotation must be finite");
        }
    }

    public static AxiomEntityTemplate decode(byte[] payload) throws IOException {
        NbtCompound compound;
        try (DataInputStream input =
                     new DataInputStream(new ByteArrayInputStream(payload))) {
            compound = NbtIo.readCompound(input);
        }

        String type = compound.getString("Id");
        if (type.isBlank()) {
            type = compound.getString("id");
        }
        if (type.isBlank()) {
            throw new IOException("Entity template has no Id");
        }

        compound.remove("Id");
        compound.putString("id", type);
        compound.remove("Pos");
        compound.remove("UUID");
        compound.remove("UUIDMost");
        compound.remove("UUIDLeast");

        float yaw = 0.0f;
        float pitch = 0.0f;
        NbtList rotation = compound.getList("Rotation", NbtElement.FLOAT_TYPE);
        if (rotation.size() == 2
                && rotation.get(0) instanceof AbstractNbtNumber yawNumber
                && rotation.get(1) instanceof AbstractNbtNumber pitchNumber) {
            yaw = yawNumber.floatValue();
            pitch = pitchNumber.floatValue();
        }
        // Location is supplied by the extension envelope, not embedded in the snapshot.
        compound.remove("Rotation");
        String snbt = compound.toString();
        int snbtBytes = snbt.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (snbtBytes > BuilderExtensionWireProtocol.MAX_ENTITY_TEMPLATE_BYTES) {
            throw new IOException(
                    "Entity snapshot SNBT exceeds protocol limit "
                            + BuilderExtensionWireProtocol.MAX_ENTITY_TEMPLATE_BYTES
                            + " UTF-8 bytes");
        }
        return new AxiomEntityTemplate(snbt, yaw, pitch);
    }
}
