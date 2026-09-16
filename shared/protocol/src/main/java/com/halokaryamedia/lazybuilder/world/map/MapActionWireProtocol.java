package com.halokaryamedia.lazybuilder.world.map;

import com.halokaryamedia.lazybuilder.world.export.ExportSettingsWire;
import com.halokaryamedia.lazybuilder.world.registry.WorldId;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** Small versioned wire format shared by Paper and the Fabric map client. */
public final class MapActionWireProtocol {
    /** V3 adds shared export-only settings to custom-area export requests. */
    public static final int VERSION = 3;
    public static final int MAX_MESSAGE_BYTES = 4096;
    private static final int MAX_STRING_BYTES = 192;

    private static final int TELEPORT_LOCATION = 1;
    private static final int EXPORT_AREA = 2;
    private static final int CURRENT_WORLD = 3;
    private static final int TELEPORT_OK = 101;
    private static final int EXPORT_ACCEPTED = 102;
    private static final int EXPORT_COMPLETE = 103;
    private static final int CURRENT_WORLD_RESULT = 104;
    private static final int CURRENT_WORLD_CLEARED = 105;
    private static final int ERROR = 127;

    private MapActionWireProtocol() {}

    public sealed interface Request permits TeleportLocation, ExportArea, CurrentWorldRequest {}

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
            String artifactName,
            ExportSettingsWire.Settings settings
    ) implements Request {
        public ExportArea(
                WorldId worldId,
                int x1,
                int z1,
                int x2,
                int z2,
                String targetFormat,
                String artifactName
        ) {
            this(worldId, x1, z1, x2, z2, targetFormat, artifactName, ExportSettingsWire.Settings.inherit());
        }

        public ExportArea {
            Objects.requireNonNull(worldId, "worldId");
            targetFormat = requireString(targetFormat, "targetFormat");
            artifactName = requireString(artifactName, "artifactName");
            settings = Objects.requireNonNull(settings, "settings");
        }
    }

    public record CurrentWorldRequest() implements Request {}

    public sealed interface Response permits TeleportOk, ExportAccepted, ExportComplete,
            CurrentWorldResult, CurrentWorldCleared, ErrorResponse {}

    public record TeleportOk(WorldId worldId, double x, double y, double z) implements Response {
        public TeleportOk { Objects.requireNonNull(worldId, "worldId"); }
    }

    public record ExportAccepted(WorldId worldId) implements Response {
        public ExportAccepted { Objects.requireNonNull(worldId, "worldId"); }
    }

    public record ExportComplete(WorldId worldId, String fileName, String targetFormat) implements Response {
        public ExportComplete {
            Objects.requireNonNull(worldId, "worldId");
            fileName = requireString(fileName, "fileName");
            targetFormat = requireString(targetFormat, "targetFormat");
        }
    }

    public record CurrentWorldResult(WorldId worldId, String displayName, String folderName) implements Response {
        public CurrentWorldResult {
            Objects.requireNonNull(worldId, "worldId");
            displayName = requireString(displayName, "displayName");
            folderName = requireString(folderName, "folderName");
        }
    }

    /** Explicitly means the player is currently outside all managed worlds. */
    public record CurrentWorldCleared() implements Response {}

    public record ErrorResponse(String message) implements Response {
        public ErrorResponse { message = requireString(message, "message"); }
    }

    public static byte[] encodeRequest(Request request) {
        Objects.requireNonNull(request, "request");
        int opcode = switch (request) {
            case TeleportLocation ignored -> TELEPORT_LOCATION;
            case ExportArea ignored -> EXPORT_AREA;
            case CurrentWorldRequest ignored -> CURRENT_WORLD;
        };
        return encode(opcode, out -> {
            switch (request) {
                case TeleportLocation teleport -> {
                    writeWorldId(out, teleport.worldId());
                    out.writeInt(teleport.blockX());
                    out.writeInt(teleport.blockZ());
                }
                case ExportArea export -> {
                    writeWorldId(out, export.worldId());
                    out.writeInt(export.x1());
                    out.writeInt(export.z1());
                    out.writeInt(export.x2());
                    out.writeInt(export.z2());
                    writeString(out, export.targetFormat());
                    writeString(out, export.artifactName());
                    ExportSettingsWire.write(out, export.settings());
                }
                case CurrentWorldRequest ignored -> { }
            }
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
                        readWorldId(in),
                        in.readInt(), in.readInt(), in.readInt(), in.readInt(),
                        readString(in), readString(in), ExportSettingsWire.read(in));
                case CURRENT_WORLD -> new CurrentWorldRequest();
                default -> throw new IOException("Unknown map request opcode: " + opcode);
            };
            if (in.available() != 0) throw new IOException("Trailing bytes in map request");
            return request;
        }
    }

    public static byte[] encodeResponse(Response response) {
        Objects.requireNonNull(response, "response");
        int opcode = switch (response) {
            case TeleportOk ignored -> TELEPORT_OK;
            case ExportAccepted ignored -> EXPORT_ACCEPTED;
            case ExportComplete ignored -> EXPORT_COMPLETE;
            case CurrentWorldResult ignored -> CURRENT_WORLD_RESULT;
            case CurrentWorldCleared ignored -> CURRENT_WORLD_CLEARED;
            case ErrorResponse ignored -> ERROR;
        };
        return encode(opcode, out -> {
            switch (response) {
                case TeleportOk teleport -> {
                    writeWorldId(out, teleport.worldId());
                    out.writeDouble(teleport.x());
                    out.writeDouble(teleport.y());
                    out.writeDouble(teleport.z());
                }
                case ExportAccepted accepted -> writeWorldId(out, accepted.worldId());
                case ExportComplete complete -> {
                    writeWorldId(out, complete.worldId());
                    writeString(out, complete.fileName());
                    writeString(out, complete.targetFormat());
                }
                case CurrentWorldResult current -> {
                    writeWorldId(out, current.worldId());
                    writeString(out, current.displayName());
                    writeString(out, current.folderName());
                }
                case CurrentWorldCleared ignored -> { }
                case ErrorResponse error -> writeString(out, error.message());
            }
        });
    }

    public static Response decodeResponse(byte[] payload) throws IOException {
        Objects.requireNonNull(payload, "payload");
        if (payload.length < 2 || payload.length > MAX_MESSAGE_BYTES) throw new IOException("Invalid map payload size");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            int version = in.readUnsignedByte();
            if (version != VERSION) throw new IOException("Unsupported map protocol version: " + version);
            int opcode = in.readUnsignedByte();
            Response response = switch (opcode) {
                case TELEPORT_OK -> new TeleportOk(readWorldId(in), in.readDouble(), in.readDouble(), in.readDouble());
                case EXPORT_ACCEPTED -> new ExportAccepted(readWorldId(in));
                case EXPORT_COMPLETE -> new ExportComplete(readWorldId(in), readString(in), readString(in));
                case CURRENT_WORLD_RESULT -> new CurrentWorldResult(readWorldId(in), readString(in), readString(in));
                case CURRENT_WORLD_CLEARED -> new CurrentWorldCleared();
                case ERROR -> new ErrorResponse(readString(in));
                default -> throw new IOException("Unknown map response opcode: " + opcode);
            };
            if (in.available() != 0) throw new IOException("Trailing bytes in map response");
            return response;
        }
    }

    public static byte[] teleportRequest(WorldId worldId, int blockX, int blockZ) {
        return encodeRequest(new TeleportLocation(worldId, blockX, blockZ));
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
        return exportAreaRequest(worldId, x1, z1, x2, z2,
                targetFormat, artifactName, ExportSettingsWire.Settings.inherit());
    }

    public static byte[] exportAreaRequest(
            WorldId worldId,
            int x1,
            int z1,
            int x2,
            int z2,
            String targetFormat,
            String artifactName,
            ExportSettingsWire.Settings settings
    ) {
        return encodeRequest(new ExportArea(
                worldId, x1, z1, x2, z2, targetFormat, artifactName, settings));
    }

    public static byte[] currentWorldRequest() {
        return encodeRequest(new CurrentWorldRequest());
    }

    public static byte[] teleportOk(WorldId worldId, double x, double y, double z) {
        return encodeResponse(new TeleportOk(worldId, x, y, z));
    }

    public static byte[] exportAccepted(WorldId worldId) {
        return encodeResponse(new ExportAccepted(worldId));
    }

    public static byte[] exportComplete(WorldId worldId, String fileName, String targetFormat) {
        return encodeResponse(new ExportComplete(worldId, fileName, targetFormat));
    }

    public static byte[] currentWorld(WorldId worldId, String displayName, String folderName) {
        return encodeResponse(new CurrentWorldResult(worldId, displayName, folderName));
    }

    public static byte[] currentWorldCleared() {
        return encodeResponse(new CurrentWorldCleared());
    }

    public static byte[] error(String message) {
        String safe = message == null || message.isBlank() ? "Map action failed" : message.strip();
        if (safe.getBytes(StandardCharsets.UTF_8).length > MAX_STRING_BYTES) safe = "Map action failed";
        return encodeResponse(new ErrorResponse(safe));
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
