# Operations and Recovery Reference

Use for Launcher actions that are long-running, destructive, restart-sensitive, retryable, cancellable, or capable of partial completion.

Examples include server duplicate, backup/restore, repair, provisioning/update, large Launcher-owned copy/export, and support-data export.

## Current operation contract

The shared Launcher operation layer already exists. Reuse it rather than inventing a feature-specific queue/history model.

Current operation snapshots expose:

```text
id
correlationId
kind
resource
state
phase
status
details
progress { current, total?, unit } when measurable
canCancel
cancelRequested
warnings
error { code, message, details, recoverable }
created / updated / completed timestamps
```

Current global operation states are:

```text
Queued
Running
Succeeded
Failed
Cancelling
Cancelled
RecoveryRequired
```

Feature-specific progression belongs in `phase` and status text, for example:

```text
phase = validating
phase = copying
phase = verifying
phase = publishing
```

Do not expand the global state enum merely to encode every workflow step.

## Ownership and exclusivity

One operation identity tracks execution; domain services remain authorities for domain truth.

Use exclusive operation ownership when two operations on the same resource would conflict. A second caller consumes busy/current state instead of starting duplicate work.

Operation history is bounded diagnostics/execution history, not a second domain database.

## Cancellation

Advertise cancellation only while the underlying workflow has a real safe boundary.

```text
safe cancellable phase
→ canCancel = true
→ request sets Cancelling/cancelRequested
→ worker observes cancellation
→ Cancelled after safe cleanup
```

Before an irreversible publish/commit boundary, disable cancellation if stopping would create ambiguous durable state.

Never expose Cancel merely because the UI wants one.

## Progress

Prefer semantic progress:

```text
phase
current
total when measurable
unit
```

Indeterminate work may omit total. Do not manufacture percentages.

A refresh/query failure after a durable operation already committed must not relabel the committed mutation itself as failed.

## Failure-atomic filesystem pattern

For Launcher-owned copy/replace/publish work:

```text
validate source/target authority
→ preflight capacity/permissions where useful
→ create uniquely owned staging path
→ perform work in staging
→ validate staged result
→ persist recovery intent when interruption could become ambiguous
→ publish/rename atomically where practical
→ update authoritative registry/metadata at the defined commit boundary
→ cleanup stale staging/previous state only after stable success
```

Ordering can vary by domain, but the commit boundary and interruption behavior must be explicit.

## Filesystem safety

Before destructive mutation:

- resolve canonical object identity first;
- derive target paths from trusted registry/manifest state, not arbitrary UI strings;
- canonicalize/validate existing paths as needed;
- reject unsafe recursive relationships such as destination-inside-source;
- handle symlink/reparse surprises when traversal cannot prove safety;
- restrict staging/recovery paths to the exact operation intent;
- never delete parent/root paths based only on display names or filename patterns.

## Disk space

Large operations should preflight measurable capacity when useful, but must still handle ENOSPC during execution because free space can change.

Failure should leave source intact, avoid registering half-created output, and either clean staging or leave an explicitly owned recovery artifact.

## Retry classes

### Safe retry

No durable side effect committed, or owned staging can be safely restarted/reused.

### Reconcile then continue

Some durable work completed and authoritative recovery metadata can determine the actual state.

### Recovery required

The system cannot safely infer completion/rollback automatically. Mark the operation `RecoveryRequired` and preserve enough evidence for explicit recovery.

Never blindly replay destructive effects after timeout/error. Inspect authoritative state first.

## Startup recovery

Persistent operations that can outlive process termination require idempotent reconciliation:

```text
load recovery intent
→ validate identity/path ownership
→ inspect source/staging/final state
→ choose finish / rollback / preserve / RecoveryRequired
→ reconcile authoritative registry/domain state
→ remove recovery intent only after stable completion
```

Running reconciliation twice must not duplicate or delete unrelated data.

## Process-aware workspace mutation

Before mutating a server workspace:

```text
Launcher/domain state permits mutation
AND
process authority confirms no live Paper process still owns the target
```

A UI/status label alone is not sufficient because Java may linger after crash or transition.

## Backup / duplicate / restore distinction

```text
Duplicate → new independently registered server identity
Backup    → restore point for the same server identity
Restore   → replaces current state from a selected restore point
```

Restore should prefer staged validation and safe publication over in-place file-by-file replacement.

## Repair

Repair is diagnosis-driven:

```text
canonical health evidence
→ classify known recoverable defect
→ bounded planned mutation
→ verify authority again
```

Do not turn Repair into a generic reset button or silent user-data replacement path.

## Diagnostics

A failed operation should retain bounded, sanitized context sufficient to correlate the failure:

```text
Launcher build/version
operation id + correlation id
kind/resource
state + phase
stable error code/details
relevant runtime/provider versions
bounded logs
```

Never include tokens, credentials, private signing keys, or unnecessary personal content.

## Test matrix

Cover only cases applicable to the changed operation, especially:

```text
success
conflicting active operation
invalid target identity
process still running
permission denied
insufficient disk
partial staging failure
failure around publish/registry commit boundary
restart recovery
safe retry
cancellation before/after allowed boundary
repeated recovery idempotence
source preserved on failure
```

Use the smallest fixture capable of falsifying the changed recovery claim.
