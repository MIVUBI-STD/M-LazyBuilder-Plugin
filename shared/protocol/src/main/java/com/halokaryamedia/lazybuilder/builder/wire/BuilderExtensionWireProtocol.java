package com.halokaryamedia.lazybuilder.builder.wire;

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

/** Bounded transport-neutral Fabric↔Paper protocol for Builder non-block authorities. */
public final class BuilderExtensionWireProtocol {
    public static final int VERSION = 1;
    public static final int MAX_MESSAGE_BYTES = 48 * 1024;
    public static final int MAX_BATCH_ENTRIES = 256;
    public static final int CAPABILITY_BIOME = 1;
    public static final int CAPABILITY_BLOCK_ENTITY = 1 << 1;
    public static final int CAPABILITY_ENTITY = 1 << 2;
    public static final String CHANNEL = "lazybuilder:builder_ext";

    private BuilderExtensionWireProtocol() {}

    public sealed interface Request permits CapabilitiesRequest, ApplyBiomeBatch {}
    public sealed interface Response permits Capabilities, BatchResult, Error {}

    public record CapabilitiesRequest(String requestId) implements Request {
        public CapabilitiesRequest { requireId(requestId, "requestId"); }
    }

    public record BiomeMutation(
            int x,
            int y,
            int z,
            String beforeBiome,
            String afterBiome
    ) {
        public BiomeMutation {
            beforeBiome = requireResourceId(beforeBiome, "beforeBiome");
            afterBiome = requireResourceId(afterBiome, "afterBiome");
        }
    }

    public record ApplyBiomeBatch(
            String operationId,
            String dimensionId,
            List<BiomeMutation> entries
    ) implements Request {
        public ApplyBiomeBatch {
            requireId(operationId, "operationId");
            dimensionId = requireResourceId(dimensionId, "dimensionId");
            Objects.requireNonNull(entries, "entries");
            entries = List.copyOf(entries);
            if (entries.isEmpty() || entries.size() > MAX_BATCH_ENTRIES) {
                throw new IllegalArgumentException(
                        "biome batch entry count must be in 1.." + MAX_BATCH_ENTRIES);
            }
            for (BiomeMutation entry : entries) Objects.requireNonNull(entry, "entry");
        }
    }

    public record Capabilities(
            String requestId,
            int capabilityMask,
            int maxBatchEntries
    ) implements Response {
        public Capabilities {
            requireId(requestId, "requestId");
            int known = CAPABILITY_BIOME | CAPABILITY_BLOCK_ENTITY | CAPABILITY_ENTITY;
            if ((capabilityMask & ~known) != 0) {
                throw new IllegalArgumentException("capabilityMask contains unknown bits");
            }
            if (maxBatchEntries <= 0 || maxBatchEntries > MAX_BATCH_ENTRIES) {
                throw new IllegalArgumentException("maxBatchEntries out of range");
            }
        }

        public boolean supports(int capability) {
            return (capabilityMask & capability) != 0;
        }
    }

    public enum BatchState {
        COMPLETED,
        CONFLICT
    }

    public record BatchResult(
            String operationId,
            BatchState state,
            int processedEntries,
            int conflictIndex,
            String actualBiome
    ) implements Response {
        public BatchResult {
            requireId(operationId, "operationId");
            Objects.requireNonNull(state, "state");
            if (processedEntries < 0 || processedEntries > MAX_BATCH_ENTRIES) {
                throw new IllegalArgumentException("processedEntries out of range");
            }
            if (state == BatchState.COMPLETED) {
                if (conflictIndex != -1 || actualBiome != null) {
                    throw new IllegalArgumentException(
                            "COMPLETED result cannot contain conflict detail");
                }
            } else {
                if (conflictIndex < 0 || conflictIndex >= MAX_BATCH_ENTRIES) {
                    throw new IllegalArgumentException("conflictIndex out of range");
                }
                actualBiome = requireResourceId(actualBiome, "actualBiome");
            }
        }

        public static BatchResult completed(String operationId, int processedEntries) {
            return new BatchResult(
                    operationId, BatchState.COMPLETED, processedEntries, -1, null);
        }

        public static BatchResult conflict(
                String operationId,
                int processedEntries,
                int conflictIndex,
                String actualBiome
        ) {
            return new BatchResult(
                    operationId,
                    BatchState.CONFLICT,
                    processedEntries,
                    conflictIndex,
                    actualBiome
            );
        }
    }

    public record Error(String operationId, String message) implements Response {
        public Error {
            operationId = operationId == null || operationId.isBlank()
                    ? "unknown"
                    : safeText(operationId, 160);
            message = safeText(message, 320);
        }
    }

    public static byte[] encodeRequest(Request request) throws IOException {
        return encode(out -> writeRequest(out, Objects.requireNonNull(request, "request")));
    }

    public static byte[] encodeResponse(Response response) throws IOException {
        return encode(out -> writeResponse(out, Objects.requireNonNull(response, "response")));
    }

