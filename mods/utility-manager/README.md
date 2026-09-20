# LazyBuilder Utility Manager

LazyBuilder Utility Manager is the passive, non-building Fabric client convenience layer for Minecraft Java 1.21.4.

## Boundary

Approved scope includes window/client behavior, loading and reload UX, chat convenience, signing presentation/compatibility, accessibility helpers, reconnect behavior, preference persistence, notifications, screenshot convenience, creative-inventory convenience, compact builder-facing debug presentation, and small contextual clipboard actions.

It must not own building/editing tools, palettes, measurement, placement helpers, camera build tools, renderer internals, performance engines, or generic clipboard/history systems.

## Product rules

- one Utility Manager = one Fabric mod = one mod id = one output JAR;
- passive client utility features must not be split into separate LazyBuilder mods when they fit the Utility Manager boundary;
- third-party utility JARs are migration references only and must not be shaded, nested, unpacked, or copied into the Utility Manager artifact;
- no mandatory default keybinds;
- Minecraft `GameOptions` and `KeyBinding` objects remain the authoritative backend and persistence owners; LazyBuilder owns the unified user-facing settings presentation;
- no dependency on Map Manager implementation packages;
- no pollers/watchers/background workers unless an active feature proves they are required;
- preferences exist only for implemented behavior, not for speculative future features.

## Unified settings shell

Utility Manager owns the permanent LazyBuilder user-facing Settings shell. Video and
Controls bind directly to Minecraft's existing `GameOptions` / `KeyBinding` state instead
of opening separate vanilla settings screens. Interface preferences remain Utility-owned,
while Performance preferences are exposed through a narrow JDK-only Fabric ObjectShare
contract so Performance Manager keeps runtime/config ownership without a package dependency.

The shell uses one visual language across Video, Controls, Interface, and Tools: sectioned
single-column rows, contextual help, switches, sliders, dropdowns, key capture, responsive
scrolling, reset confirmation, and a fixed footer.

### Video settings information architecture

The Video surface follows a game-style hierarchy without duplicating concepts:

- **Display** owns window/frame-pacing and visibility controls such as fullscreen, V-Sync, frame-rate limit, and brightness.
- **Quality** owns the Graphics Preset plus individual visual-detail controls. Graphics Preset coordinates visual detail with the matching LazyBuilder performance policy; Graphics Mode is only Minecraft's Fast/Fancy/Fabulous rendering mode and is not a second preset system.
- **View** owns render distance, simulation distance, entity distance, and field of view.
- **Performance** owns LazyBuilder efficiency controls and background FPS limits. These controls stay independent from the visual-quality preset so choosing Low/Medium/High never silently changes optimization policy.
- **Interface** owns GUI Scale because interface sizing is not a video-quality decision.
- **Visual** owns Resource Pack and Shader selection. The row shows the current selection and opens the authoritative manager when activated. Resource Packs use Minecraft's own pack manager; Shaders use Iris when available. Visual choices never become part of the Graphics Preset.


Graphics presets are deliberately limited to Low, Medium, and High. Custom is a derived state, not a selectable preset: if any preset-controlled visual or performance preference no longer matches a known profile, the UI reports Custom. View distance, display preferences, camera settings, and background FPS limits remain independent. Presets write visual settings through Minecraft GameOptions and performance policy through the existing Performance Manager ObjectShare contract; no duplicate graphics or performance configuration file is introduced.

The stable preset policy is conservative: High targets high detail with Minecraft's Fancy renderer, while Fabulous remains an explicit manual Graphics Mode choice. This avoids making a convenience preset opt the user into the most compatibility-sensitive renderer path.

### Visual management

Visual is intentionally a small management surface rather than another graphics-tuning page.

- Resource Pack shows Default, the active pack name, or the count of active packs. Opening it delegates to Minecraft's native Resource Pack screen so ordering, compatibility, file watching, and resource reload remain Minecraft-owned.
- Shader shows Off, the active shader name when available, or Unavailable when no compatible shader renderer is installed. Opening it delegates to Iris through its public GUI API when Iris is present.
- Shader-specific profiles and individual shader options stay inside the shader manager. LazyBuilder does not clone or persist those settings.
- Resource Packs and Shaders do not change Graphics Preset, View settings, or Performance preferences automatically.
- Technical renderer/compatibility state remains internal unless the user needs an actionable explanation.

Preset-owned performance behavior is intentionally small and understandable:
- Low enables hidden-object skipping and keeps rendering/memory optimizations enabled.
- Medium keeps rendering/memory optimizations enabled and leaves hidden-object skipping off.
- High keeps rendering/memory optimizations enabled and leaves hidden-object skipping off.
- Background/minimized FPS limits remain independent because they describe inactive-window behavior, not foreground graphics quality.
- Internal caches, upload pacing, rebuild backpressure, allocator behavior, GPU residency, and other engine-level optimizations remain automatic and are not exposed as user-facing preset controls.


## Product direction

Utility Manager is the single client-side owner for passive LazyBuilder utilities. Features that previously required separate small client mods should be consolidated here only when they fit this boundary and can be maintained as independent internal modules.

