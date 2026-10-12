# Fabric Utility Manager — Capture production audit (2026-10-13)

## Scope and status

Capture only: familiar **F2 Screenshot**, **F10 Clean Screenshot**, **F9 Start/Stop Video**, Capture Settings, output ownership, crash recovery and memory/encoder behavior. Building/editing, new capture products, and all World Manager logic remain **out of scope**.

**State:** Source remediation implemented on branch `Local`; **no exact-head Fabric build, Windows GPU run, FFmpeg hardware-encoder benchmark or end-to-end playback acceptance has been executed here**. Tests exist for safe output and config changes but have not been executed in the repo. Do not describe these patches as production-ready until the integrated gate below passes.

## Mature-system comparison

- Minecraft vanilla F2 normally saves lossless PNG under `screenshots/`. A new LazyBuilder profile now chooses `ScreenshotQuality.MAXIMUM` (PNG) by default; the existing JPEG choices remain for intentional size savings, and previously persisted quality preferences are preserved.
- OBS recommends recording **MKV** for recoverability, followed by **remux** to MP4 where required. LazyBuilder already used MKV-first and preserved MKV when MP4 remux failed; retain this model instead of writing vulnerable monolithic MP4 during capture.
- FFmpeg's documented `-n` flag refuses to overwrite an existing output. Both direct video recording and MP4 remux now use `-n`, and Java MKV and screenshot publication use no-clobber file moves instead of `REPLACE_EXISTING`.
- Java NIO documents that `ATOMIC_MOVE` may replace an existing file depending on provider; it is not a portable substitute for no-overwrite semantics. Preserve existing user files when destination names collide.

References:
- https://obsproject.com/kb/audio-video-formats-guide
- https://obsproject.com/kb/standard-recording-output-guide
- https://ffmpeg.org/ffmpeg.html
- https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html

## Confirmed source findings and remediations

### P1 — Capture preference storage could follow unsafe paths

`CaptureConfigStore` previously used a predictable `lazybuilder-capture.properties.tmp` and opened a config target without `NOFOLLOW_LINKS`, while `UtilityConfigStore` already had stronger patterns.

**Changed:** only accept an ordinary target file; do not load/replace an unsafe link or directory; create an exclusive random-suffix temporary file, force file data, then publish preferences with atomic replace where supported. Never overwrite someone else's predictable `.tmp` name. Existing saved capture preferences remain authoritative.

### P1 — Screenshot outputs could clobber files

`ScreenshotCaptureService` selected a unique target at one instant but committed with `REPLACE_EXISTING` and used a predictable `.part` name.

**Changed:** exclusive `Files.createTempFile` for the encoder output and no-replace `Files.move` for final publication. If another file now occupies the name, fail and explain rather than overwrite the user's image. Normal F2 still falls back to vanilla if first-party capture cannot accept the work.

### P1 — Temporary clean screenshot view could remain hidden after failure

`GameRendererCaptureMixin` temporarily hides HUD, hand and outline to capture F10, but previously restored them only on the normal success path.

**Changed:** restore all three flags and clear active-request state in `finally` when the end-of-frame capture handler runs, including a failed encoder/readback or missing framebuffer. This does not guarantee cleanup for an unrelated fatal render exception that prevents the RETURN injection from running entirely; that requires in-game fault acceptance.

### P1 — Recorded video could clobber a same-named MKV or MP4

`VideoCaptureSession` used FFmpeg `-y` for raw-video recording and MP4 remux, and `Files.move(..., REPLACE_EXISTING)` for MKV publication.

**Changed:** FFmpeg `-n` on both operations and a no-overwrite MKV publication. A collision becomes an operation error; preserve the original `.mkv.partial` as recovery evidence and retain any MKV if remux cannot complete.

### P1 — Video readback direct/GPU memory was only limited by int range

A triple-buffered CPU packet pool plus three PBOs could reserve hundreds of MiB for a giant framebuffer even if the chosen output resolution was small.

