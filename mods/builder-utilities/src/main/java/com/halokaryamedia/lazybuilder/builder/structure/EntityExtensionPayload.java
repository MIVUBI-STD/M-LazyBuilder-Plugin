package com.halokaryamedia.lazybuilder.builder.structure;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/** Durable ENTITY extension state: absent/present plus exact target position and template NBT. */
public record EntityExtensionPayload(
        boolean present,
        double x,
        double y,
        double z,
        byte[] templateNbt
) {
    private static final int VERSION = 1;
    public static final int MAX_TEMPLATE_BYTES = 32 * 1024;

    public EntityExtensionPayload {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("entity payload coordinates must be finite");
        }
        Objects.requireNonNull(templateNbt, "templateNbt");
        templateNbt = templateNbt.clone();
        if (templateNbt.length > MAX_TEMPLATE_BYTES) {
            throw new IllegalArgumentException(
                    "entity template exceeds " + MAX_TEMPLATE_BYTES + " bytes");
        }
        if (present && templateNbt.length == 0) {
            throw new IllegalArgumentException("present entity payload requires template NBT");
        }
        if (!present && templateNbt.length != 0) {
            throw new IllegalArgumentException("absent entity payload cannot contain template NBT");
        }
    }

    public static EntityExtensionPayload absent(double x, double y, double z) {
        return new EntityExtensionPayload(false, x, y, z, new byte[0]);
    }

    public static EntityExtensionPayload present(
            double x, double y, double z, byte[] templateNbt
    ) {
        return new EntityExtensionPayload(true, x, y, z, templateNbt);
    }

    @Override public byte[] templateNbt() { return templateNbt.clone(); }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof EntityExtensionPayload that)) return false;
        return present == that.present
                && Double.doubleToLongBits(x) == Double.doubleToLongBits(that.x)
                && Double.doubleToLongBits(y) == Double.doubleToLongBits(that.y)
                && Double.doubleToLongBits(z) == Double.doubleToLongBits(that.z)
                && Arrays.equals(templateNbt, that.templateNbt);
    }

    @Override
    public int hashCode() {
        int result = Boolean.hashCode(present);
        result = 31 * result + Double.hashCode(x);
        result = 31 * result + Double.hashCode(y);
        result = 31 * result + Double.hashCode(z);
        result = 31 * result + Arrays.hashCode(templateNbt);
        return result;
    }

    public byte[] encode() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(VERSION);
            out.writeBoolean(present);
            out.writeDouble(x);
            out.writeDouble(y);
            out.writeDouble(z);
            out.writeInt(templateNbt.length);
            out.write(templateNbt);
        }
        return bytes.toByteArray();
    }

    public static EntityExtensionPayload decode(byte[] encoded) throws IOException {
        Objects.requireNonNull(encoded, "encoded");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(encoded))) {
            int version = in.readUnsignedByte();
            if (version != VERSION) throw new IOException("Unsupported entity extension payload version " + version);
            boolean present = in.readBoolean();
            double x = in.readDouble();
            double y = in.readDouble();
            double z = in.readDouble();
            int length = in.readInt();
            if (length < 0 || length > MAX_TEMPLATE_BYTES) {
                throw new IOException("Invalid entity template payload length " + length);
            }
            byte[] nbt = in.readNBytes(length);
            if (nbt.length != length || in.available() != 0) {
                throw new IOException("Truncated or trailing entity extension payload");
            }
            try {
                return new EntityExtensionPayload(present, x, y, z, nbt);
            } catch (IllegalArgumentException e) {
                throw new IOException("Invalid entity extension payload", e);
            }
        }
    }

    public boolean sameSlot(EntityExtensionPayload other) {
        return other != null
                && Double.doubleToLongBits(x) == Double.doubleToLongBits(other.x)
                && Double.doubleToLongBits(y) == Double.doubleToLongBits(other.y)
                && Double.doubleToLongBits(z) == Double.doubleToLongBits(other.z);
    }

    public boolean sameTemplate(EntityExtensionPayload other) {
        return sameSlot(other)
                && present == other.present
                && Arrays.equals(templateNbt, other.templateNbt);
    }
}
