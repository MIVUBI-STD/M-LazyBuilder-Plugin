# LazyBuilder Tauri Desktop Application

This is the canonical replacement for the transitional WPF desktop implementation.

## Stack

```text
Tauri 2
Svelte 5
Vite
TypeScript
Tailwind CSS 4
Rust
```

## Ownership

```text
src/
→ presentation + application state only

src/app/bridge/
→ typed Tauri command boundary

src-tauri/src/commands/
→ thin command wrappers

src-tauri/src/engine/
→ reusable desktop runtime/domain logic
```

Minecraft world authority stays inside `modules/world-manager`. The Rust desktop runtime may call its authenticated loopback control bridge but must not duplicate world lifecycle or filesystem ownership.
