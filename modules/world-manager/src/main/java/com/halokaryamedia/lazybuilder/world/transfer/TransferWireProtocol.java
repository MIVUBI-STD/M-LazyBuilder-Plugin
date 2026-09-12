package com.halokaryamedia.lazybuilder.world.transfer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Small binary protocol shared by the Paper transport and Fabric client.
 *
 * <p>Each payload is one request or one response. File-data requests may use a
 * bounded credit window over Minecraft's already ordered/reliable play connection;
 * the Paper adapter still serializes each player's application-level processing.
 * No second socket, relay, HTTP service, or persistent transfer worker is needed.</p>
 */
public final class TransferWireProtocol {
    public static final int VERSION = 2;
    public static final int MAX_MESSAGE_BYTES = 30 * 1024;
    public static final int MAX_CHUNK_BYTES = 24 * 1024;
    public static final int PIPELINE_WINDOW = 4;
    private static final int MAX_STRING_BYTES = 1024;

    private static final int BEGIN_UPLOAD = 1;
    private static final int UPLOAD_CHUNK = 2;
    private static final int FINISH_UPLOAD = 3;
    private static final int ABORT_UPLOAD = 4;
    private static final int BEGIN_DOWNLOAD = 5;
    private static final int DOWNLOAD_CHUNK = 6;
    private static final int FINISH_DOWNLOAD = 7;
    private static final int ABORT_DOWNLOAD = 8;

    private static final int UPLOAD_ACCEPTED = 101;
    private static final int UPLOAD_PROGRESS = 102;
    private static final int UPLOAD_FINISHED = 103;
    private static final int DOWNLOAD_ACCEPTED = 104;
    private static final int DOWNLOAD_CHUNK_DATA = 105;
    private static final int ACK = 106;
    private static final int ERROR = 127;

    private TransferWireProtocol() {}

    public sealed interface Request permits BeginUpload, UploadChunk, FinishUpload, AbortUpload,
            BeginDownload, DownloadChunkRequest, FinishDownload, AbortDownload {}

    public record BeginUpload(String fileName, long totalBytes, String sha256) implements Request {
        public BeginUpload {
            fileName = requireString(fileName, "fileName");
            sha256 = requireString(sha256, "sha256");
            if (totalBytes < 0) throw new IllegalArgumentException("totalBytes must not be negative");
        }
    }

    public record UploadChunk(UUID sessionId, int chunkIndex, byte[] data) implements Request {
        public UploadChunk {
            Objects.requireNonNull(sessionId, "sessionId");
            if (chunkIndex < 0) throw new IllegalArgumentException("chunkIndex must not be negative");
            data = Arrays.copyOf(Objects.requireNonNull(data, "data"), data.length);
            if (data.length < 1 || data.length > MAX_CHUNK_BYTES) {
                throw new IllegalArgumentException("Upload chunk size is invalid");
            }
        }
        @Override public byte[] data() { return Arrays.copyOf(data, data.length); }
    }

    public record FinishUpload(UUID sessionId) implements Request {
        public FinishUpload { Objects.requireNonNull(sessionId, "sessionId"); }
    }

    public record AbortUpload(UUID sessionId) implements Request {
        public AbortUpload { Objects.requireNonNull(sessionId, "sessionId"); }
    }

    public record BeginDownload(String fileName) implements Request {
        public BeginDownload { fileName = requireString(fileName, "fileName"); }
    }

    public record DownloadChunkRequest(UUID sessionId, int chunkIndex) implements Request {
        public DownloadChunkRequest {
            Objects.requireNonNull(sessionId, "sessionId");
            if (chunkIndex < 0) throw new IllegalArgumentException("chunkIndex must not be negative");
        }
    }

    public record FinishDownload(UUID sessionId) implements Request {
        public FinishDownload { Objects.requireNonNull(sessionId, "sessionId"); }
    }

    public record AbortDownload(UUID sessionId) implements Request {
        public AbortDownload { Objects.requireNonNull(sessionId, "sessionId"); }
    }

