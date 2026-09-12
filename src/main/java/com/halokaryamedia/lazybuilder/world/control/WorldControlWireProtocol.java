package com.halokaryamedia.lazybuilder.world.control;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Shared bounded protocol for the general World Manager client surface. */
public final class WorldControlWireProtocol {
    public static final int VERSION = 1;
    public static final int MAX_MESSAGE_BYTES = 64 * 1024;
    private static final int MAX_STRING_BYTES = 1024;
    private static final int MAX_WORLDS = 4096;

    private static final int LIST = 1;
    private static final int CREATE = 2;
    private static final int LOAD = 3;
    private static final int UNLOAD = 4;
    private static final int TELEPORT = 5;
    private static final int ARCHIVE = 6;
    private static final int RESTORE = 7;

    private static final int WORLDS = 101;
    private static final int WORLD_CHANGED = 102;
    private static final int TELEPORT_OK = 103;
    private static final int ERROR = 127;

    private WorldControlWireProtocol() {}

    public sealed interface Request permits ListWorlds, CreateWorld, LoadWorld, UnloadWorld,
            TeleportWorld, ArchiveWorld, RestoreWorld {}

    public record ListWorlds() implements Request {}

    public record CreateWorld(String folderName, String displayName, String kind) implements Request {
        public CreateWorld {
            folderName = requireString(folderName, "folderName");
            displayName = requireString(displayName, "displayName");
            kind = requireString(kind, "kind");
        }
    }

    public record LoadWorld(UUID worldId) implements Request { public LoadWorld { Objects.requireNonNull(worldId); } }
    public record UnloadWorld(UUID worldId) implements Request { public UnloadWorld { Objects.requireNonNull(worldId); } }
    public record TeleportWorld(UUID worldId) implements Request { public TeleportWorld { Objects.requireNonNull(worldId); } }
    public record ArchiveWorld(UUID worldId) implements Request { public ArchiveWorld { Objects.requireNonNull(worldId); } }
    public record RestoreWorld(UUID worldId) implements Request { public RestoreWorld { Objects.requireNonNull(worldId); } }

    public sealed interface Response permits WorldList, WorldChanged, TeleportOk, ErrorResponse {}

    public record WorldSummary(
            UUID worldId,
            String folderName,
            String displayName,
            String kind,
            String lifecycle,
            String runtimeState,
            boolean autoLoad,
            String defaultGameMode
    ) {
        public WorldSummary {
            Objects.requireNonNull(worldId, "worldId");
            folderName = requireString(folderName, "folderName");
            displayName = requireString(displayName, "displayName");
            kind = requireString(kind, "kind");
            lifecycle = requireString(lifecycle, "lifecycle");
            runtimeState = requireString(runtimeState, "runtimeState");
            defaultGameMode = requireString(defaultGameMode, "defaultGameMode");
        }
    }

    public record WorldList(List<WorldSummary> worlds) implements Response {
        public WorldList {
            worlds = List.copyOf(Objects.requireNonNull(worlds, "worlds"));
            if (worlds.size() > MAX_WORLDS) throw new IllegalArgumentException("Too many worlds");
        }
    }

    public record WorldChanged(String action, WorldSummary world) implements Response {
        public WorldChanged {
            action = requireString(action, "action");
            Objects.requireNonNull(world, "world");
        }
    }

    public record TeleportOk(WorldSummary world) implements Response {
        public TeleportOk { Objects.requireNonNull(world, "world"); }
    }

    public record ErrorResponse(String message) implements Response {
        public ErrorResponse { message = requireString(message, "message"); }
    }

    public static byte[] encodeRequest(Request request) throws IOException {
        Objects.requireNonNull(request, "request");
        return write(requestOpcode(request), out -> {
            switch (request) {
                case ListWorlds ignored -> { }
                case CreateWorld create -> {
                    writeString(out, create.folderName());
                    writeString(out, create.displayName());
                    writeString(out, create.kind());
                }
                case LoadWorld load -> writeUuid(out, load.worldId());
                case UnloadWorld unload -> writeUuid(out, unload.worldId());
                case TeleportWorld teleport -> writeUuid(out, teleport.worldId());
                case ArchiveWorld archive -> writeUuid(out, archive.worldId());
                case RestoreWorld restore -> writeUuid(out, restore.worldId());
            }
        });
    }

    public static Request decodeRequest(byte[] payload) throws IOException {
        try (DataInputStream in = input(payload)) {
            int opcode = readHeader(in);
            Request request = switch (opcode) {
                case LIST -> new ListWorlds();
                case CREATE -> new CreateWorld(readString(in), readString(in), readString(in));
                case LOAD -> new LoadWorld(readUuid(in));
                case UNLOAD -> new UnloadWorld(readUuid(in));
                case TELEPORT -> new TeleportWorld(readUuid(in));
                case ARCHIVE -> new ArchiveWorld(readUuid(in));
                case RESTORE -> new RestoreWorld(readUuid(in));
                default -> throw new IOException("Unknown world-control request opcode: " + opcode);
            };
            requireExhausted(in, "request");
            return request;
        }
    }

