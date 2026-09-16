package com.halokaryamedia.lazybuilder.terraformclient.net;

import com.halokaryamedia.lazybuilder.terraform.wire.TerraformWireProtocol;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.Arrays;
import java.util.Objects;

public record TerraformPayload(byte[] bytes) implements CustomPayload {
    public static final Id<TerraformPayload> ID=new Id<>(Identifier.of("lazybuilder","terraform"));
    public static final PacketCodec<RegistryByteBuf,TerraformPayload> CODEC=PacketCodec.ofStatic(
            (buf,payload)->buf.writeBytes(payload.bytes), buf->{byte[] bytes=new byte[buf.readableBytes()];buf.readBytes(bytes);return new TerraformPayload(bytes);});
    public TerraformPayload { bytes=Arrays.copyOf(Objects.requireNonNull(bytes),bytes.length); if(bytes.length<1||bytes.length>TerraformWireProtocol.MAX_MESSAGE_BYTES) throw new IllegalArgumentException("invalid Terraform payload size"); }
    @Override public byte[] bytes(){return Arrays.copyOf(bytes,bytes.length);} @Override public Id<? extends CustomPayload> getId(){return ID;}
}
