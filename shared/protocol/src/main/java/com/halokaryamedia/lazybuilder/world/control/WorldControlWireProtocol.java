package com.halokaryamedia.lazybuilder.world.control;

import com.halokaryamedia.lazybuilder.world.export.ExportSettingsWire;

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
    /** V6 adds shared export-only settings to full-world export requests. */
    public static final int VERSION = 6;
    public static final int MAX_MESSAGE_BYTES = 64 * 1024;
    private static final int MAX_STRING_BYTES = 1024;
    private static final int MAX_WORLDS = 4096;
    private static final int MAX_FORMATS = 256;

    private static final int LIST = 1;
    private static final int CREATE = 2;
    private static final int TELEPORT = 5;
    private static final int ARCHIVE = 6;
    private static final int RESTORE = 7;
    private static final int DUPLICATE = 8;
    private static final int DELETE = 9;
    private static final int GET_SETTINGS = 10;
    private static final int SET_DEFAULT_MODE = 12;
    private static final int SET_DIFFICULTY = 13;
    private static final int SET_PVP = 14;
    private static final int RESET_BUILD_READY = 15;
    private static final int SET_SPAWN_HERE = 16;
    private static final int EXPORT_WORLD = 17;
    private static final int IMPORT_WORLD = 18;
    private static final int GET_EXPORT_FORMATS = 19;
    private static final int INSPECT_IMPORT = 20;
    private static final int DISCARD_IMPORT = 21;

    private static final int WORLDS = 101;
    private static final int WORLD_CHANGED = 102;
    private static final int TELEPORT_OK = 103;
    private static final int SETTINGS = 104;
    private static final int EXPORT_READY = 105;
    private static final int EXPORT_FORMATS = 106;
    private static final int IMPORT_INSPECTION = 107;
    private static final int ERROR = 127;

    private WorldControlWireProtocol() {}

    public sealed interface Request permits ListWorlds, CreateWorld, TeleportWorld,
            ArchiveWorld, RestoreWorld, DuplicateWorld, DeleteWorld, GetSettings,
            SetDefaultMode, SetDifficulty, SetPvp, ResetBuildReady, SetSpawnHere,
            ExportWorld, ImportWorld, GetExportFormats, InspectImport, DiscardImport {}

    public record ListWorlds() implements Request {}
    public record GetExportFormats() implements Request {}
    public record InspectImport(String artifactName) implements Request {
        public InspectImport { artifactName = requireString(artifactName, "artifactName"); }
    }
    public record DiscardImport(String artifactName) implements Request {
        public DiscardImport { artifactName = requireString(artifactName, "artifactName"); }
    }

    public record CreateWorld(String folderName, String displayName, String kind) implements Request {
        public CreateWorld {
            folderName = requireString(folderName, "folderName");
            displayName = requireString(displayName, "displayName");
            kind = requireString(kind, "kind");
        }
    }

    public record TeleportWorld(UUID worldId) implements Request { public TeleportWorld { Objects.requireNonNull(worldId); } }
    public record ArchiveWorld(UUID worldId) implements Request { public ArchiveWorld { Objects.requireNonNull(worldId); } }
    public record RestoreWorld(UUID worldId) implements Request { public RestoreWorld { Objects.requireNonNull(worldId); } }
    public record GetSettings(UUID worldId) implements Request { public GetSettings { Objects.requireNonNull(worldId); } }

    public record SetDefaultMode(UUID worldId, String gameMode) implements Request {
        public SetDefaultMode { Objects.requireNonNull(worldId); gameMode = requireString(gameMode, "gameMode"); }
    }
    public record SetDifficulty(UUID worldId, String difficulty) implements Request {
        public SetDifficulty { Objects.requireNonNull(worldId); difficulty = requireString(difficulty, "difficulty"); }
    }
    public record SetPvp(UUID worldId, boolean enabled) implements Request { public SetPvp { Objects.requireNonNull(worldId); } }
    public record ResetBuildReady(UUID worldId) implements Request { public ResetBuildReady { Objects.requireNonNull(worldId); } }
    public record SetSpawnHere(UUID worldId) implements Request { public SetSpawnHere { Objects.requireNonNull(worldId); } }

    public record DuplicateWorld(UUID sourceWorldId, String destinationFolder, String displayName) implements Request {
        public DuplicateWorld {
            Objects.requireNonNull(sourceWorldId, "sourceWorldId");
            destinationFolder = requireString(destinationFolder, "destinationFolder");
            displayName = requireString(displayName, "displayName");
        }
    }

    public record DeleteWorld(UUID worldId, String typedDisplayName) implements Request {
        public DeleteWorld {
            Objects.requireNonNull(worldId, "worldId");
            typedDisplayName = requireString(typedDisplayName, "typedDisplayName");
        }
    }

    public record ExportWorld(
            UUID worldId,
            String targetFormat,
            String artifactName,
            ExportSettingsWire.Settings settings
    ) implements Request {
        public ExportWorld(UUID worldId, String targetFormat, String artifactName) {
            this(worldId, targetFormat, artifactName, ExportSettingsWire.Settings.inherit());
        }

        public ExportWorld {
            Objects.requireNonNull(worldId, "worldId");
            targetFormat = requireString(targetFormat, "targetFormat");
            artifactName = requireString(artifactName, "artifactName");
            settings = Objects.requireNonNull(settings, "settings");
        }
    }

    public record ImportWorld(String artifactName, String destinationFolder, String displayName) implements Request {
        public ImportWorld {
            artifactName = requireString(artifactName, "artifactName");
            destinationFolder = requireString(destinationFolder, "destinationFolder");
            displayName = requireString(displayName, "displayName");
        }
    }

    public sealed interface Response permits WorldList, WorldChanged, TeleportOk, SettingsSnapshot,
            ExportReady, ExportFormats, ImportInspection, ErrorResponse {}

    public record ImportInspection(String artifactName, String edition, String sourceVersion, String suggestedName)
            implements Response {
        public ImportInspection {
            artifactName = requireString(artifactName, "artifactName");
            edition = requireString(edition, "edition");
            sourceVersion = requireString(sourceVersion, "sourceVersion");
            suggestedName = requireString(suggestedName, "suggestedName");
        }
    }

    public record WorldSummary(
            UUID worldId,
            String folderName,
            String displayName,
            String kind,
            String lifecycle,
            String defaultGameMode
    ) {
        public WorldSummary {
            Objects.requireNonNull(worldId, "worldId");
            folderName = requireString(folderName, "folderName");
            displayName = requireString(displayName, "displayName");
            kind = requireString(kind, "kind");
            lifecycle = requireString(lifecycle, "lifecycle");
            defaultGameMode = requireString(defaultGameMode, "defaultGameMode");
        }
    }

    public record SettingsSnapshot(
            UUID worldId,
            String defaultGameMode,
            String difficulty,
            boolean pvpEnabled,
            String weather,
            long timeOfDayTicks,
            double spawnX,
            double spawnY,
            double spawnZ
    ) implements Response {
        public SettingsSnapshot {
            Objects.requireNonNull(worldId, "worldId");
            defaultGameMode = requireString(defaultGameMode, "defaultGameMode");
            difficulty = requireString(difficulty, "difficulty");
            weather = requireString(weather, "weather");
        }
    }

    public record ExportReady(UUID worldId, String artifactName, String targetFormat) implements Response {
        public ExportReady {
            Objects.requireNonNull(worldId, "worldId");
            artifactName = requireString(artifactName, "artifactName");
            targetFormat = requireString(targetFormat, "targetFormat");
        }
    }

    public record ExportFormats(List<String> formats) implements Response {
        public ExportFormats {
            formats = List.copyOf(Objects.requireNonNull(formats, "formats"));
            if (formats.isEmpty() || formats.size() > MAX_FORMATS) {
                throw new IllegalArgumentException("Export format count is invalid");
            }
            for (String format : formats) requireString(format, "format");
        }
    }

    public record WorldList(List<WorldSummary> worlds, boolean canManage, boolean canTeleport) implements Response {
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
                case GetExportFormats ignored -> { }
                case InspectImport inspect -> writeString(out, inspect.artifactName());
                case DiscardImport discard -> writeString(out, discard.artifactName());
                case CreateWorld create -> {
                    writeString(out, create.folderName());
                    writeString(out, create.displayName());
                    writeString(out, create.kind());
                }
                case TeleportWorld teleport -> writeUuid(out, teleport.worldId());
                case ArchiveWorld archive -> writeUuid(out, archive.worldId());
                case RestoreWorld restore -> writeUuid(out, restore.worldId());
                case GetSettings settings -> writeUuid(out, settings.worldId());
                case SetDefaultMode setting -> { writeUuid(out, setting.worldId()); writeString(out, setting.gameMode()); }
                case SetDifficulty setting -> { writeUuid(out, setting.worldId()); writeString(out, setting.difficulty()); }
                case SetPvp setting -> { writeUuid(out, setting.worldId()); out.writeBoolean(setting.enabled()); }
                case ResetBuildReady setting -> writeUuid(out, setting.worldId());
                case SetSpawnHere setting -> writeUuid(out, setting.worldId());
                case DuplicateWorld duplicate -> {
                    writeUuid(out, duplicate.sourceWorldId());
                    writeString(out, duplicate.destinationFolder());
                    writeString(out, duplicate.displayName());
                }
                case DeleteWorld delete -> {
                    writeUuid(out, delete.worldId());
                    writeString(out, delete.typedDisplayName());
                }
                case ExportWorld export -> {
                    writeUuid(out, export.worldId());
                    writeString(out, export.targetFormat());
                    writeString(out, export.artifactName());
                    ExportSettingsWire.write(out, export.settings());
                }
                case ImportWorld importWorld -> {
                    writeString(out, importWorld.artifactName());
                    writeString(out, importWorld.destinationFolder());
                    writeString(out, importWorld.displayName());
                }
            }
        });
    }

    public static Request decodeRequest(byte[] payload) throws IOException {
        try (DataInputStream in = input(payload)) {
            int opcode = readHeader(in);
            Request request = switch (opcode) {
                case LIST -> new ListWorlds();
                case CREATE -> new CreateWorld(readString(in), readString(in), readString(in));
                case TELEPORT -> new TeleportWorld(readUuid(in));
                case ARCHIVE -> new ArchiveWorld(readUuid(in));
                case RESTORE -> new RestoreWorld(readUuid(in));
                case DUPLICATE -> new DuplicateWorld(readUuid(in), readString(in), readString(in));
                case DELETE -> new DeleteWorld(readUuid(in), readString(in));
                case GET_SETTINGS -> new GetSettings(readUuid(in));
                case SET_DEFAULT_MODE -> new SetDefaultMode(readUuid(in), readString(in));
                case SET_DIFFICULTY -> new SetDifficulty(readUuid(in), readString(in));
                case SET_PVP -> new SetPvp(readUuid(in), in.readBoolean());
                case RESET_BUILD_READY -> new ResetBuildReady(readUuid(in));
                case SET_SPAWN_HERE -> new SetSpawnHere(readUuid(in));
                case EXPORT_WORLD -> new ExportWorld(
                        readUuid(in), readString(in), readString(in), ExportSettingsWire.read(in));
                case IMPORT_WORLD -> new ImportWorld(readString(in), readString(in), readString(in));
                case GET_EXPORT_FORMATS -> new GetExportFormats();
                case INSPECT_IMPORT -> new InspectImport(readString(in));
                case DISCARD_IMPORT -> new DiscardImport(readString(in));
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
            case SettingsSnapshot ignored -> SETTINGS;
            case ExportReady ignored -> EXPORT_READY;
            case ExportFormats ignored -> EXPORT_FORMATS;
            case ImportInspection ignored -> IMPORT_INSPECTION;
            case ErrorResponse ignored -> ERROR;
        };
        return write(opcode, out -> {
            switch (response) {
                case WorldList list -> {
                    out.writeBoolean(list.canManage());
                    out.writeBoolean(list.canTeleport());
                    out.writeInt(list.worlds().size());
                    for (WorldSummary world : list.worlds()) writeWorld(out, world);
                }
                case WorldChanged changed -> {
                    writeString(out, changed.action());
                    writeWorld(out, changed.world());
                }
                case TeleportOk ok -> writeWorld(out, ok.world());
                case SettingsSnapshot settings -> writeSettings(out, settings);
                case ExportReady export -> {
                    writeUuid(out, export.worldId());
                    writeString(out, export.artifactName());
                    writeString(out, export.targetFormat());
                }
                case ExportFormats formats -> {
                    out.writeInt(formats.formats().size());
                    for (String format : formats.formats()) writeString(out, format);
                }
                case ImportInspection inspection -> {
                    writeString(out, inspection.artifactName());
                    writeString(out, inspection.edition());
                    writeString(out, inspection.sourceVersion());
                    writeString(out, inspection.suggestedName());
                }
                case ErrorResponse error -> writeString(out, error.message());
            }
        });
    }

    public static Response decodeResponse(byte[] payload) throws IOException {
        try (DataInputStream in = input(payload)) {
            int opcode = readHeader(in);
            Response response = switch (opcode) {
                case WORLDS -> {
                    boolean canManage = in.readBoolean();
                    boolean canTeleport = in.readBoolean();
                    int count = in.readInt();
                    if (count < 0 || count > MAX_WORLDS) throw new IOException("World count is invalid");
                    List<WorldSummary> worlds = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) worlds.add(readWorld(in));
                    yield new WorldList(worlds, canManage, canTeleport);
                }
                case WORLD_CHANGED -> new WorldChanged(readString(in), readWorld(in));
                case TELEPORT_OK -> new TeleportOk(readWorld(in));
                case SETTINGS -> readSettings(in);
                case EXPORT_READY -> new ExportReady(readUuid(in), readString(in), readString(in));
                case EXPORT_FORMATS -> {
                    int count = in.readInt();
                    if (count <= 0 || count > MAX_FORMATS) throw new IOException("Export format count is invalid");
                    List<String> formats = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) formats.add(readString(in));
                    yield new ExportFormats(formats);
                }
                case IMPORT_INSPECTION -> new ImportInspection(
                        readString(in), readString(in), readString(in), readString(in));
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
            case TeleportWorld ignored -> TELEPORT;
            case ArchiveWorld ignored -> ARCHIVE;
            case RestoreWorld ignored -> RESTORE;
            case DuplicateWorld ignored -> DUPLICATE;
            case DeleteWorld ignored -> DELETE;
            case GetSettings ignored -> GET_SETTINGS;
            case SetDefaultMode ignored -> SET_DEFAULT_MODE;
            case SetDifficulty ignored -> SET_DIFFICULTY;
            case SetPvp ignored -> SET_PVP;
            case ResetBuildReady ignored -> RESET_BUILD_READY;
            case SetSpawnHere ignored -> SET_SPAWN_HERE;
            case ExportWorld ignored -> EXPORT_WORLD;
            case ImportWorld ignored -> IMPORT_WORLD;
            case GetExportFormats ignored -> GET_EXPORT_FORMATS;
            case InspectImport ignored -> INSPECT_IMPORT;
            case DiscardImport ignored -> DISCARD_IMPORT;
        };
    }

    private static void writeWorld(DataOutputStream out, WorldSummary world) throws IOException {
        writeUuid(out, world.worldId());
        writeString(out, world.folderName());
        writeString(out, world.displayName());
        writeString(out, world.kind());
        writeString(out, world.lifecycle());
        writeString(out, world.defaultGameMode());
    }

    private static WorldSummary readWorld(DataInputStream in) throws IOException {
        return new WorldSummary(
                readUuid(in), readString(in), readString(in), readString(in),
                readString(in), readString(in));
    }

    private static void writeSettings(DataOutputStream out, SettingsSnapshot settings) throws IOException {
        writeUuid(out, settings.worldId());
        writeString(out, settings.defaultGameMode());
        writeString(out, settings.difficulty());
        out.writeBoolean(settings.pvpEnabled());
        writeString(out, settings.weather());
        out.writeLong(settings.timeOfDayTicks());
        out.writeDouble(settings.spawnX());
        out.writeDouble(settings.spawnY());
        out.writeDouble(settings.spawnZ());
    }

    private static SettingsSnapshot readSettings(DataInputStream in) throws IOException {
        return new SettingsSnapshot(
                readUuid(in), readString(in), readString(in), in.readBoolean(),
                readString(in), in.readLong(), in.readDouble(), in.readDouble(), in.readDouble());
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
