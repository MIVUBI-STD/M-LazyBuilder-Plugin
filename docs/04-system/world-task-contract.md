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
World-Manager task runner
  ↓
existing application service
```

## Initial task model

Each task snapshot exposes:

- `taskId`: stable UUID for polling;
- `type`: `CLONE`, `BACKUP`, `IMPORT`, `EXPORT`, `ARCHIVE`, `RESTORE`, or `DELETE`;
- `worldId`: managed world ID when the operation targets an existing world; may be absent for pre-publication imports;
- `state`: `QUEUED`, `RUNNING`, `SUCCEEDED`, or `FAILED`;
- `progressPercent`: monotonic `0..100` progress;
- `message`: short user-facing status;
- `result`: structured/result reference once successful;
- `error`: safe failure detail once failed;
- `createdAt` / `updatedAt`: UTC timestamps.

`WorldTaskRegistry` owns only bounded in-memory task state. It intentionally owns no scheduler, worker pool, filesystem behavior, or world mutation. The existing World-Manager application services remain the semantic owners of clone/export/import/lifecycle/delete behavior.

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

Task history is bounded. Completed entries may be evicted when the history limit is reached, but active `QUEUED` / `RUNNING` tasks must not be removed just to satisfy history trimming.

Task persistence across a full Paper restart is intentionally out of scope for the first implementation. Interrupted tasks are therefore treated as runtime work, not durable jobs. Add durable recovery only if real operations later require it.

## HTTP surface planned after the registry is compile-green

```text
POST /v1/tasks/<operation>
GET  /v1/tasks/{taskId}
GET  /v1/tasks
```

Starting a task should return quickly with `202 Accepted` and the initial snapshot. The HTTP listener must not perform conversion, copy, archive, import/export, or delete work inline.

## Threading rule

Task orchestration may run outside the Paper main thread, but any operation that reaches Bukkit/Paper world APIs must dispatch that specific mutation to the Paper scheduler. Filesystem/conversion work should not occupy the main server thread.

## Security rules

1. All task endpoints use the existing loopback bearer-token authentication.
2. Desktop supplies managed IDs and validated logical inputs, not arbitrary filesystem paths.
3. Task results expose logical artifact/result references instead of unrestricted host paths where possible.
4. Error payloads must not leak bearer tokens or secrets.
5. Delete remains strongly confirmed in the desktop UX and must retain server-side protection for the default world.

## Implementation order

1. task state/type/snapshot/registry contract;
2. registry tests and CI proof;
3. task runner abstraction and bounded executor ownership;
4. read-only task HTTP endpoints;
5. wire one low-risk operation first;
6. verify lifecycle/cancellation/shutdown behavior;
7. add the remaining heavy operations one at a time.

Do not expose all heavy operations at once merely because the task transport exists.
