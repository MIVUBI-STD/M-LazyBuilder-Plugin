package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.client.xaero.XaeroAvailability;
import net.fabricmc.api.ClientModInitializer;

/** Fabric client entrypoint. Keeps initialization event-driven and side-local. */
public final class LazyBuilderClient implements ClientModInitializer {
    private static final ClientMapController MAPS = new ClientMapController();

    @Override
    public void onInitializeClient() {
        new LazyBuilderClientNetworking(MAPS).register();
        XaeroAvailability.logStatus();
    }

    public static ClientMapController maps() {
        return MAPS;
    }
}
