package com.halokaryamedia.lazybuilder.terraformserver;

import org.bukkit.plugin.java.JavaPlugin;

/** Paper authority bootstrap for standalone Terraform operations. */
public final class TerraformManagerPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("Terraform Manager enabled; geometry authority ready for protocol/execution adapters");
    }
}
