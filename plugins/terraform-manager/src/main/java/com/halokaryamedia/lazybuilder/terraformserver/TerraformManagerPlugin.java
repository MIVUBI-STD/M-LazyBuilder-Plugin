package com.halokaryamedia.lazybuilder.terraformserver;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Paper lifecycle and world-write authority for standalone Terraform operations. */
public final class TerraformManagerPlugin extends JavaPlugin implements Listener {
    private TerraformApplyQueue queue;
    private PaperTerraformPayloadAdapter payloads;
    @Override public void onEnable() {
        queue = new TerraformApplyQueue(this); queue.start();
        payloads = new PaperTerraformPayloadAdapter(this, queue); payloads.start();
        getServer().getPluginManager().registerEvents(this,this);
        getLogger().info("Terraform Manager enabled: Cliff, Ridge, Mountain geometry authority ready.");
    }
    @EventHandler public void onQuit(PlayerQuitEvent event){if(queue!=null)queue.clearHistory(event.getPlayer().getUniqueId());}
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event){if(queue!=null)queue.clearHistory(event.getPlayer().getUniqueId());}
    @Override public void onDisable() {
        if (payloads != null) payloads.stop();
        if (queue != null) queue.stop();
    }
}
