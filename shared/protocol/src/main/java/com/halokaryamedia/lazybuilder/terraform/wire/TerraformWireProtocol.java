package com.halokaryamedia.lazybuilder.terraform.wire;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Bounded, transport-neutral Paper↔Fabric Terraform V1 contract. */
public final class TerraformWireProtocol {
    public static final int VERSION = 1;
    public static final int MAX_MESSAGE_BYTES = 48 * 1024;
    public static final int MAX_PATH_POINTS = 256;
    public static final String CHANNEL = "lazybuilder:terraform";
    private TerraformWireProtocol() {}

    public enum Tool { CLIFF, RIDGE, MOUNTAIN }
    public enum Variation { SOFT, NATURAL, DRAMATIC }
    public sealed interface Request permits Apply, Undo {}
    public sealed interface Response permits Accepted, Finished, Error {}
    public record Point(double x, double y, double z) {}
    public record Apply(String operationId, Tool tool, Variation variation, double size, double height,
                        long seed, double frontX, double frontZ, List<Point> points) implements Request {
        public Apply {
            requireId(operationId); Objects.requireNonNull(tool); Objects.requireNonNull(variation);
            if (!Double.isFinite(size) || size < 4 || size > 96) throw new IllegalArgumentException("size out of range");
            if (!Double.isFinite(height) || height < 4 || height > 192) throw new IllegalArgumentException("height out of range");
            if (!Double.isFinite(frontX) || !Double.isFinite(frontZ) || Math.hypot(frontX, frontZ) < 1e-6) throw new IllegalArgumentException("front invalid");
            points = List.copyOf(points);
            int min = tool == Tool.MOUNTAIN ? 1 : 2;
            if (points.size() < min || points.size() > MAX_PATH_POINTS) throw new IllegalArgumentException("path point count invalid");
            for (Point p : points) if (!finite(p.x) || !finite(p.y) || !finite(p.z)) throw new IllegalArgumentException("point invalid");
        }
    }
    public record Undo(String operationId) implements Request { public Undo { requireId(operationId); } }
    public record Accepted(String operationId) implements Response { public Accepted { requireId(operationId); } }
    public record Finished(String operationId, int changedBlocks, boolean undo) implements Response { public Finished { requireId(operationId); } }
    public record Error(String operationId, String message) implements Response {
        public Error { operationId = operationId == null ? "unknown" : operationId; message = safeText(message, 240); }
    }

    public static byte[] encodeRequest(Request request) throws IOException { return encode(out -> writeRequest(out, request)); }
    public static byte[] encodeResponse(Response response) throws IOException { return encode(out -> writeResponse(out, response)); }

    public static Request decodeRequest(byte[] bytes) throws IOException {
        DataInputStream in = input(bytes); requireVersion(in.readUnsignedByte());
        return switch (in.readUnsignedByte()) {
            case 1 -> {
                String id = readString(in, 80);
                Tool tool = enumValue(Tool.values(), in.readUnsignedByte(), "tool");
                Variation variation = enumValue(Variation.values(), in.readUnsignedByte(), "variation");
                double size = in.readDouble(), height = in.readDouble(); long seed = in.readLong();
                double frontX = in.readDouble(), frontZ = in.readDouble(); int count = in.readUnsignedShort();
                if (count > MAX_PATH_POINTS) throw new IOException("too many path points");
                List<Point> points = new ArrayList<>(count);
                for (int i = 0; i < count; i++) points.add(new Point(in.readDouble(), in.readDouble(), in.readDouble()));
                yield new Apply(id, tool, variation, size, height, seed, frontX, frontZ, points);
            }
            case 2 -> new Undo(readString(in, 80));
            default -> throw new IOException("unknown terraform request");
        };
    }

    public static Response decodeResponse(byte[] bytes) throws IOException {
        DataInputStream in = input(bytes); requireVersion(in.readUnsignedByte());
        return switch (in.readUnsignedByte()) {
            case 1 -> new Accepted(readString(in, 80));
            case 2 -> new Finished(readString(in, 80), in.readInt(), in.readBoolean());
            case 3 -> new Error(readString(in, 80), readString(in, 240));
            default -> throw new IOException("unknown terraform response");
        };
    }

    private static void writeRequest(DataOutputStream out, Request request) throws IOException {
        out.writeByte(VERSION);
        if (request instanceof Apply a) {
            out.writeByte(1); writeString(out, a.operationId); out.writeByte(a.tool.ordinal()); out.writeByte(a.variation.ordinal());
            out.writeDouble(a.size); out.writeDouble(a.height); out.writeLong(a.seed); out.writeDouble(a.frontX); out.writeDouble(a.frontZ);
            out.writeShort(a.points.size()); for (Point p : a.points) { out.writeDouble(p.x); out.writeDouble(p.y); out.writeDouble(p.z); }
        } else if (request instanceof Undo u) { out.writeByte(2); writeString(out, u.operationId); }
        else throw new IOException("unsupported request");
    }

    private static void writeResponse(DataOutputStream out, Response response) throws IOException {
        out.writeByte(VERSION);
        if (response instanceof Accepted a) { out.writeByte(1); writeString(out, a.operationId); }
        else if (response instanceof Finished f) { out.writeByte(2); writeString(out, f.operationId); out.writeInt(f.changedBlocks); out.writeBoolean(f.undo); }
        else if (response instanceof Error e) { out.writeByte(3); writeString(out, e.operationId); writeString(out, e.message); }
        else throw new IOException("unsupported response");
    }

    private interface Writer { void write(DataOutputStream out) throws IOException; }
    private static byte[] encode(Writer writer) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); try (DataOutputStream out = new DataOutputStream(bytes)) { writer.write(out); }
        byte[] encoded = bytes.toByteArray(); if (encoded.length > MAX_MESSAGE_BYTES) throw new IOException("terraform payload too large"); return encoded;
    }
    private static DataInputStream input(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 2 || bytes.length > MAX_MESSAGE_BYTES) throw new IOException("invalid terraform payload size");
        return new DataInputStream(new ByteArrayInputStream(bytes));
    }
    private static void requireVersion(int version) throws IOException { if (version != VERSION) throw new IOException("unsupported terraform protocol version " + version); }
    private static void writeString(DataOutputStream out, String value) throws IOException { byte[] b = safeText(value, 240).getBytes(StandardCharsets.UTF_8); out.writeShort(b.length); out.write(b); }
    private static String readString(DataInputStream in, int maxChars) throws IOException { int n = in.readUnsignedShort(); if (n > MAX_MESSAGE_BYTES) throw new IOException("string too large"); byte[] b = in.readNBytes(n); if (b.length != n) throw new EOFException(); return safeText(new String(b, StandardCharsets.UTF_8), maxChars); }
    private static String safeText(String value, int max) { String text = Objects.requireNonNullElse(value, ""); return text.length() <= max ? text : text.substring(0, max); }
    private static void requireId(String id) { if (id == null || id.isBlank() || id.length() > 80) throw new IllegalArgumentException("operation id invalid"); }
    private static boolean finite(double d) { return Double.isFinite(d); }
    private static <T> T enumValue(T[] values, int ordinal, String label) throws IOException { if (ordinal < 0 || ordinal >= values.length) throw new IOException("invalid " + label); return values[ordinal]; }
}
