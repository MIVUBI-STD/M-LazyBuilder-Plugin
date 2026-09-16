package com.halokaryamedia.lazybuilder.utility.telemetry;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

/** Neutral Paper/Fabric contract for the Compact Debug server telemetry channel. */
public final class UtilityTelemetryWireProtocol {
    public static final String CHANNEL = "lazybuilder:utility_telemetry";
    public static final int VERSION = 1;
    public static final int MAX_MESSAGE_BYTES = 512;
    public static final int MAX_WORLD_NAME_LENGTH = 128;

    private static final byte TYPE_SUBSCRIBE = 1;
    private static final byte TYPE_SNAPSHOT = 2;

    private UtilityTelemetryWireProtocol() {
    }

    public sealed interface Request permits Subscribe {
    }

    public record Subscribe() implements Request {
    }

    public sealed interface Response permits Snapshot {
    }

    public record Snapshot(
            String worldName,
            double cpuPercent,
            long usedMemoryBytes,
            long maxMemoryBytes
    ) implements Response {
        public Snapshot {
            worldName = validateWorldName(worldName);
            if ((!Double.isNaN(cpuPercent) && !Double.isFinite(cpuPercent))
                    || (Double.isFinite(cpuPercent) && (cpuPercent < 0.0D || cpuPercent > 100.0D))) {
                throw new IllegalArgumentException("cpuPercent must be NaN or within 0..100");
            }
            if (usedMemoryBytes < 0L) throw new IllegalArgumentException("usedMemoryBytes must be >= 0");
            if (maxMemoryBytes <= 0L) throw new IllegalArgumentException("maxMemoryBytes must be > 0");
            if (usedMemoryBytes > maxMemoryBytes) {
                throw new IllegalArgumentException("usedMemoryBytes must not exceed maxMemoryBytes");
            }
        }
    }

    public static byte[] subscribe() {
        return encode(TYPE_SUBSCRIBE, out -> { });
    }

    public static byte[] snapshot(String worldName, double cpuPercent, long usedMemoryBytes, long maxMemoryBytes) {
        Snapshot snapshot = new Snapshot(worldName, cpuPercent, usedMemoryBytes, maxMemoryBytes);
        return encode(TYPE_SNAPSHOT, out -> {
            out.writeUTF(snapshot.worldName());
            out.writeDouble(snapshot.cpuPercent());
            out.writeLong(snapshot.usedMemoryBytes());
            out.writeLong(snapshot.maxMemoryBytes());
        });
    }

    public static Request decodeRequest(byte[] payload) throws IOException {
        try (DataInputStream in = input(payload)) {
            requireVersion(in.readUnsignedByte());
            int type = in.readUnsignedByte();
            Request request = switch (type) {
                case TYPE_SUBSCRIBE -> new Subscribe();
                default -> throw new IOException("Unknown utility telemetry request type: " + type);
            };
            requireFullyConsumed(in);
            return request;
        }
    }

    public static Response decodeResponse(byte[] payload) throws IOException {
        try (DataInputStream in = input(payload)) {
            requireVersion(in.readUnsignedByte());
            int type = in.readUnsignedByte();
            Response response = switch (type) {
                case TYPE_SNAPSHOT -> new Snapshot(
                        in.readUTF(),
                        in.readDouble(),
                        in.readLong(),
                        in.readLong()
                );
                default -> throw new IOException("Unknown utility telemetry response type: " + type);
            };
            requireFullyConsumed(in);
            return response;
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid utility telemetry payload: " + exception.getMessage(), exception);
        }
    }

    private static byte[] encode(byte type, Encoder encoder) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeByte(VERSION);
                out.writeByte(type);
                encoder.write(out);
            }
            byte[] payload = bytes.toByteArray();
            if (payload.length < 2 || payload.length > MAX_MESSAGE_BYTES) {
                throw new IllegalStateException("Utility telemetry payload exceeded bounds");
            }
            return payload;
        } catch (IOException impossible) {
            throw new IllegalStateException("Unexpected in-memory telemetry encoding failure", impossible);
        }
    }

    private static DataInputStream input(byte[] payload) throws IOException {
        Objects.requireNonNull(payload, "payload");
        if (payload.length < 2 || payload.length > MAX_MESSAGE_BYTES) {
            throw new IOException("Invalid utility telemetry payload size");
        }
        return new DataInputStream(new ByteArrayInputStream(payload));
    }

    private static String validateWorldName(String worldName) {
        String value = Objects.requireNonNull(worldName, "worldName").strip();
        if (value.isEmpty() || value.length() > MAX_WORLD_NAME_LENGTH) {
            throw new IllegalArgumentException("Invalid world name length");
        }
        return value;
    }

    private static void requireVersion(int version) throws IOException {
        if (version != VERSION) throw new IOException("Unsupported utility telemetry version: " + version);
    }

    private static void requireFullyConsumed(DataInputStream in) throws IOException {
        if (in.available() != 0) throw new IOException("Unexpected trailing utility telemetry data");
    }

    @FunctionalInterface
    private interface Encoder {
        void write(DataOutputStream out) throws IOException;
    }
}
