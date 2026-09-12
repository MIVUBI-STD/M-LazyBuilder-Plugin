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
 * Small binary protocol shared by the Paper transfer adapter and the future client mod.
 *
 * <p>Each payload is one request or one response. Upload/download chunks use a
 * stop-and-wait flow: the client waits for the server response before sending the
 * next request. This keeps ordering deterministic without a background network worker.</p>
 */
public final class TransferWireProtocol {
    public static final int VERSION = 1;
    public static final int MAX_MESSAGE_BYTES = 30 * 1024;
    public static final int MAX_CHUNK_BYTES = 24 * 1024;
    private static final int MAX_STRING_BYTES = 1024;

    private TransferWireProtocol() {}

    public sealed interface Request permits BeginUpload, UploadChunk, FinishUpload, AbortUpload,
            BeginDownload, DownloadChunkRequest, FinishDownload, AbortDownload {}

    public record BeginUpload(String fileName, long totalBytes, String sha256) implements Request {}
    public record UploadChunk(UUID sessionId, int chunkIndex, byte[] data) implements Request {
        public UploadChunk {
            Objects.requireNonNull(sessionId, "sessionId");
            data = Arrays.copyOf(Objects.requireNonNull(data, "data"), data.length);
        }
        @Override public byte[] data() { return Arrays.copyOf(data, data.length); }
    }
    public record FinishUpload(UUID sessionId) implements Request {}
    public record AbortUpload(UUID sessionId) implements Request {}
    public record BeginDownload(String fileName) implements Request {}
    public record DownloadChunkRequest(UUID sessionId, int chunkIndex) implements Request {}
    public record FinishDownload(UUID sessionId) implements Request {}
    public record AbortDownload(UUID sessionId) implements Request {}

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

    public static Request decodeRequest(byte[] payload) throws IOException {
        Objects.requireNonNull(payload, "payload");
        if (payload.length < 2 || payload.length > MAX_MESSAGE_BYTES) {
            throw new IOException("Transfer payload size is invalid");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            int version = in.readUnsignedByte();
            if (version != VERSION) throw new IOException("Unsupported transfer protocol version: " + version);
            int opcode = in.readUnsignedByte();
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
                default -> throw new IOException("Unknown transfer opcode: " + opcode);
            };
            if (in.available() != 0) throw new IOException("Transfer payload contains trailing bytes");
            return request;
        }
    }

    public static byte[] uploadAccepted(TransferDescriptor descriptor) throws IOException {
        return descriptorResponse(UPLOAD_ACCEPTED, descriptor);
    }

    public static byte[] uploadProgress(UUID sessionId, TransferSessionService.UploadProgress progress) throws IOException {
        return write(UPLOAD_PROGRESS, out -> {
            writeUuid(out, sessionId);
            out.writeLong(progress.receivedBytes());
            out.writeLong(progress.totalBytes());
            out.writeInt(progress.nextChunkIndex());
            out.writeInt(progress.totalChunks());
        });
    }

    public static byte[] uploadFinished(UUID sessionId, String fileName) throws IOException {
        return write(UPLOAD_FINISHED, out -> {
            writeUuid(out, sessionId);
            writeString(out, fileName);
        });
    }

    public static byte[] downloadAccepted(TransferDescriptor descriptor) throws IOException {
        return descriptorResponse(DOWNLOAD_ACCEPTED, descriptor);
    }

    public static byte[] downloadChunk(UUID sessionId, TransferSessionService.DownloadChunk chunk) throws IOException {
        return write(DOWNLOAD_CHUNK_DATA, out -> {
            writeUuid(out, sessionId);
            out.writeInt(chunk.chunkIndex());
            out.writeBoolean(chunk.last());
            byte[] bytes = chunk.bytes();
            if (bytes.length > MAX_CHUNK_BYTES) throw new IOException("Download chunk exceeds wire limit");
            out.writeInt(bytes.length);
            out.write(bytes);
        });
    }

    public static byte[] ack(int requestOpcode) throws IOException {
        return write(ACK, out -> out.writeByte(requestOpcode));
    }

    public static byte[] error(String message) {
        String safe = Objects.requireNonNullElse(message, "Transfer request failed");
        if (safe.length() > 512) safe = safe.substring(0, 512);
        try {
            String finalSafe = safe;
            return write(ERROR, out -> writeString(out, finalSafe));
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

    private static byte[] descriptorResponse(int opcode, TransferDescriptor descriptor) throws IOException {
        return write(opcode, out -> {
            writeUuid(out, descriptor.sessionId());
            writeString(out, descriptor.fileName());
            out.writeLong(descriptor.totalBytes());
            out.writeInt(descriptor.chunkBytes());
            out.writeInt(descriptor.totalChunks());
            writeString(out, descriptor.sha256());
        });
    }

    private static byte[] write(int opcode, Writer writer) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeByte(VERSION);
            out.writeByte(opcode);
            writer.write(out);
        }
        byte[] payload = buffer.toByteArray();
        if (payload.length > MAX_MESSAGE_BYTES) throw new IOException("Transfer response exceeds wire limit");
        return payload;
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_STRING_BYTES) throw new IOException("Transfer string length is invalid");
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) throw new EOFException("Transfer string is truncated");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = Objects.requireNonNull(value, "value").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) throw new IOException("Transfer string is too long");
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static UUID readUuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    private static void writeUuid(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
    }

    @FunctionalInterface
    private interface Writer { void write(DataOutputStream out) throws IOException; }
}
