package com.halokaryamedia.lazybuilder.builder.wire;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Fragmentation envelope for Bukkit/Paper plugin messaging.
 *
 * <p>Raw protocol messages below the safe transport threshold pass through unchanged.
 * Larger messages are split into bounded fragments and reassembled before wire decode.</p>
 */
public final class BuilderExtensionTransport {
    private static final int MAGIC = 0x4c424658; // LBFX
    private static final int VERSION = 1;
    private static final int MAX_CONCURRENT_TRANSFERS = 16;
    private static final long TRANSFER_TIMEOUT_MILLIS = 30_000L;

    /** Conservative ceiling below Bukkit Messenger.MAX_MESSAGE_SIZE (32766). */
    public static final int MAX_PLUGIN_MESSAGE_BYTES = 32_000;
    public static final int MAX_FRAGMENT_PAYLOAD_BYTES = 31_000;

    private BuilderExtensionTransport() {}

    public static List<byte[]> fragment(byte[] wireMessage) throws IOException {
        if (wireMessage == null
                || wireMessage.length < 2
                || wireMessage.length > BuilderExtensionWireProtocol.MAX_MESSAGE_BYTES) {
            throw new IOException("invalid Builder wire message size");
        }
        if (wireMessage.length <= MAX_PLUGIN_MESSAGE_BYTES) {
            return List.of(Arrays.copyOf(wireMessage, wireMessage.length));
        }

        int count = Math.toIntExact(
                (wireMessage.length + (long) MAX_FRAGMENT_PAYLOAD_BYTES - 1L)
                        / MAX_FRAGMENT_PAYLOAD_BYTES);
        if (count <= 1 || count > 0xffff) {
            throw new IOException("invalid Builder fragment count");
        }

        UUID transfer = UUID.randomUUID();
        List<byte[]> fragments = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int offset = index * MAX_FRAGMENT_PAYLOAD_BYTES;
            int length = Math.min(
                    MAX_FRAGMENT_PAYLOAD_BYTES,
                    wireMessage.length - offset);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(length + 40);
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(MAGIC);
                out.writeByte(VERSION);
                out.writeLong(transfer.getMostSignificantBits());
                out.writeLong(transfer.getLeastSignificantBits());
                out.writeInt(wireMessage.length);
                out.writeShort(index);
                out.writeShort(count);
                out.writeShort(length);
                out.write(wireMessage, offset, length);
            }
            byte[] fragment = bytes.toByteArray();
            if (fragment.length > MAX_PLUGIN_MESSAGE_BYTES) {
                throw new IOException("Builder transport fragment exceeds safe plugin-message limit");
            }
            fragments.add(fragment);
        }
        return List.copyOf(fragments);
    }

    public static boolean isFragment(byte[] message) {
        if (message == null || message.length < 4) return false;
        int magic = ((message[0] & 0xff) << 24)
                | ((message[1] & 0xff) << 16)
                | ((message[2] & 0xff) << 8)
                | (message[3] & 0xff);
        return magic == MAGIC;
    }

    public static final class Reassembler {
        private final Map<UUID, Pending> pending = new HashMap<>();

        public synchronized Optional<byte[]> accept(byte[] message) throws IOException {
            if (message == null
                    || message.length < 2
                    || message.length > MAX_PLUGIN_MESSAGE_BYTES) {
                throw new IOException("invalid Builder plugin-message size");
            }
            if (!isFragment(message)) {
                return Optional.of(Arrays.copyOf(message, message.length));
            }

            long now = System.currentTimeMillis();
            prune(now);

            Fragment fragment = decodeFragment(message);
            Pending transfer = pending.get(fragment.transferId());
            if (transfer == null) {
                if (pending.size() >= MAX_CONCURRENT_TRANSFERS) {
                    throw new IOException("too many concurrent Builder fragmented transfers");
                }
                transfer = new Pending(
                        fragment.totalLength(),
                        fragment.fragmentCount(),
                        now);
                pending.put(fragment.transferId(), transfer);
            } else if (transfer.totalLength != fragment.totalLength()
                    || transfer.parts.length != fragment.fragmentCount()) {
                pending.remove(fragment.transferId());
                throw new IOException("Builder fragment metadata changed mid-transfer");
            }

            transfer.add(fragment.fragmentIndex(), fragment.payload());
            if (!transfer.complete()) return Optional.empty();

            pending.remove(fragment.transferId());
            return Optional.of(transfer.assemble());
        }

        public synchronized void clear() {
            pending.clear();
        }

        private void prune(long now) {
            pending.entrySet().removeIf(entry ->
                    now - entry.getValue().createdAtMillis > TRANSFER_TIMEOUT_MILLIS);
        }
    }

    private static Fragment decodeFragment(byte[] message) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new ByteArrayInputStream(message))) {
            if (in.readInt() != MAGIC) throw new IOException("invalid fragment magic");
            if (in.readUnsignedByte() != VERSION) {
                throw new IOException("unsupported Builder fragment version");
            }
            UUID transfer = new UUID(in.readLong(), in.readLong());
            int totalLength = in.readInt();
            int index = in.readUnsignedShort();
            int count = in.readUnsignedShort();
            int length = in.readUnsignedShort();

            if (totalLength < 2
                    || totalLength > BuilderExtensionWireProtocol.MAX_MESSAGE_BYTES) {
                throw new IOException("invalid fragmented Builder total length");
            }
            if (count <= 1 || index >= count) {
                throw new IOException("invalid Builder fragment index/count");
            }
            if (length <= 0
                    || length > MAX_FRAGMENT_PAYLOAD_BYTES
                    || length != in.available()) {
                throw new IOException("invalid Builder fragment payload length");
            }
            byte[] payload = in.readNBytes(length);
            if (payload.length != length) throw new EOFException();
            return new Fragment(transfer, totalLength, index, count, payload);
        }
    }

    private record Fragment(
            UUID transferId,
            int totalLength,
            int fragmentIndex,
            int fragmentCount,
            byte[] payload
    ) {}

    private static final class Pending {
        private final int totalLength;
        private final byte[][] parts;
        private final long createdAtMillis;
        private int received;
        private int bytes;

        private Pending(int totalLength, int count, long createdAtMillis) {
            this.totalLength = totalLength;
            this.parts = new byte[count][];
            this.createdAtMillis = createdAtMillis;
        }

        private void add(int index, byte[] payload) throws IOException {
            byte[] previous = parts[index];
            if (previous != null) {
                if (!Arrays.equals(previous, payload)) {
                    throw new IOException("conflicting duplicate Builder fragment");
                }
                return;
            }
            parts[index] = Arrays.copyOf(payload, payload.length);
            received++;
            bytes = Math.addExact(bytes, payload.length);
            if (bytes > totalLength) {
                throw new IOException("Builder fragmented transfer exceeds declared length");
            }
        }

        private boolean complete() {
            return received == parts.length;
        }

        private byte[] assemble() throws IOException {
            if (!complete() || bytes != totalLength) {
                throw new IOException("incomplete Builder fragmented transfer");
            }
            byte[] result = new byte[totalLength];
            int offset = 0;
            for (byte[] part : parts) {
                if (part == null) throw new IOException("missing Builder fragment");
                System.arraycopy(part, 0, result, offset, part.length);
                offset += part.length;
            }
            return result;
        }
    }
}