    public sealed interface Response permits UploadAccepted, UploadProgressResponse, UploadFinished,
            DownloadAccepted, DownloadChunkData, Ack, ErrorResponse {}

    public record UploadAccepted(TransferDescriptor descriptor) implements Response {
        public UploadAccepted { Objects.requireNonNull(descriptor, "descriptor"); }
    }

    public record UploadProgressResponse(
            UUID sessionId,
            long receivedBytes,
            long totalBytes,
            int nextChunkIndex,
            int totalChunks
    ) implements Response {
        public UploadProgressResponse {
            Objects.requireNonNull(sessionId, "sessionId");
            if (receivedBytes < 0 || totalBytes < 0 || receivedBytes > totalBytes) {
                throw new IllegalArgumentException("Upload progress byte counts are invalid");
            }
            if (nextChunkIndex < 0 || totalChunks < 0 || nextChunkIndex > totalChunks) {
                throw new IllegalArgumentException("Upload progress chunk counts are invalid");
            }
        }
    }

    public record UploadFinished(UUID sessionId, String fileName) implements Response {
        public UploadFinished {
            Objects.requireNonNull(sessionId, "sessionId");
            fileName = requireString(fileName, "fileName");
        }
    }

    public record DownloadAccepted(TransferDescriptor descriptor) implements Response {
        public DownloadAccepted { Objects.requireNonNull(descriptor, "descriptor"); }
    }

    public record DownloadChunkData(UUID sessionId, int chunkIndex, boolean last, byte[] data) implements Response {
        public DownloadChunkData {
            Objects.requireNonNull(sessionId, "sessionId");
            if (chunkIndex < 0) throw new IllegalArgumentException("chunkIndex must not be negative");
            data = Arrays.copyOf(Objects.requireNonNull(data, "data"), data.length);
            if (data.length < 1 || data.length > MAX_CHUNK_BYTES) {
                throw new IllegalArgumentException("Download chunk size is invalid");
            }
        }
        @Override public byte[] data() { return Arrays.copyOf(data, data.length); }
    }

    public record Ack(int requestOpcode) implements Response {}

    public record ErrorResponse(String message) implements Response {
        public ErrorResponse { message = requireString(message, "message"); }
    }

    public static byte[] encodeRequest(Request request) throws IOException {
        Objects.requireNonNull(request, "request");
        return write(opcode(request), out -> {
            switch (request) {
                case BeginUpload begin -> {
                    writeString(out, begin.fileName());
                    out.writeLong(begin.totalBytes());
                    writeString(out, begin.sha256());
                }
                case UploadChunk chunk -> {
                    writeUuid(out, chunk.sessionId());
                    out.writeInt(chunk.chunkIndex());
                    byte[] data = chunk.data();
                    out.writeInt(data.length);
                    out.write(data);
                }
                case FinishUpload finish -> writeUuid(out, finish.sessionId());
                case AbortUpload abort -> writeUuid(out, abort.sessionId());
                case BeginDownload begin -> writeString(out, begin.fileName());
                case DownloadChunkRequest chunk -> {
                    writeUuid(out, chunk.sessionId());
                    out.writeInt(chunk.chunkIndex());
                }
                case FinishDownload finish -> writeUuid(out, finish.sessionId());
                case AbortDownload abort -> writeUuid(out, abort.sessionId());
            }
        });
    }