The target is one Fabric utility JAR with modular source ownership, not one monolithic implementation class.

The chat surface follows the same rule: keep Minecraft's familiar chat interaction, but centralize message presentation, history, draft handling, search/context actions, signing compatibility, accessibility-adjacent chat behavior, and notification routing under Utility Manager instead of stacking multiple overlapping chat mods.

### Single-mod consolidation target

The following external utilities are migration sources, not permanent runtime dependencies:

- Chat Patches;
- Chat Signing Hider;
- Narrus Yeetus;
- No Chat Reports.

The approved end state is:

```text
lazybuilder-utility-manager.jar
```

with internal source remaining modular under domains such as:

```text
utility/
├── chat/
│   ├── presentation/
│   ├── history/
│   ├── search/
│   ├── context/
│   ├── routing/
│   └── signing/
├── accessibility/
├── connection/
├── notification/
├── inventory/
├── debug/
├── screenshot/
└── window/
```

A useful third-party utility feature should be audited, independently implemented when retained, verified, and then owned by Utility Manager. Adding another permanent utility JAR is not the default solution.

## Implemented Utility behavior

Current client-side behavior remains deliberately small and vanilla-shaped:

- Extended Chat History: enabled by default and retains more vanilla chat lines/history without replacing the chat screen;
- Keep Chat Draft: enabled by default and restores an unsent draft while the current multiplayer connection/session remains active; disconnect clears the draft so text is not carried into another server context;
- Chat Search: enabled by default and adds a compact `Ctrl+F` overlay to the existing vanilla chat screen. Search is session-only, indexes at most the existing bounded chat history, shows the selected match as a small preview, uses `Enter` / `Shift+Enter` for navigation, and does not persist chat to disk;
- Chat Timestamps: enabled by default and prefixes visible chat lines with a low-contrast local `HH:mm` timestamp while preserving the original text component styling/click behavior;
- Compact Duplicate Messages: consecutive recognized gameplay/warning lines such as repeated join/leave, game-mode feedback, advancement/challenge feedback, and compact warnings are replaced in place with a single `×N` line within an eight-second window. Player chat and unknown system text are never collapsed. Rewriting is suspended while the user is scrolled up in chat;
- Signing Indicator Suppression: enabled by default and removes only the visible signing indicator while leaving chat signatures, packets, reporting data, and protocol behavior untouched;
- Report Button Suppression: enabled by default and hides the report button in the vanilla Social Interactions player list without mutating reportability data, message signatures, packets, or report protocol state;
- Narrator Suppression: enabled by default and disables narrator mode and narrator hotkey through Minecraft options while retaining Minecraft accessibility infrastructure;
- Reconnect Button: enabled by default and adds one action to the existing vanilla disconnect layout when a previous multiplayer target is known;
- Copy Connection Details: contextual action on the disconnect screen for copying the known server target and disconnect reason;
- Borderless Window: opt-in and applied once at client startup, using the monitor that contains most of the Minecraft window; exclusive fullscreen is left alone; changing this preference takes effect on the next client start rather than through a background window watcher;
- Shared Notifications: Utility features use Minecraft's native system-toast surface instead of creating separate HUD or popup systems;
- Resource Reload Notice: startup resource loading stays silent, while later client-resource reloads report completion through the shared notification surface;
- Contextual Screenshot Names: opt-in and keeps the vanilla F2 capture path while adding a safe multiplayer/singleplayer context prefix to automatically named screenshots;
- Instant Creative Search: enabled by default; while the vanilla Creative inventory is open, typing a valid character switches to the vanilla Search Items tab, focuses its existing search field, and lets vanilla process the original character and subsequent query. Ctrl/Alt/Super-modified input and another focused UI element are left untouched;
- Compact Debug: enabled by default and replaces the vanilla F3 information wall with a small Minecraft-native builder HUD. Coordinate is the first top-left block with explicit `X`, `Y`, and `Z`; client FPS/CPU/GPU/RAM and world Facing/Biome/Time remain on the left; server world/telemetry presentation stays on the right. Unsupported metrics are shown as unavailable rather than estimated.

Reconnect state is session-only. Utility Manager does not persist the last server address to disk.

Borderless Window changes only window presentation. Focus-based FPS/resource throttling is explicitly owned by Performance Manager and must not be implemented here.

Screenshot naming does not depend on Map Manager and does not create a replacement screenshot system. Explicit filenames supplied by Minecraft or another mod are left unchanged.

Instant Creative Search does not replace the creative inventory or its search implementation. It only enters the existing vanilla search path earlier, and it adds no keybind or background tick loop.

Compact Debug observes a bounded metric set only. Client process CPU is cached at a low frequency, RAM uses the JVM runtime, and GPU/server machine metrics remain unavailable until a truthful supported source exists. The HUD does not add a performance optimizer, graphics controller, profiler history, worker thread, or polling loop.

Coordinate copy reuses the familiar debug chord `F3+C` while Compact Debug is active. It copies only the raw integer triplet (`X Y Z`) and confirms through the shared Utility toast surface.

Clipboard helpers are contextual actions only. World/project copy actions belong in the Map Manager UI that owns those values; block, structure, NBT, and other build-data clipboard behavior remains outside Utility Manager.

