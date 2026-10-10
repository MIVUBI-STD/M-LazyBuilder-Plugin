package com.halokaryamedia.lazybuilder.client.net;

import com.halokaryamedia.lazybuilder.world.control.WorldControlWireProtocol;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.Arrays;
import java.util.Objects;

public record WorldPayload(byte[] bytes) implements CustomPayload {
    public static final Id<WorldPayload> ID = new Id<>(Identifier.of("lazybuilder", "world"));
    public static final PacketCodec<RegistryByteBuf, WorldPayload> CODEC = PacketCodec.ofStatic(
            (buf, payload) -> buf.writeBytes(payload.bytes),
            buf -> {
                int length = buf.readableBytes();
                requireValidPayloadLength(length);
                byte[] bytes = new byte[length];
                buf.readBytes(bytes);
                return new WorldPayload(bytes);
            }
    );

    public WorldPayload {
        Objects.requireNonNull(bytes, "bytes");
        requireValidPayloadLength(bytes.length);
        bytes = Arrays.copyOf(bytes, bytes.length);
    }

    /** Enforced before decoder allocation and before defensive copying. */
    static void requireValidPayloadLength(int length) {
        if (length < 1 || length > WorldControlWireProtocol.MAX_MESSAGE_BYTES) {
            throw new IllegalArgumentException("Invalid LazyBuilder world-control payload size");
        }
    }

    @Override public byte[] bytes() { return Arrays.copyOf(bytes, bytes.length); }
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
