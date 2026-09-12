# LazyBuilder client modules

`client/fabric` contains the Minecraft/Fabric in-game frontend for LazyBuilder.

The Fabric client is a transport/UI surface only. It does not own World-Manager lifecycle, registry, filesystem, conversion, backup, import/export, or deletion logic. Those remain canonical server-side World-Manager responsibilities.

Its Paper plugin-message channels are intentionally separate from the Tauri desktop loopback HTTP bridge because they serve a different client runtime, but both transports must delegate to the same World-Manager application services.

Additional client surfaces must live in their own child directory rather than sharing one source tree.
