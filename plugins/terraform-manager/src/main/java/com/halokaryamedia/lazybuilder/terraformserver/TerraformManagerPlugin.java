package com.halokaryamedia.lazybuilder.terraformserver;

import org.bukkit.plugin.java.JavaPlugin;

/** Paper lifecycle and world-write authority for standalone Terraform operations. */
public final class TerraformManagerPlugin extends JavaPlugin {
    private TerraformApplyQueue queue;
    private PaperTerraformPayloadAdapter payloads;
    @Override public void onEnable() {
        queue = new TerraformApplyQueue(this); queue.start();
        payloads = new PaperTerraformPayloadAdapter(this, queue); payloads.start();
        getLogger().info("Terraform Manager enabled: Cliff, Ridge, Mountain geometry authority ready.");
    }
    @Override public void onDisable() {
        if (payloads != null) payloads.stop();
        if (queue != null) queue.stop();
    }
}
