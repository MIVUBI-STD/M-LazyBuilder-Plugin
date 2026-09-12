# EngineData

`EngineData` is the canonical implementation boundary for the LazyBuilder desktop product.

```text
Frontend/RustApp
→ Tauri 2 desktop shell
→ Svelte 5 + Vite + TypeScript UI
→ Rust Tauri command/runtime backend

Backend/Minecraft
→ repository-owned Minecraft runtime modules remain in their existing canonical source locations
→ Paper: modules/world-manager + modules/utilities-manager
→ Fabric: client/fabric
```

The desktop frontend must never become a second owner for Paper, plugin, or world business logic. Svelte owns presentation/application state. Rust owns desktop-native process/filesystem/runtime work. World-Manager remains the canonical server-side world authority.
