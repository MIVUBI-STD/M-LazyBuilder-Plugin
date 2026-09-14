# Reliability / Recovery Audit

## Purpose

This audit reviews whether LazyBuilder needs additional builder-facing reliability or recovery features after the Review / Collaboration audit.

The goal is not to create a second autosave, backup, snapshot, rollback, transfer-resume, or recovery engine. The goal is to identify whether builders still lack clear failure context or safe recovery guidance in workflows already owned by Map Manager / World Manager.

## Existing recovery foundations

LazyBuilder already has substantial recovery behavior in the existing world/transfer architecture.

### Transfer path

The current transfer flow already provides:

- bounded transfer sessions;
- SHA-256 verification before publication/finalization;
- client-side usable-space preflight for downloads;
- server-side space/size validation for uploads;
- partial-file cleanup on failure;
- explicit upload/download abort paths;
- cleanup of active sessions on disconnect;
- cleanup of completed import uploads that were never claimed for review;
- atomic/fallback-safe local publication for completed downloads;
- no idle polling or permanent recovery worker.

`ClientTransferController` already exposes explicit transfer phases and a `FAILED` state with an operation-specific message. It also deletes local partial download files when a download aborts.

### World-control path

`ClientWorldController` already exposes:

- `activityMessage` for the current operation;
- `lastError` for failed world operations;
- authoritative responses from Paper;
- explicit cleanup of abandoned import-review artifacts.

Lifecycle/destructive actions also use a confirmation screen before execution. Delete/reset actions are visually marked as dangerous rather than being silently executed.

### Network/session recovery

The networking contract already defines:

- active transfer cleanup on disconnect;
- no cross-connection byte-transfer resume token;
- completed heavy world-operation results may be retained in bounded form for surfacing after reconnect;
- malformed/out-of-order requests fail closed and clean affected state.

Therefore, the product already has recovery ownership. A new generic Recovery Manager would duplicate existing authorities.

## Candidate audit

| Candidate | Decision | Reason |
| --- | --- | --- |
| New Recovery Manager | Reject | Recovery already belongs to the operation owner; a new Manager would split authority. |
| Client autosave system | Reject | Minecraft/world runtime already owns world saving; duplicating it creates dangerous semantics. |
| Client world backup engine | Reject | World/file lifecycle belongs to World Manager/server-side file ownership, not a Fabric convenience layer. |
| Automatic snapshot before every build action | Reject | High storage/latency cost and overlaps server/world-management ownership. |
| Undo/rollback system | Reject | Direct editing/build ownership belongs to Axiom/WorldEdit and world-management semantics. |
| Resumable transfer across reconnect | Reject for current scope | Requires persistent resume/security state and a second transfer lifecycle. Current restart behavior is intentional. |
| Background orphan scanner | Reject | Existing cleanup is event/session owned; polling would add unnecessary background work. |
| Generic crash/session journal | Reject | Broad subsystem without a demonstrated LazyBuilder-specific recovery gap. |
| More confirmation dialogs everywhere | Reject | Confirmation should be reserved for genuinely destructive/irreversible operations. |
| Actionable failure context in existing surfaces | Keep as presentation rule | Existing controllers already expose operation state and errors; improve those surfaces rather than creating a new subsystem. |
| Explicit safe retry where the operation is naturally retryable | Candidate rule | Retry should restart the existing operation from its canonical owner, never create a second recovery path. |

## Approved reliability rule: actionable failure context

The only additional reliability direction approved by this audit is a presentation standard, not a new runtime feature.

When an operation fails, the existing owning surface should make three things clear where available:

```text
What failed
Why it failed
What the builder can safely do next
```

Examples:

```text
Import upload failed
Reason: selected file is missing
Next: choose the file again

Export download failed
Reason: not enough space at the selected location
Next: choose another destination with sufficient space

World operation failed
Reason: <server-authoritative message>
Next: retry only if the operation remains valid
```

Do not invent a recovery action when the system cannot prove it is safe.

## Retry contract

Retry is acceptable only when it re-enters the existing canonical operation from the beginning.

```text
failed transfer
→ cleanup partial/session state
→ idle/failed presentation
→ user explicitly starts the same operation again
```

Rejected retry behavior:

- hidden automatic retry loops;
- background retries after disconnect;
- continuing from an unverified partial file;
- retry queues;
- persistent retry jobs;
- retrying destructive operations without explicit user intent.

For byte transfer, restart-from-zero remains the correct current model after connection loss.

## Destructive-action contract

Confirmations remain appropriate for lifecycle/destructive operations such as delete/reset when the user decision is materially consequential.

They should not spread to ordinary navigation, map inspection, copying references, or routine safe actions. Warning fatigue would make real destructive confirmations less useful.

## Ownership lock

```text
Transfer recovery / partial cleanup  -> existing transfer owner
World lifecycle recovery            -> Paper World Manager / World Control
Destructive confirmation UX         -> existing Map Manager surface
Build/edit undo                      -> Axiom / WorldEdit ecosystem
Performance failure                 -> Performance Manager only where it owns the policy
Generic client convenience failure  -> Utility Manager only for its own feature
```

No shared Recovery service is justified.

## User-facing reliability boundary

LazyBuilder should favor explicit state over invisible automation.

Allowed:

- concise operation progress;
- concrete error text;
- safe next-step guidance;
- destructive confirmation;
- cleanup of partial/request-owned state;
- explicit manual retry when safe.

Rejected by default:

- autosave replacement;
- backup engine duplication;
- automatic rollback;
- retry daemons;
- hidden healing loops;
- permanent warning HUD;
- recovery database;
- operation history database.

## Audit result

Reliability / Recovery does **not** justify a new feature subsystem.

Existing transfer, world-control, and confirmation paths already own the underlying safety behavior. The architectural improvement is to keep failure presentation actionable and operation-local, while retaining explicit manual retry only where the canonical owner can safely restart the operation.

With this audit, the post-C4 non-tool builder review has no remaining unreviewed category:

```text
Build Tool Overlap        complete
Session / Project Context complete
Review / Collaboration    complete
Reliability / Recovery    complete
```

Any further builder-facing feature should now require a concrete workflow problem rather than another generic category expansion.
