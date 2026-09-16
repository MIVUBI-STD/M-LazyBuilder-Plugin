package com.halokaryamedia.lazybuilder.terraformclient;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client bootstrap. Input/render/network adapters are registered here as the editor is completed. */
public final class TerraformManagerClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("LazyBuilder/Terraform");
    private static final TerraformEditorState STATE = new TerraformEditorState();

    @Override
    public void onInitializeClient() {
        LOGGER.info("Terraform Manager loaded; standalone geometry tools: Cliff, Ridge, Mountain");
    }

    public static TerraformEditorState state() { return STATE; }
}
