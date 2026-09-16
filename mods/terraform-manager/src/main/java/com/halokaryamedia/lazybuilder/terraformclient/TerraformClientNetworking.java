package com.halokaryamedia.lazybuilder.terraformclient;

import com.halokaryamedia.lazybuilder.terraform.wire.TerraformWireProtocol;
import com.halokaryamedia.lazybuilder.terraformclient.net.TerraformPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import java.io.IOException;

final class TerraformClientNetworking {
    void register() {
        PayloadTypeRegistry.playC2S().register(TerraformPayload.ID,TerraformPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TerraformPayload.ID,TerraformPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(TerraformPayload.ID,(payload,context)->context.client().execute(()->accept(payload.bytes())));
    }
    static void send(TerraformWireProtocol.Request request) {
        try { ClientPlayNetworking.send(new TerraformPayload(TerraformWireProtocol.encodeRequest(request))); }
        catch(IOException exception){ notifyPlayer("Terraform request failed: "+exception.getMessage()); }
    }
    private void accept(byte[] bytes) {
        try {
            TerraformWireProtocol.Response response=TerraformWireProtocol.decodeResponse(bytes);
            if(response instanceof TerraformWireProtocol.Error e) notifyPlayer("Terraform: "+e.message());
            else if(response instanceof TerraformWireProtocol.Finished f) notifyPlayer("Terraform "+(f.undo()?"undo":"operation")+" complete: "+f.changedBlocks()+" blocks");
        } catch(IOException|RuntimeException exception){ notifyPlayer("Terraform response rejected: "+exception.getMessage()); }
    }
    private static void notifyPlayer(String text){MinecraftClient client=MinecraftClient.getInstance();if(client.player!=null)client.player.sendMessage(Text.literal(text),false);}
}