**Changed:** source readback preflight capped at **64 MiB per frame** (roughly 5K accepted; 8K rejected). The rejection message asks users to reduce the actual Minecraft display framebuffer, not just the encoder output setting. Release queued direct-buffer references after the writer terminates. Retain the existing event-driven, bounded queue and dropped-frame behavior rather than blocking the render thread.

**Residual:** GPU drivers and native-memory pressure vary. This cap is a conservative source ceiling, not proof of stable FPS or a measured RAM ceiling.

### P1 — FFmpeg discovery could stall on unread subprocess pipes

`FfmpegCapabilities` waited for `-encoders` to exit before reading combined stdout/stderr. On Windows with a small child-process pipe, the child can block filling that pipe.

**Changed:** discard output for `-version` / encoder-init probes; redirect the encoder catalogue to an exclusively-created temporary file, cap its read size at 2 MiB, and delete it afterward. No additional permanent worker or process monitor.

### P2 — Encoder finalization error could strand STOPPING

After a forcibly destroyed FFmpeg process, `VideoCaptureSession` could still invoke `process.exitValue()` while the process remained alive, causing an unchecked exception with no normal failure transition.

**Changed:** check `process.isAlive()` and enter FAILED with explicit preservation wording if FFmpeg remains. MP4 stream-copy remux can take more than 30 seconds on large files, so its bounded wait was raised to 5 minutes, redirecting output away from an unread pipe. The original MKV remains if remux fails.

### P2 — GPU fence failure could prevent recording from draining

`FrameReadbackRing` treated `GL_WAIT_FAILED` like an unsignalled fence and could leave a pending slot forever. **Changed:** release that fence and signal recording failure rather than waiting indefinitely.

### P2 — Recovery could follow symlink-like fake partial recordings

`CaptureRecovery` scanned `*.mkv.partial` by name. **Changed:** only admit ordinary non-symlink files before invoking FFmpeg or renaming. Remux recovery retains the original already-used no-overwrite policy.

## Familiar UX retained

- F2 stays the normal screenshot key; F10 stays the clean screenshot key; F9 toggles recording. Existing keybinding settings remain authoritative.
- Recording is deliberately **video-only**, with no false suggestion that game audio is captured.
- Default PNG mirrors vanilla F2 for new profiles; JPEG remains a deliberate trade-off and existing preferences are not rewritten.
- Capture Settings reports `captures/videos` as the output location, explains MP4/MKV fallback and labels frame rate as a target under load.
- Avoid extra popup managers, mandatory advanced settings or video/audio devices that this runtime cannot actually control.

## Proof required before production

1. **Screenshot:** ordinary F2 JPEG/PNG, clean F10 with HUD/hand restoration under save error, existing target collision, symlink config and repeated screenshots at native 4K; inspect resulting files and Minecraft frame timing.
2. **Video:** F9 at 1080p/4K with supported hardware and software encoders; repeated start/stop, resolution changes, insufficient disk, 8K budget rejection and direct GPU fence failure. Measure peak resident/native memory and dropped frames across long sessions.
3. **Recovery/finalization:** kill/restart while recording, corrupted/incomplete MKV, missing FFmpeg, oversized remux, same-named pre-existing MP4/MKV and staged `.mkv.partial` source; prove no unrelated user data is modified.
4. **UI/compatibility:** Capture Settings keyboard navigation, file discovery, status updates after failures, F2 vanilla fallback, and video-only disclosure.

Run the same SHA through Fabric Gradle verification and Windows native FFmpeg/OpenGL acceptance. Existing test source does not constitute executed test evidence. Neither a prior CI run nor `get_commit_combined_status` returning no checks proves PASS.

## Deferred issues

- Client screenshot link/open-folder convenience: evaluate after usability testing; no new file browser is approved by default.
- Full audio capture: current recorder intentionally does not capture system/game audio; do not promise it without an approved backend and privacy/permission analysis.
- Per-device automatic memory/FPS tuning: only consider if long-session measurements demonstrate a repeatable problem; avoid speculative AI-slop configuration knobs.
