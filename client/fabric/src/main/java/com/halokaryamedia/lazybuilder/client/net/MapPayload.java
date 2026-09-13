package com.halokaryamedia.lazybuilder.client.net;

import com.halokaryamedia.lazybuilder.world.map.MapActionWireProtocol;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.Arrays;
import java.util.Objects;

public record MapPayload(byte[] bytes) implements CustomPayload {
    public static final Id<MapPayload> ID = new Id<>(Identifier.of("lazybuilder", "map"));
    public static final PacketCodec<RegistryByteBuf, MapPayload> CODEC = PacketCodec.ofStatic(
            (buf, payload) -> buf.writeBytes(payload.bytes),
            buf -> {
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                return new MapPayload(bytes);
            }
    );

    public MapPayload {
        bytes = Arrays.copyOf(Objects.requireNonNull(bytes, "bytes"), bytes.length);
        if (bytes.length < 1 || bytes.length > MapActionWireProtocol.MAX_MESSAGE_BYTES) {
            throw new IllegalArgumentException("Invalid LazyBuilder map payload size");
        }
    }

    @Override public byte[] bytes() { return Arrays.copyOf(bytes, bytes.length); }
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