    public static Request decodeRequest(byte[] payload) throws IOException {
        try (DataInputStream in = input(payload)) {
            int opcode = readHeader(in);
            Request request = switch (opcode) {
                case BEGIN_UPLOAD -> new BeginUpload(readString(in), in.readLong(), readString(in));
                case UPLOAD_CHUNK -> {
                    UUID id = readUuid(in);
                    int index = in.readInt();
                    int length = in.readInt();
                    if (length < 1 || length > MAX_CHUNK_BYTES) throw new IOException("Transfer chunk size is invalid");
                    byte[] data = in.readNBytes(length);
                    if (data.length != length) throw new EOFException("Transfer chunk is truncated");
                    yield new UploadChunk(id, index, data);
                }
                case FINISH_UPLOAD -> new FinishUpload(readUuid(in));
                case ABORT_UPLOAD -> new AbortUpload(readUuid(in));
                case BEGIN_DOWNLOAD -> new BeginDownload(readString(in));
                case DOWNLOAD_CHUNK -> new DownloadChunkRequest(readUuid(in), in.readInt());
                case FINISH_DOWNLOAD -> new FinishDownload(readUuid(in));
                case ABORT_DOWNLOAD -> new AbortDownload(readUuid(in));
                default -> throw new IOException("Unknown transfer request opcode: " + opcode);
            };
            requireExhausted(in, "request");
            return request;
        }
    }

    public static byte[] encodeResponse(Response response) throws IOException {
        Objects.requireNonNull(response, "response");
        int opcode = switch (response) {
            case UploadAccepted ignored -> UPLOAD_ACCEPTED;
            case UploadProgressResponse ignored -> UPLOAD_PROGRESS;
            case UploadFinished ignored -> UPLOAD_FINISHED;
            case DownloadAccepted ignored -> DOWNLOAD_ACCEPTED;
            case DownloadChunkData ignored -> DOWNLOAD_CHUNK_DATA;
            case Ack ignored -> ACK;
            case ErrorResponse ignored -> ERROR;
        };
        return write(opcode, out -> {
            switch (response) {
                case UploadAccepted accepted -> writeDescriptor(out, accepted.descriptor());
                case UploadProgressResponse progress -> {
                    writeUuid(out, progress.sessionId());
                    out.writeLong(progress.receivedBytes());
                    out.writeLong(progress.totalBytes());
                    out.writeInt(progress.nextChunkIndex());
                    out.writeInt(progress.totalChunks());
                }
                case UploadFinished finished -> {
                    writeUuid(out, finished.sessionId());
                    writeString(out, finished.fileName());
                }
                case DownloadAccepted accepted -> writeDescriptor(out, accepted.descriptor());
                case DownloadChunkData chunk -> {
                    writeUuid(out, chunk.sessionId());
                    out.writeInt(chunk.chunkIndex());
                    out.writeBoolean(chunk.last());
                    byte[] data = chunk.data();
                    out.writeInt(data.length);
                    out.write(data);
                }
                case Ack ack -> out.writeByte(ack.requestOpcode());
                case ErrorResponse error -> writeString(out, error.message());
            }
        });
    }

    public static Response decodeResponse(byte[] payload) throws IOException {
        try (DataInputStream in = input(payload)) {
            int opcode = readHeader(in);
            Response response = switch (opcode) {
                case UPLOAD_ACCEPTED -> new UploadAccepted(readDescriptor(in));
                case UPLOAD_PROGRESS -> new UploadProgressResponse(
                        readUuid(in), in.readLong(), in.readLong(), in.readInt(), in.readInt());
                case UPLOAD_FINISHED -> new UploadFinished(readUuid(in), readString(in));
                case DOWNLOAD_ACCEPTED -> new DownloadAccepted(readDescriptor(in));
                case DOWNLOAD_CHUNK_DATA -> {
                    UUID id = readUuid(in);
                    int index = in.readInt();
                    boolean last = in.readBoolean();
                    int length = in.readInt();
                    if (length < 1 || length > MAX_CHUNK_BYTES) throw new IOException("Download chunk size is invalid");
                    byte[] data = in.readNBytes(length);
                    if (data.length != length) throw new EOFException("Download chunk is truncated");
                    yield new DownloadChunkData(id, index, last, data);
                }
                case ACK -> new Ack(in.readUnsignedByte());
                case ERROR -> new ErrorResponse(readString(in));
                default -> throw new IOException("Unknown transfer response opcode: " + opcode);
            };
            requireExhausted(in, "response");
            return response;
        }
    }

    public static byte[] uploadAccepted(TransferDescriptor descriptor) throws IOException {
        return encodeResponse(new UploadAccepted(descriptor));
    }

