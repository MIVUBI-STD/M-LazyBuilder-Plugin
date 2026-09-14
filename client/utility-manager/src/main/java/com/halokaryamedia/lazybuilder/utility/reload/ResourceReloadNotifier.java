package com.halokaryamedia.lazybuilder.utility.reload;

import com.halokaryamedia.lazybuilder.utility.notification.UtilityNotifications;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

/**
 * Reports user-initiated client resource reloads through the shared Utility
 * Manager notification surface. Startup reloads are ignored.
 */
public final class ResourceReloadNotifier implements SimpleSynchronousResourceReloadListener {
    private static final Identifier ID = Identifier.of("lazybuilder_utility_manager", "reload_notice");
    private static boolean clientStarted;

    private ResourceReloadNotifier() {
    }

    public static void register() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
                .registerReloadListener(new ResourceReloadNotifier());
    }

    public static void markClientStarted() {
        clientStarted = true;
    }

    @Override
    public Identifier getFabricId() {
        return ID;
    }

    @Override
    public void reload(ResourceManager manager) {
        if (!clientStarted) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        client.execute(() -> UtilityNotifications.show(
                "Resources Reloaded",
                "Client resources are ready."
        ));
    }
}
