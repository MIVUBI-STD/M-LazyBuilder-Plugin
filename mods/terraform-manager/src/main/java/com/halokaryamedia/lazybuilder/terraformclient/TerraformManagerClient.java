package com.halokaryamedia.lazybuilder.terraformclient;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client lifecycle owner for the standalone Terraform editor. */
public final class TerraformManagerClient implements ClientModInitializer {
    private static final Logger LOGGER=LoggerFactory.getLogger("LazyBuilder/Terraform");
    private static final TerraformEditorState STATE=new TerraformEditorState();
    private final TerraformClientNetworking networking=new TerraformClientNetworking();
    @Override public void onInitializeClient(){
        networking.register();TerraformInteractionController.register();TerraformHotkeys.register();
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->client.execute(()->{
            STATE.setEditorOpen(false);TerraformInteractionController.resetRuntime();
            if(client.currentScreen instanceof TerraformPaletteScreen)client.setScreen(null);
        }));
        LOGGER.info("Terraform Manager loaded; standalone Cliff, Ridge, Mountain editor ready.");
    }
    public static TerraformEditorState state(){return STATE;}
}