    public static Request decodeRequest(byte[] bytes) throws IOException {
        try (DataInputStream in = input(bytes)) {
            requireVersion(in.readUnsignedByte());
            Request request = switch (in.readUnsignedByte()) {
                case 1 -> new CapabilitiesRequest(readString(in, 160));
                case 2 -> {
                    String operationId = readString(in, 160);
                    String dimensionId = readString(in, 128);
                    int count = in.readUnsignedShort();
                    if (count <= 0 || count > MAX_BATCH_ENTRIES) {
                        throw new IOException("invalid biome batch count");
                    }
                    List<BiomeMutation> entries = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        entries.add(new BiomeMutation(
                                in.readInt(),
                                in.readInt(),
                                in.readInt(),
                                readString(in, 128),
                                readString(in, 128)
                        ));
                    }
                    yield new ApplyBiomeBatch(operationId, dimensionId, entries);
                }
                default -> throw new IOException("unknown Builder extension request");
            };
            requireExhausted(in);
            return request;
        }
    }

    public static Response decodeResponse(byte[] bytes) throws IOException {
        try (DataInputStream in = input(bytes)) {
            requireVersion(in.readUnsignedByte());
            Response response = switch (in.readUnsignedByte()) {
                case 1 -> new Capabilities(
                        readString(in, 160),
                        in.readInt(),
                        in.readUnsignedShort()
                );
                case 2 -> {
                    String operationId = readString(in, 160);
                    BatchState state = enumValue(
                            BatchState.values(), in.readUnsignedByte(), "batch state");
                    int processed = in.readUnsignedShort();
                    int conflictIndex = in.readInt();
                    String actual = readNullableString(in, 128);
                    yield new BatchResult(
                            operationId, state, processed, conflictIndex, actual);
                }
                case 3 -> new Error(readString(in, 160), readString(in, 320));
                default -> throw new IOException("unknown Builder extension response");
            };
            requireExhausted(in);
            return response;
        }
    }

    private static void writeRequest(DataOutputStream out, Request request)
            throws IOException {
        out.writeByte(VERSION);
        if (request instanceof CapabilitiesRequest capabilities) {
            out.writeByte(1);
            writeString(out, capabilities.requestId());
            return;
        }
        if (request instanceof ApplyBiomeBatch batch) {
            out.writeByte(2);
            writeString(out, batch.operationId());
            writeString(out, batch.dimensionId());
            out.writeShort(batch.entries().size());
            for (BiomeMutation entry : batch.entries()) {
                out.writeInt(entry.x());
                out.writeInt(entry.y());
                out.writeInt(entry.z());
                writeString(out, entry.beforeBiome());
                writeString(out, entry.afterBiome());
            }
            return;
        }
        throw new IOException("unsupported Builder extension request");
    }

    private static void writeResponse(DataOutputStream out, Response response)
            throws IOException {
        out.writeByte(VERSION);
        if (response instanceof Capabilities capabilities) {
            out.writeByte(1);
            writeString(out, capabilities.requestId());
            out.writeInt(capabilities.capabilityMask());
            out.writeShort(capabilities.maxBatchEntries());
            return;
        }
        if (response instanceof BatchResult result) {
            out.writeByte(2);
            writeString(out, result.operationId());
            out.writeByte(result.state().ordinal());
            out.writeShort(result.processedEntries());
            out.writeInt(result.conflictIndex());
            writeNullableString(out, result.actualBiome());
            return;
        }
        if (response instanceof Error error) {
            out.writeByte(3);
            writeString(out, error.operationId());
            writeString(out, error.message());
            return;
        }
        throw new IOException("unsupported Builder extension response");
    }

    private interface Writer {
        void write(DataOutputStream output) throws IOException;
    }

    private static byte[] encode(Writer writer) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            writer.write(out);
        }
        byte[] encoded = bytes.toByteArray();
        if (encoded.length > MAX_MESSAGE_BYTES) {
            throw new IOException("Builder extension payload exceeds "
                    + MAX_MESSAGE_BYTES + " bytes");
        }
        return encoded;
    }

    private static DataInputStream input(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 2 || bytes.length > MAX_MESSAGE_BYTES) {
            throw new IOException("invalid Builder extension payload size");
        }
        return new DataInputStream(new ByteArrayInputStream(bytes));
    }

    private static void requireVersion(int version) throws IOException {
        if (version != VERSION) {
            throw new IOException(
                    "unsupported Builder extension protocol version " + version);
        }
    }

    private static void requireExhausted(DataInputStream in) throws IOException {
        if (in.available() != 0) {
            throw new IOException("Builder extension payload contains trailing data");
        }
    }

    private static void writeString(DataOutputStream out, String value)
            throws IOException {
        byte[] bytes = safeText(value, 320).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 0xffff) throw new IOException("string too large");
        out.writeShort(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in, int maxChars)
            throws IOException {
        int size = in.readUnsignedShort();
        byte[] bytes = in.readNBytes(size);
        if (bytes.length != size) throw new EOFException();
        return safeText(new String(bytes, StandardCharsets.UTF_8), maxChars);
    }

    private static void writeNullableString(DataOutputStream out, String value)
            throws IOException {
        out.writeBoolean(value != null);
        if (value != null) writeString(out, value);
    }

    private static String readNullableString(DataInputStream in, int maxChars)
            throws IOException {
        return in.readBoolean() ? readString(in, maxChars) : null;
    }

    private static String safeText(String value, int maxChars) {
        String text = Objects.requireNonNullElse(value, "");
        return text.length() <= maxChars ? text : text.substring(0, maxChars);
    }

    private static void requireId(String id, String label) {
        if (id == null || id.isBlank() || id.length() > 160) {
            throw new IllegalArgumentException(label + " invalid");
        }
    }

    private static String requireResourceId(String id, String label) {
        if (id == null || id.isBlank() || id.length() > 128
                || id.indexOf(':') <= 0 || id.indexOf(' ') >= 0) {
            throw new IllegalArgumentException(label + " must be a namespaced id");
        }
        return id;
    }

    private static <T> T enumValue(T[] values, int ordinal, String label)
            throws IOException {
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IOException("invalid " + label);
        }
        return values[ordinal];
    }
}
