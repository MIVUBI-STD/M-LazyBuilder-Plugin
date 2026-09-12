package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.client.xaero.XaeroAvailability;
import com.halokaryamedia.lazybuilder.client.xaero.XaeroMapActions;
import net.fabricmc.api.ClientModInitializer;

/** Fabric client entrypoint. Keeps initialization event-driven and side-local. */
public final class LazyBuilderClient implements ClientModInitializer {
    private static final ClientWorldController WORLDS = new ClientWorldController();
    private static final ClientTransferController TRANSFERS = new ClientTransferController();
    private static final ClientMapController MAPS = new ClientMapController(TRANSFERS::downloadExport);

    @Override
    public void onInitializeClient() {
        new LazyBuilderClientNetworking(WORLDS, MAPS, TRANSFERS).register();
        WorldManagerClientUi.register(WORLDS, TRANSFERS);
        XaeroAvailability.logStatus();
        if (XaeroAvailability.isAvailable()) {
            XaeroMapActions.register();
        }
    }

    public static ClientWorldController worlds() { return WORLDS; }
    public static ClientMapController maps() { return MAPS; }
    public static ClientTransferController transfers() { return TRANSFERS; }
}
