package com.halokaryamedia.lazybuilder.client.net;

import com.halokaryamedia.lazybuilder.world.transfer.TransferWireProtocol;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.Arrays;
import java.util.Objects;

public record TransferPayload(byte[] bytes) implements CustomPayload {
    public static final Id<TransferPayload> ID = new Id<>(Identifier.of("lazybuilder", "transfer"));
    public static final PacketCodec<RegistryByteBuf, TransferPayload> CODEC = PacketCodec.ofStatic(
            (buf, payload) -> buf.writeBytes(payload.bytes),
            buf -> {
                int length = buf.readableBytes();
                requireValidPayloadLength(length);
                byte[] bytes = new byte[length];
                buf.readBytes(bytes);
                return new TransferPayload(bytes);
            }
    );

    public TransferPayload {
        Objects.requireNonNull(bytes, "bytes");
        requireValidPayloadLength(bytes.length);
        bytes = Arrays.copyOf(bytes, bytes.length);
    }

    /** Enforced before decoder allocation and before defensive copying. */
    static void requireValidPayloadLength(int length) {
        if (length < 1 || length > TransferWireProtocol.MAX_MESSAGE_BYTES) {
            throw new IllegalArgumentException("Invalid LazyBuilder transfer payload size");
        }
    }

    @Override public byte[] bytes() { return Arrays.copyOf(bytes, bytes.length); }
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
