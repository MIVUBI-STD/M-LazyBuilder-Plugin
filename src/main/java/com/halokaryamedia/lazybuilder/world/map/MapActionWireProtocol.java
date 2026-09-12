package com.halokaryamedia.lazybuilder.world.map;

import com.halokaryamedia.lazybuilder.world.registry.WorldId;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** Small versioned wire format for Xaero/map action intents. */
public final class MapActionWireProtocol {
    public static final int VERSION = 1;
    public static final int MAX_MESSAGE_BYTES = 4096;
    private static final int MAX_STRING_BYTES = 192;

    private static final byte TELEPORT_LOCATION = 1;
    private static final byte EXPORT_AREA = 2;
    private static final byte TELEPORT_OK = 101;
    private static final byte EXPORT_ACCEPTED = 102;
    private static final byte EXPORT_COMPLETE = 103;
    private static final byte ERROR = 127;

    private MapActionWireProtocol() {}

    public sealed interface Request permits TeleportLocation, ExportArea {}

    public record TeleportLocation(WorldId worldId, int blockX, int blockZ) implements Request {
        public TeleportLocation { Objects.requireNonNull(worldId, "worldId"); }
    }

    public record ExportArea(
            WorldId worldId,
            int x1,
            int z1,
            int x2,
            int z2,
            String targetFormat,
            String artifactName
    ) implements Request {
        public ExportArea {
            Objects.requireNonNull(worldId, "worldId");
            targetFormat = requireString(targetFormat, "targetFormat");
            artifactName = requireString(artifactName, "artifactName");
        }
    }

    public static byte[] teleportRequest(WorldId worldId, int blockX, int blockZ) {
        return encode(TELEPORT_LOCATION, out -> {
            writeWorldId(out, worldId);
            out.writeInt(blockX);
            out.writeInt(blockZ);
        });
    }

    public static byte[] exportAreaRequest(
            WorldId worldId,
            int x1,
            int z1,
            int x2,
            int z2,
            String targetFormat,
            String artifactName
    ) {
        return encode(EXPORT_AREA, out -> {
            writeWorldId(out, worldId);
            out.writeInt(x1);
            out.writeInt(z1);
            out.writeInt(x2);
            out.writeInt(z2);
            writeString(out, targetFormat);
            writeString(out, artifactName);
        });
    }

    public static Request decodeRequest(byte[] payload) throws IOException {
        Objects.requireNonNull(payload, "payload");
        if (payload.length < 2 || payload.length > MAX_MESSAGE_BYTES) throw new IOException("Invalid map payload size");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            int version = in.readUnsignedByte();
            if (version != VERSION) throw new IOException("Unsupported map protocol version: " + version);
            int opcode = in.readUnsignedByte();
            Request request = switch (opcode) {
                case TELEPORT_LOCATION -> new TeleportLocation(readWorldId(in), in.readInt(), in.readInt());
                case EXPORT_AREA -> new ExportArea(
                        readWorldId(in), in.readInt(), in.readInt(), in.readInt(), in.readInt(), readString(in), readString(in));
                default -> throw new IOException("Unknown map request opcode: " + opcode);
            };
            if (in.available() != 0) throw new IOException("Trailing bytes in map request");
            return request;
        }
    }

    public static byte[] teleportOk(WorldId worldId, double x, double y, double z) {
        return encode(TELEPORT_OK, out -> {
            writeWorldId(out, worldId);
            out.writeDouble(x);
            out.writeDouble(y);
            out.writeDouble(z);
        });
    }

    public static byte[] exportAccepted(WorldId worldId) {
        return encode(EXPORT_ACCEPTED, out -> writeWorldId(out, worldId));
    }

    public static byte[] exportComplete(WorldId worldId, String fileName, String targetFormat) {
        return encode(EXPORT_COMPLETE, out -> {
            writeWorldId(out, worldId);
            writeString(out, fileName);
            writeString(out, targetFormat);
        });
    }

    public static byte[] error(String message) {
        String safe = message == null || message.isBlank() ? "Map action failed" : message.strip();
        if (safe.getBytes(StandardCharsets.UTF_8).length > MAX_STRING_BYTES) safe = "Map action failed";
        String finalSafe = safe;
        return encode(ERROR, out -> writeString(out, finalSafe));
    }

    private static byte[] encode(int opcode, Writer writer) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(buffer)) {
                out.writeByte(VERSION);
                out.writeByte(opcode);
                writer.write(out);
            }
            byte[] bytes = buffer.toByteArray();
            if (bytes.length > MAX_MESSAGE_BYTES) throw new IllegalArgumentException("Map message exceeds protocol limit");
            return bytes;
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static WorldId readWorldId(DataInputStream in) throws IOException {
        return new WorldId(new UUID(in.readLong(), in.readLong()));
    }

    private static void writeWorldId(DataOutputStream out, WorldId id) throws IOException {
        UUID value = Objects.requireNonNull(id, "worldId").value();
        out.writeLong(value.getMostSignificantBits());
        out.writeLong(value.getLeastSignificantBits());
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readUnsignedShort();
        if (length < 1 || length > MAX_STRING_BYTES || length > in.available()) throw new IOException("Invalid map string length");
        byte[] data = in.readNBytes(length);
        if (data.length != length) throw new IOException("Unexpected end of map payload");
        return new String(data, StandardCharsets.UTF_8);
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] data = requireString(value, "value").getBytes(StandardCharsets.UTF_8);
        if (data.length > MAX_STRING_BYTES) throw new IllegalArgumentException("Map string exceeds protocol limit");
        out.writeShort(data.length);
        out.write(data);
    }

    private static String requireString(String value, String label) {
        Objects.requireNonNull(value, label);
        String stripped = value.strip();
        if (stripped.isEmpty()) throw new IllegalArgumentException(label + " must not be blank");
        return stripped;
    }

    @FunctionalInterface
    private interface Writer { void write(DataOutputStream out) throws IOException; }
}
