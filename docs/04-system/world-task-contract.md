# Asynchronous World Task Contract

## Purpose

Long-running world operations must not block the loopback HTTP listener or duplicate World-Manager business logic in the desktop runtime.

The task contract is server-owned. Desktop only starts tasks and reads task snapshots.

```text
Svelte
  ↓ Tauri command
Rust desktop client
  ↓ authenticated loopback HTTP
World-Manager task endpoint
  ↓
WorldTaskRunner
  ↓
existing application service
```

## Task model

Each task snapshot exposes:

- `taskId`: stable UUID for polling;
- `type`: `CLONE`, `BACKUP`, `IMPORT`, `EXPORT`, `ARCHIVE`, `RESTORE`, or `DELETE`;
- `worldId`: managed world ID when the operation targets an existing world; may be absent for pre-publication imports;
- `state`: `QUEUED`, `RUNNING`, `SUCCEEDED`, or `FAILED`;
- `progressPercent`: monotonic `0..100` progress;
- `message`: short user-facing status;
- `result`: result/reference once successful;
- `error`: safe failure detail once failed;
- `createdAt` / `updatedAt`: UTC timestamps.

`WorldTaskRegistry` owns only bounded in-memory task state. Existing World-Manager application services remain the semantic owners of clone/export/import/lifecycle/delete behavior.

`WorldTaskRunner` owns only bounded asynchronous execution. It uses a small fixed worker count, publishes lifecycle/progress through `WorldTaskRegistry`, and has explicit shutdown ownership in the Paper plugin lifecycle. It does not become a second owner for world or filesystem behavior.

## Transition rules

```text
QUEUED
  ↓
RUNNING
  ├──→ SUCCEEDED
  └──→ FAILED
```

Progress may only move forward while `RUNNING`. Successful tasks finish at `100%`. Terminal tasks cannot be restarted by mutating the same task ID; a retry creates a new task.

## History and memory

Task history is bounded. Completed entries may be evicted when the history limit is reached, but active `QUEUED` / `RUNNING` tasks must not be removed merely to satisfy history trimming.

Task persistence across a full Paper restart is intentionally out of scope for the first implementation. Interrupted tasks are runtime work, not durable jobs. Durable recovery should be introduced only if a real operation later requires it.

## Current read-only HTTP surface

The authenticated loopback bridge now exposes task observation without exposing any heavy mutation yet:

```text
GET /v1/tasks
GET /v1/tasks/{taskId}
```

Responses are small JSON snapshots. Unknown task IDs return `404`; malformed UUIDs return `400`. The endpoints are read-only and use the same bearer token as the rest of the World-Manager bridge.

## Future task-start surface

Heavy operation start endpoints remain intentionally gated. When added, they use the existing `/v1/tasks/...` namespace and must return quickly with `202 Accepted` plus the queued task snapshot. The HTTP listener must not perform conversion, copy, archive, import/export, or delete work inline.

## Threading rule

Task orchestration may run outside the Paper main thread, but any operation that reaches Bukkit/Paper world APIs must dispatch that specific mutation to the Paper scheduler. Filesystem/conversion work must not occupy the main server thread.

## Shutdown rule

WorldTaskRunner is closed explicitly during plugin shutdown after the local HTTP bridge stops accepting new requests. The runner first performs bounded graceful shutdown and then interrupts remaining worker tasks if the timeout expires. A task interrupted during shutdown is recorded as `FAILED` for the lifetime of that runtime.

## Security rules

1. All task endpoints use the existing loopback bearer-token authentication.
2. Desktop supplies managed IDs and validated logical inputs, not arbitrary filesystem paths.
3. Task results expose logical artifact/result references instead of unrestricted host paths where possible.
4. Error payloads must not leak bearer tokens or secrets.
5. Delete remains strongly confirmed in the desktop UX and must retain server-side protection for the default world.

## Implementation order

1. task state/type/snapshot/registry contract — complete;
2. registry tests and CI proof — complete;
3. bounded WorldTaskRunner + explicit shutdown ownership — implemented, CI-gated;
4. read-only task HTTP endpoints — implemented, CI-gated;
5. wire one low-risk operation first;
6. verify operation-specific main-thread boundaries and shutdown behavior;
7. add the remaining heavy operations one at a time.

Do not expose all heavy operations at once merely because the task transport exists.
