# Operations and Recovery Reference

Use for any Launcher action that is long-running, destructive, restart-sensitive, retryable, cancellable, or capable of partial completion.

Examples:

```text
server duplicate
server backup/restore
server repair
runtime provisioning/update
launcher self-update preparation
large import/export/copy controlled by Launcher
support package export
```

## Operation Contract

Every non-trivial operation should answer:

```text
operation id
operation kind
target identity
current state/current phase
progress if measurable
start time
safe cancel boundary
retry semantics
result or stable error
recovery metadata if interrupted
```

Do not create separate operation systems per feature. A future shared operation registry may coordinate presentation/history, but domain services remain authorities for domain state.

## Recommended State Shape

Use the smallest valid state machine. A general pattern is:

```text
Queued
→ Preparing
→ Running
→ Committing
→ Succeeded
```

Failure exits:

```text
Preparing/Running/Committing
→ Failed(retryability)
→ NeedsRecovery when durable state is ambiguous
```

Cancellation:

```text
Preparing/Running
→ Cancelling
→ Cancelled
```

Never advertise Cancel when the operation cannot stop without corrupting/ambiguating state.

## Progress

Prefer semantic progress:

```text
phase: Copying server files
bytesCurrent
bytesTotal
itemsCurrent/itemsTotal when meaningful
```

Progress can be indeterminate when work cannot be measured. Do not manufacture percentages.

A later refresh failure must not convert an already committed operation into a false failure.

## Failure-Atomic Filesystem Pattern

Default pattern for copy/replace/publish work:

```text
validate authority/source/destination
→ capacity/permission preflight where useful
→ create uniquely owned staging path
→ perform work in staging
→ validate staged result
→ persist recovery intent when needed
→ atomic rename/publish
→ update registry/metadata authority
→ cleanup old/staging state
```

Ordering may differ when the registry is the source of truth, but interruption behavior must be explicit.

## Filesystem Safety

Before destructive mutation:

- resolve registered object identity first;
- canonicalize paths where the target exists;
- derive mutation path from trusted registry/manifest data, not arbitrary UI input;
- validate target-specific manifest/identity where available;
- reject symlink/reparse surprises when recursive traversal cannot safely reason about them;
- reject destination-inside-source for recursive copy;
- restrict recovery staging to the exact original parent/name/intent;
- never delete parent/root paths based only on filename patterns.

## Disk Space

Large operations should preflight capacity when practical.

Estimate:

```text
required = measurable payload + safety headroom
```

Still handle ENOSPC during execution because available space can change after preflight.

Failure must:

```text
leave source untouched
remove or retain clearly identified recovery staging
avoid registering a half-created result
return a stable actionable error
```

## Retry Semantics

Classify failures:

### Safe retry

No durable side effect committed, or staging identity can be reused safely.

### Resume/reconcile

Some durable work completed and authoritative recovery metadata exists.

### Manual recovery required

State cannot be safely inferred automatically.

Do not blindly replay effects after a timeout/error. Check the authoritative state first.

## Startup Recovery

Persistent operations that can survive process termination need startup reconciliation.

Pattern:

```text
load durable operation intent
→ validate ownership/signature/path identity
→ inspect staged/final/original state
→ choose finish / rollback / preserve original / require repair
→ update authoritative registry
→ remove recovery record only after stable completion
```

Recovery should be idempotent. Running it twice must not delete or duplicate unrelated data.

## Process-Aware Mutations

Before mutating a server workspace:

```text
launcher state allows mutation
AND
process authority confirms no Paper process owns target
```

State labels alone are insufficient because Java can linger after crash/transition.

## Backup and Restore

Backup and duplicate are different semantics.

### Duplicate

Creates a new independently registered server with a new identity.

### Backup

Creates a restore point for the same server identity.

### Restore

Preferred pattern:

```text
verify server offline/process-free
→ validate backup integrity/compatibility
→ create pre-restore safety snapshot when feasible
→ stage restored workspace/data
→ validate
→ atomic publish/swap
→ retain recovery metadata until post-restore verification succeeds
```

Never overwrite the current server in place file-by-file when an atomic/staged replacement can provide safer semantics.

## Repair

Repair must be diagnosis-driven, not a collection of destructive guesses.

```text
inspect canonical health checks
→ classify known recoverable problem
→ show planned repairs to UI
→ mutate only owned recoverable state
→ verify health again
```

Repair must not silently reset worlds/configuration or replace user data unless explicitly part of a confirmed recovery flow.

## Diagnostics / Support Data

For each failed operation capture enough structured evidence to answer:

```text
launcher version/build
operation kind/id
safe phase/state
stable error code
provider/context chain
relevant runtime versions
sanitized target identity
latest bounded logs
```

Never include secrets, tokens, auth material, private signing keys, or unnecessary personal content.

## Test Matrix

For long-running/destructive work, cover as applicable:

```text
[ ] normal success
[ ] destination already exists
[ ] invalid target identity
[ ] target process still running
[ ] permission denied
[ ] insufficient disk space
[ ] partial staging failure
[ ] failure after durable intent but before publish
[ ] failure after publish but before registry update
[ ] restart recovery
[ ] retry after failure
[ ] cancellation at each declared safe boundary
[ ] repeated recovery is idempotent
[ ] source remains intact on failure
```
