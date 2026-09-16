package com.halokaryamedia.lazybuilder.terraformserver;

import com.halokaryamedia.lazybuilder.terraform.BoundedShapeField;
import com.halokaryamedia.lazybuilder.terraform.wire.TerraformWireProtocol;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import java.io.IOException;

final class PaperTerraformPayloadAdapter implements PluginMessageListener {
    private final TerraformManagerPlugin plugin;
    private final TerraformApplyQueue queue;
    PaperTerraformPayloadAdapter(TerraformManagerPlugin plugin, TerraformApplyQueue queue) { this.plugin=plugin; this.queue=queue; }
    void start() {
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, TerraformWireProtocol.CHANNEL, this);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, TerraformWireProtocol.CHANNEL);
    }
    void stop() { plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, TerraformWireProtocol.CHANNEL, this); plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, TerraformWireProtocol.CHANNEL); }

    @Override public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!TerraformWireProtocol.CHANNEL.equals(channel)) return;
        String id = "unknown";
        try {
            TerraformWireProtocol.Request request = TerraformWireProtocol.decodeRequest(message);
            if (request instanceof TerraformWireProtocol.Apply apply) {
                id = apply.operationId();
                if (!player.hasPermission("lazybuilder.terraform.use")) { send(player,new TerraformWireProtocol.Error(id,"permission denied")); return; }
                BoundedShapeField shape = TerraformShapeMapper.shape(apply);
                send(player,new TerraformWireProtocol.Accepted(id));
                String finalId=id; queue.submit(player,id,shape,(changed,undo)->send(player,new TerraformWireProtocol.Finished(finalId,changed,false)));
            } else if (request instanceof TerraformWireProtocol.Undo undo) {
                id=undo.operationId();
                if (!player.hasPermission("lazybuilder.terraform.use")) { send(player,new TerraformWireProtocol.Error(id,"permission denied")); return; }
                String finalId=id; if (!queue.submitUndo(player,id,(changed,isUndo)->send(player,new TerraformWireProtocol.Finished(finalId,changed,true)))) send(player,new TerraformWireProtocol.Error(id,"nothing to undo or operation still queued"));
            }
        } catch (IOException | RuntimeException exception) { send(player,new TerraformWireProtocol.Error(id,exception.getMessage())); }
    }
    private void send(Player player, TerraformWireProtocol.Response response) {
        if(!player.isOnline())return;
        try { player.sendPluginMessage(plugin, TerraformWireProtocol.CHANNEL, TerraformWireProtocol.encodeResponse(response)); }
        catch (IOException exception) { plugin.getLogger().warning("Failed to encode Terraform response: "+exception.getMessage()); }
    }
}
