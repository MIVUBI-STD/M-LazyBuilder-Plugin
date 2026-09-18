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
    /** V5 correlates request-bound responses so stale/errors cannot resolve another map operation. */
    public static final int VERSION = 5;
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

    public sealed interface Request permits TeleportLocation, ExportArea, CurrentWorldRequest {
        long requestId();
    }

    public record TeleportLocation(long requestId, WorldId worldId, int blockX, int blockZ) implements Request {
        public TeleportLocation {
            requireRequestId(requestId);
            Objects.requireNonNull(worldId, "worldId");
        }
    }

    public record ExportArea(
            long requestId,
            WorldId worldId,
            String dimensionId,
            int x1,
            int z1,
            int x2,
            int z2,
            String targetFormat,
            String artifactName,
            ExportSettingsWire.Settings settings
    ) implements Request {
        public ExportArea {
            requireRequestId(requestId);
            Objects.requireNonNull(worldId, "worldId");
            dimensionId = requireString(dimensionId, "dimensionId");
            targetFormat = requireString(targetFormat, "targetFormat");
            artifactName = requireString(artifactName, "artifactName");
            settings = Objects.requireNonNull(settings, "settings");
        }
    }

    public record CurrentWorldRequest(long requestId) implements Request {
        public CurrentWorldRequest {
            requireRequestId(requestId);
        }
    }

    public sealed interface Response permits TeleportOk, ExportAccepted, ExportComplete,
            CurrentWorldResult, CurrentWorldCleared, ErrorResponse {
        long requestId();
    }

    public record TeleportOk(long requestId, WorldId worldId, double x, double y, double z) implements Response {
        public TeleportOk { Objects.requireNonNull(worldId, "worldId"); }
    }

    public record ExportAccepted(long requestId, WorldId worldId) implements Response {
        public ExportAccepted { Objects.requireNonNull(worldId, "worldId"); }
    }

    public record ExportComplete(long requestId, WorldId worldId, String fileName, String targetFormat) implements Response {
        public ExportComplete {
            Objects.requireNonNull(worldId, "worldId");
            fileName = requireString(fileName, "fileName");
            targetFormat = requireString(targetFormat, "targetFormat");
        }
    }

    public record CurrentWorldResult(long requestId, WorldId worldId, String displayName, String folderName) implements Response {
        public CurrentWorldResult {
            Objects.requireNonNull(worldId, "worldId");
            displayName = requireString(displayName, "displayName");
            folderName = requireString(folderName, "folderName");
        }
        public CurrentWorldResult(WorldId worldId, String displayName, String folderName) {
            this(0L, worldId, displayName, folderName);
        }
    }

    /** Explicitly means the player is currently outside all managed worlds. */
    public record CurrentWorldCleared(long requestId) implements Response {
        public CurrentWorldCleared() { this(0L); }
    }

    public record ErrorResponse(long requestId, String message) implements Response {
        public ErrorResponse { message = requireString(message, "message"); }
        public ErrorResponse(String message) { this(0L, message); }
    }

    public static byte[] encodeRequest(Request request) {
        Objects.requireNonNull(request, "request");
        requireRequestId(request.requestId());
        int opcode = switch (request) {
            case TeleportLocation ignored -> TELEPORT_LOCATION;
            case ExportArea ignored -> EXPORT_AREA;
            case CurrentWorldRequest ignored -> CURRENT_WORLD;
        };
        return encode(opcode, out -> {
            out.writeLong(request.requestId());
            switch (request) {
                case TeleportLocation teleport -> {
                    writeWorldId(out, teleport.worldId());
                    out.writeInt(teleport.blockX());
                    out.writeInt(teleport.blockZ());
                }
                case ExportArea export -> {
                    writeWorldId(out, export.worldId());
                    writeString(out, export.dimensionId());
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
        if (payload.length < 10 || payload.length > MAX_MESSAGE_BYTES) throw new IOException("Invalid map payload size");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            int version = in.readUnsignedByte();
            if (version != VERSION) throw new IOException("Unsupported map protocol version: " + version);
            int opcode = in.readUnsignedByte();
            long requestId = in.readLong();
            if (requestId <= 0L) throw new IOException("Invalid map request id");
            Request request = switch (opcode) {
                case TELEPORT_LOCATION -> new TeleportLocation(requestId, readWorldId(in), in.readInt(), in.readInt());
                case EXPORT_AREA -> new ExportArea(
                        requestId, readWorldId(in), readString(in),
                        in.readInt(), in.readInt(), in.readInt(), in.readInt(),
                        readString(in), readString(in), ExportSettingsWire.read(in));
                case CURRENT_WORLD -> new CurrentWorldRequest(requestId);
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
            out.writeLong(response.requestId());
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
        if (payload.length < 10 || payload.length > MAX_MESSAGE_BYTES) throw new IOException("Invalid map payload size");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            int version = in.readUnsignedByte();
            if (version != VERSION) throw new IOException("Unsupported map protocol version: " + version);
            int opcode = in.readUnsignedByte();
            long requestId = in.readLong();
            if (requestId < 0L) throw new IOException("Invalid map response request id");
            Response response = switch (opcode) {
                case TELEPORT_OK -> new TeleportOk(
                        requireBoundResponseId(requestId), readWorldId(in), in.readDouble(), in.readDouble(), in.readDouble());
                case EXPORT_ACCEPTED -> new ExportAccepted(requireBoundResponseId(requestId), readWorldId(in));
                case EXPORT_COMPLETE -> new ExportComplete(
                        requireBoundResponseId(requestId), readWorldId(in), readString(in), readString(in));
                case CURRENT_WORLD_RESULT -> new CurrentWorldResult(requestId, readWorldId(in), readString(in), readString(in));
                case CURRENT_WORLD_CLEARED -> new CurrentWorldCleared(requestId);
                case ERROR -> new ErrorResponse(requestId, readString(in));
                default -> throw new IOException("Unknown map response opcode: " + opcode);
            };
            if (in.available() != 0) throw new IOException("Trailing bytes in map response");
            return response;
        }
    }

    public static byte[] teleportRequest(long requestId, WorldId worldId, int blockX, int blockZ) {
        return encodeRequest(new TeleportLocation(requestId, worldId, blockX, blockZ));
    }

    public static byte[] exportAreaRequest(
            long requestId, WorldId worldId, String dimensionId, int x1, int z1, int x2, int z2,
            String targetFormat, String artifactName, ExportSettingsWire.Settings settings
    ) {
        return encodeRequest(new ExportArea(
                requestId, worldId, dimensionId, x1, z1, x2, z2, targetFormat, artifactName, settings));
    }

    public static byte[] currentWorldRequest(long requestId) {
        return encodeRequest(new CurrentWorldRequest(requestId));
    }

    public static byte[] teleportOk(long requestId, WorldId worldId, double x, double y, double z) {
        return encodeResponse(new TeleportOk(requestId, worldId, x, y, z));
    }
    public static byte[] exportAccepted(long requestId, WorldId worldId) {
        return encodeResponse(new ExportAccepted(requestId, worldId));
    }
    public static byte[] exportComplete(long requestId, WorldId worldId, String fileName, String targetFormat) {
        return encodeResponse(new ExportComplete(requestId, worldId, fileName, targetFormat));
    }
    public static byte[] currentWorld(long requestId, WorldId worldId, String displayName, String folderName) {
        return encodeResponse(new CurrentWorldResult(requestId, worldId, displayName, folderName));
    }
    public static byte[] currentWorld(WorldId worldId, String displayName, String folderName) {
        return currentWorld(0L, worldId, displayName, folderName);
    }

    public static byte[] currentWorldCleared(long requestId) {
        return encodeResponse(new CurrentWorldCleared(requestId));
    }
    public static byte[] currentWorldCleared() { return currentWorldCleared(0L); }

    public static byte[] error(long requestId, String message) {
        String safe = message == null || message.isBlank() ? "Map action failed" : message.strip();
        if (safe.getBytes(StandardCharsets.UTF_8).length > MAX_STRING_BYTES) safe = "Map action failed";
        return encodeResponse(new ErrorResponse(requestId, safe));
    }
    public static byte[] error(String message) { return error(0L, message); }

    private static long requireRequestId(long requestId) {
        if (requestId <= 0L) throw new IllegalArgumentException("requestId must be positive");
        return requestId;
    }

    private static long requireBoundResponseId(long requestId) throws IOException {
        if (requestId <= 0L) throw new IOException("Request-bound map response requires a positive request id");
        return requestId;
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