    public static byte[] encodeResponse(Response response) throws IOException {
        Objects.requireNonNull(response, "response");
        int opcode = switch (response) {
            case WorldList ignored -> WORLDS;
            case WorldChanged ignored -> WORLD_CHANGED;
            case TeleportOk ignored -> TELEPORT_OK;
            case ErrorResponse ignored -> ERROR;
        };
        return write(opcode, out -> {
            switch (response) {
                case WorldList list -> {
                    out.writeInt(list.worlds().size());
                    for (WorldSummary world : list.worlds()) writeWorld(out, world);
                }
                case WorldChanged changed -> {
                    writeString(out, changed.action());
                    writeWorld(out, changed.world());
                }
                case TeleportOk ok -> writeWorld(out, ok.world());
                case ErrorResponse error -> writeString(out, error.message());
            }
        });
    }

    public static Response decodeResponse(byte[] payload) throws IOException {
        try (DataInputStream in = input(payload)) {
            int opcode = readHeader(in);
            Response response = switch (opcode) {
                case WORLDS -> {
                    int count = in.readInt();
                    if (count < 0 || count > MAX_WORLDS) throw new IOException("World count is invalid");
                    List<WorldSummary> worlds = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) worlds.add(readWorld(in));
                    yield new WorldList(worlds);
                }
                case WORLD_CHANGED -> new WorldChanged(readString(in), readWorld(in));
                case TELEPORT_OK -> new TeleportOk(readWorld(in));
                case ERROR -> new ErrorResponse(readString(in));
                default -> throw new IOException("Unknown world-control response opcode: " + opcode);
            };
            requireExhausted(in, "response");
            return response;
        }
    }

    public static byte[] error(String message) {
        String safe = Objects.requireNonNullElse(message, "World request failed").strip();
        if (safe.isEmpty()) safe = "World request failed";
        if (safe.length() > 512) safe = safe.substring(0, 512);
        try { return encodeResponse(new ErrorResponse(safe)); }
        catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }

    private static int requestOpcode(Request request) {
        return switch (request) {
            case ListWorlds ignored -> LIST;
            case CreateWorld ignored -> CREATE;
            case LoadWorld ignored -> LOAD;
            case UnloadWorld ignored -> UNLOAD;
            case TeleportWorld ignored -> TELEPORT;
            case ArchiveWorld ignored -> ARCHIVE;
            case RestoreWorld ignored -> RESTORE;
        };
    }

    private static void writeWorld(DataOutputStream out, WorldSummary world) throws IOException {
        writeUuid(out, world.worldId());
        writeString(out, world.folderName());
        writeString(out, world.displayName());
        writeString(out, world.kind());
        writeString(out, world.lifecycle());
        writeString(out, world.runtimeState());
        out.writeBoolean(world.autoLoad());
        writeString(out, world.defaultGameMode());
    }

    private static WorldSummary readWorld(DataInputStream in) throws IOException {
        return new WorldSummary(
                readUuid(in), readString(in), readString(in), readString(in),
                readString(in), readString(in), in.readBoolean(), readString(in));
    }

    private static DataInputStream input(byte[] payload) throws IOException {
        Objects.requireNonNull(payload, "payload");
        if (payload.length < 2 || payload.length > MAX_MESSAGE_BYTES) {
            throw new IOException("World-control payload size is invalid");
        }
        return new DataInputStream(new ByteArrayInputStream(payload));
    }

    private static int readHeader(DataInputStream in) throws IOException {
        int version = in.readUnsignedByte();
        if (version != VERSION) throw new IOException("Unsupported world-control protocol version: " + version);
        return in.readUnsignedByte();
    }

    private static byte[] write(int opcode, Writer writer) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeByte(VERSION);
            out.writeByte(opcode);
            writer.write(out);
        }
        byte[] payload = buffer.toByteArray();
        if (payload.length > MAX_MESSAGE_BYTES) throw new IOException("World-control message exceeds wire limit");
        return payload;
    }

    private static void requireExhausted(DataInputStream in, String label) throws IOException {
        if (in.available() != 0) throw new IOException("World-control " + label + " contains trailing bytes");
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = requireString(value, "value").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) throw new IOException("World-control string is too long");
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_STRING_BYTES) throw new IOException("World-control string length is invalid");
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) throw new EOFException("World-control string is truncated");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeUuid(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
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