## Chat architecture direction

Chat must stop acting as a catch-all developer console. Utility Manager should classify and route messages before presentation so player communication remains readable while developer-only diagnostics stay in logs or launcher/server console surfaces.

The stable presentation categories are intentionally small:

- `CHAT`: player-to-player communication;
- `GAME`: normal Minecraft gameplay events such as join, advancement, and game-mode feedback;
- `SYSTEM`: concise LazyBuilder/server information relevant to the player;
- `WARNING`: actionable conflict/degraded behavior;
- `ERROR`: actionable failures such as invalid commands or failed operations.

Routing policy:

- player-relevant information may appear in chat;
- developer-only detail belongs in logs/console;
- information important to both should use a compact in-game message plus detailed console/log output;
- repeated recognized `GAME` and `WARNING` presentation lines may be collapsed conservatively when they are consecutive;
- normal player chat must not be deduplicated by default;
- unknown system text must not be collapsed merely because its rendered text happens to repeat;
- internal translation keys, stack traces, packet/signing state, and implementation identifiers must not be exposed in normal chat unless explicitly requested for diagnostics.

Signing presentation and signing/reporting protocol behavior remain separate internal concerns. Presentation-only features may hide indicators or report controls; packet/signature/reporting behavior must live behind a dedicated `chat/signing` boundary and requires independent protocol verification before replacing No Chat Reports.

The migration target is to retire overlapping external chat/narrator helper mods only after equivalent retained behavior is implemented and verified inside Utility Manager. Do not bundle third-party JARs inside Utility Manager as a shortcut.

## Maintenance notes

Extended Chat History intentionally stays a minimal vanilla patch rather than replacing ChatHud. Its three `@ModifyConstant` hooks are mapping/version-sensitive because they target Vanilla's internal retention limits. Treat Minecraft-version upgrades as a verification point for these hooks rather than introducing a larger custom chat subsystem prematurely.

Chat Search attaches a single hidden `TextFieldWidget` to vanilla `ChatScreen` during `init`, activates it only on `Ctrl+F`, and reuses the bounded session index observed from `ChatHud`. It does not replace `ChatScreen`, persist history, or add a background search service.

Chat Timestamps remain a thin `ChatHud.addMessage(Text)` argument transform rather than a custom renderer. The prefix uses a copied text component so the original message content retains its style and click metadata. Treat this method signature as a Minecraft-version verification point.

Compact Duplicate Messages only rewrites the newest unsigned vanilla `addMessage(Text)` entry. It removes that entry and its visible wrapped lines, then lets vanilla add the compact replacement again so line wrapping remains Minecraft-owned. This path intentionally does not target signed player-chat storage, does not run while chat is scrolled, and resets at connection boundaries. `messages`, `visibleMessages`, `scrolledLines`, and `ChatHudLine.Visible.endOfEntry()` are version-sensitive verification points.

Signing indicator suppression is presentation-only and is routed through `utility/chat/signing/SigningPresentationPolicy`. It must not evolve into packet/signature/reporting mutation inside the ChatHud mixin.

Report-button suppression is also presentation-only. The Social Interactions entry mixin only changes button visibility after vanilla initialization and intentionally leaves reportability/signature state intact.

Any retained No Chat Reports-equivalent protocol behavior belongs behind the same signing module with its own tests and runtime verification.

Narrator suppression uses Minecraft's own narrator options rather than removing narrator/accessibility classes. Accessibility infrastructure remains available when suppression is disabled.

Instant Creative Search targets `CreativeInventoryScreen.charTyped`, its existing `searchBox`, and private `setSelectedTab` path for Yarn 1.21.4. Treat Minecraft-version upgrades as a verification point for this mixin rather than introducing a replacement inventory/search controller.

Compact Debug targets `DebugHud.render` and the existing `Keyboard.onKey` debug-input path for Yarn 1.21.4. Treat Minecraft-version upgrades as verification points for these mixins. Keep the renderer thin and keep metric/telemetry state outside the mixin classes.

Keep Chat Draft, chat search, chat timestamps, compact duplicate messages, reconnect actions, screenshot naming, reload notifications, instant creative search, compact debug, narrator suppression, signing-indicator suppression, report-button suppression, and borderless startup application are event/screen-driven. None of them require a client tick loop or background poller.

## Preferences

Preferences are stored in Fabric's normal config directory as `lazybuilder-utility-manager.properties`.

The active preference surface is intentionally limited to implemented features:

```properties
window.borderless=false
chat.extended_history=true
chat.keep_draft=true
chat.search=true
chat.timestamps=true
chat.hide_signing_indicators=true
chat.hide_report_button=true
accessibility.suppress_narrator=true
connection.reconnect_button=true
screenshots.contextual_names=false
inventory.instant_creative_search=true
hud.compact_debug=true
```

The previous `screenshots.organize_by_project` key is accepted as a read-only migration alias so existing local configs continue to work. New saves use `screenshots.contextual_names`.

Auto Reconnect remains out of the active product surface. It may only return when the feature itself is implemented and verified rather than added as speculative configuration.