    public static byte[] uploadProgress(
            UUID sessionId,
            long receivedBytes,
            long totalBytes,
            int nextChunkIndex,
            int totalChunks
    ) throws IOException {
        return encodeResponse(new UploadProgressResponse(
                sessionId, receivedBytes, totalBytes, nextChunkIndex, totalChunks));
    }

    public static byte[] uploadFinished(UUID sessionId, String fileName) throws IOException {
        return encodeResponse(new UploadFinished(sessionId, fileName));
    }

    public static byte[] downloadAccepted(TransferDescriptor descriptor) throws IOException {
        return encodeResponse(new DownloadAccepted(descriptor));
    }

    public static byte[] downloadChunk(UUID sessionId, int chunkIndex, byte[] data, boolean last) throws IOException {
        return encodeResponse(new DownloadChunkData(sessionId, chunkIndex, last, data));
    }

    public static byte[] ack(int requestOpcode) throws IOException {
        return encodeResponse(new Ack(requestOpcode));
    }

    public static byte[] error(String message) {
        String safe = Objects.requireNonNullElse(message, "Transfer request failed").strip();
        if (safe.isEmpty()) safe = "Transfer request failed";
        if (safe.length() > 512) safe = safe.substring(0, 512);
        try {
            return encodeResponse(new ErrorResponse(safe));
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static int opcode(Request request) {
        return switch (request) {
            case BeginUpload ignored -> BEGIN_UPLOAD;
            case UploadChunk ignored -> UPLOAD_CHUNK;
            case FinishUpload ignored -> FINISH_UPLOAD;
            case AbortUpload ignored -> ABORT_UPLOAD;
            case BeginDownload ignored -> BEGIN_DOWNLOAD;
            case DownloadChunkRequest ignored -> DOWNLOAD_CHUNK;
            case FinishDownload ignored -> FINISH_DOWNLOAD;
            case AbortDownload ignored -> ABORT_DOWNLOAD;
        };
    }

    private static DataInputStream input(byte[] payload) throws IOException {
        Objects.requireNonNull(payload, "payload");
        if (payload.length < 2 || payload.length > MAX_MESSAGE_BYTES) {
            throw new IOException("Transfer payload size is invalid");
        }
        return new DataInputStream(new ByteArrayInputStream(payload));
    }

    private static int readHeader(DataInputStream in) throws IOException {
        int version = in.readUnsignedByte();
        if (version != VERSION) throw new IOException("Unsupported transfer protocol version: " + version);
        return in.readUnsignedByte();
    }

    private static void requireExhausted(DataInputStream in, String label) throws IOException {
        if (in.available() != 0) throw new IOException("Transfer " + label + " contains trailing bytes");
    }

    private static byte[] write(int opcode, Writer writer) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeByte(VERSION);
            out.writeByte(opcode);
            writer.write(out);
        }
        byte[] payload = buffer.toByteArray();
        if (payload.length > MAX_MESSAGE_BYTES) throw new IOException("Transfer message exceeds wire limit");
        return payload;
    }

    private static void writeDescriptor(DataOutputStream out, TransferDescriptor descriptor) throws IOException {
        writeUuid(out, descriptor.sessionId());
        writeString(out, descriptor.fileName());
        out.writeLong(descriptor.totalBytes());
        out.writeInt(descriptor.chunkBytes());
        out.writeInt(descriptor.totalChunks());
        writeString(out, descriptor.sha256());
    }

    private static TransferDescriptor readDescriptor(DataInputStream in) throws IOException {
        return new TransferDescriptor(
                readUuid(in), readString(in), in.readLong(), in.readInt(), in.readInt(), readString(in));
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_STRING_BYTES) throw new IOException("Transfer string length is invalid");
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) throw new EOFException("Transfer string is truncated");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = requireString(value, "value").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) throw new IOException("Transfer string is too long");
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static UUID readUuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    private static void writeUuid(DataOutputStream out, UUID id) throws IOException {
        Objects.requireNonNull(id, "id");
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
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
