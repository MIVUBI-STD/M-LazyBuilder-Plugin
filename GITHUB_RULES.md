# GitHub Rules — LazyBuilder Plugin

Canonical repository mutation rules.

## 1. Pin authority

Before a material write, know:

- repository;
- intended branch/ref;
- current target state;
- exact scope;
- required proof.

`Local` is the normal development authority. Never silently fall back to `main`. Pin the current HEAD SHA before a material write and use an expected-HEAD fast-forward ref update; never force-push to resolve a stale or conflicted write.

Every tracked file must resolve to one canonical path-access owner through `.agents/permissions/path-access.json`. The existing `scripts/verify_agent_governance.py` checks tracked-path coverage through Git's index when available. An unlisted path is **deny** until a justified owner/scope is assigned; do not add a catch-all writable rule. Path ownership is not user authorization.

## 2. Read minimum

Prefer direct reads of known owners. Search only when the owner/path is unknown. Do not broad-scan the repository for reassurance.

Default investigation:

```text
owner/source files: 1–3
history reads:      0 unless rationale materially matters
broad scans:        0 unless ownership is genuinely unknown
```

## 3. Diagnose first

Classify the first wrong owner:

```text
requirement/policy wrong -> documentation or contract owner
implementation wrong     -> implementation owner
assertion stale          -> test owner
CI routing wrong         -> workflow owner
environment unavailable  -> environment/capability owner
runtime-only uncertainty -> LIVE_SERVER proof owner
```

Do not redesign the system to fix a bounded defect.

## 4. GitHub-first partition and ChatGPT no-local-PC invariant

For ChatGPT-owned `M-LazyBuilder-Plugin` work, `Local` means the remote GitHub branch, **not** a checkout or folder on the user's PC. Normal source development, repository governance, commits, and recoverable continuity are **ChatGPT + GitHub/cloud-only**. Never ask the user to clone/download source, install developer tools, run `DEV.cmd`/npm/Gradle/Maven, provide PC paths or data, operate Minecraft, or perform local verification as a prerequisite or workaround for ChatGPT-owned GitHub source work.

Use the strongest authorized capability actually available:

```text
exact GitHub Local source + canonical owner
→ scoped source/static reasoning and authorized repository mutation
→ existing exact-SHA cloud/CI evidence when available and material
→ specific unverified claim remains UNKNOWN / NOT EXECUTED
```

`REMOTE_GITHUB` may finish the independently provable source task without executable tooling. Source tests/fixtures may be prepared remotely without representing them as executed. Never make CI registration, manual dispatch, a shell, or runtime access a blanket prerequisite for source work.

`LOCAL_CODE`, `LIVE_SERVER`, and target Windows native acceptance are valid proof contexts for claims that genuinely require them, not an automatic ChatGPT-to-user handoff. If unavailable through an authorized ChatGPT/cloud route, finish the GitHub-valid partitions, state only the precise remaining proof residue, and STOP. Human/Codex local development and separately authorized installer/native acceptance keep the existing `DEV.cmd` and release contracts; they do not make the user PC a ChatGPT dependency.

## 5. Write once

Before the first mutation, finalise the intended file set and content. Prefer one coherent commit per logical outcome.

Commit format:

```text
feat(<scope>): ...
fix(<scope>): ...
refactor(<scope>): ...
test(<scope>): ...
docs(<scope>): ...
ci(<scope>): ...
build(<scope>): ...
chore(<scope>): ...
```

Do not create checkpoint/retry commits or temporary files solely to transfer work.

### Commit-based continuity (no duplicate status database)

For every material, independently valid logical commit, include `Work`, `State`, and `Proof` in its body. Add `Decision`, `Unresolved`, and `Next` when applicable; never invent unavailable evidence:

```text
Work: <domain / outcome>
State: ACTIVE | BLOCKED | PAUSED | DONE
Decision: <canonical owner / reason>
Proof: <actual evidence and its ceiling>
Unresolved: <specific remaining risk>
Next: <one next action, when incomplete>
```

Current source and `docs/05-operations/` remain the canonical continuation/proof owner where those documents already apply. Git history captures previous work and decisions; it is **not** an automatic instruction to execute the next commit. Do not add a second TODO, session memory, checkpoint, status tracker, or roadmap to duplicate them. A clean, complete work item may finish with `State: DONE` and no new task.

### Short-prompt repository startup and topic-based recovery

A repository/branch URL with "amati" is a read-only INSPECT request. Pin the exact `Local` HEAD, read current `AGENTS.md` and `GITHUB_RULES.md`, then resolve the smallest current semantic owner. Report confirmed state, scoped unfinished work, proof limits, and one justified next step; do not commit.

For "lanjutkan" or a new chat, recover by the named work topic, not merely the last repository commit. Reconcile relevant `Work` / `State` / `Proof` / `Unresolved` / `Next` history with subsequent changes to affected source. Distinguish `PAUSED`, `BLOCKED`, completed logical units and unresolved claims. A historical `Next` is never stand-alone authorization. If topic/scope genuinely cannot be distinguished, mark that ambiguity rather than creating a speculative task.

Prior chat contents, chat titles, sidebar names and Project settings do not replace GitHub state or require a naming convention. A Project bootstrap, if used, points to the current `Local` root rules instead of copying them. No parallel continuation file, progress tracker, chat-memory store, or second authority may be created.

## 6. Integrity rules

- Never commit secrets, credentials, private keys, server tokens, `.env` values, or production data.
- Do not commit IDE state, local server worlds, logs, crash reports, caches, compiled jars, build directories, `dist/`, `.runtime-proof/`, or temporary workflow artifacts.
- Pin runtime/build assumptions deliberately; do not perform unrelated dependency upgrades.
- Preserve existing public behavior during refactors unless behavior change is explicitly in scope.
- Do not silently introduce NMS/version-coupled internals. Any NMS use must have an explicit owner, justification, and supported-version boundary.
- Main-thread vs asynchronous work must be explicit. Bukkit/Paper state must not be accessed asynchronously unless the API contract permits it.
- Persistent data/schema changes require backward/forward compatibility reasoning before release.

### GitHub Actions supply-chain contract

- External `uses:` actions are pinned to a full 40-character commit SHA. Keep the human-readable major/release as an inline comment when useful; do not execute movable tags or branches.
- Workflow token permissions stay explicit and least-privilege. Current verification/preview workflows require only `contents: read` unless a new capability proves otherwise.
- Read-only checkouts use `persist-credentials: false`; workflow steps must not inherit repository write credentials unnecessarily.
- Every job has an explicit `timeout-minutes` appropriate to its proof boundary; do not rely on GitHub's multi-hour default.
- All verification workflows are manual-only (`workflow_dispatch`): no `push`, `pull_request`, `schedule`, `workflow_run`, release or tag triggers. The user chooses when to run a checkpoint.
- Caches accelerate deterministic builds; they are never authority for source, artifacts, or verification state.
- Adding OIDC, artifact attestations, signing, deployment credentials, or write permissions belongs to an explicit release/deployment requirement, not ordinary verification hardening.

## 7. Verification

Use the cheapest relevant check during development:

```text
docs/policy only       -> structural review/static validation
Java logic             -> compile + targeted tests
plugin packaging       -> build/package verification
Paper API integration  -> integration test where feasible
server lifecycle       -> LIVE_SERVER smoke/runtime test
```

Repository-wide final verification is intentionally outside the inner `Local` commit loop:

```text
ordinary push to Local -> no automatic verification workflow
pull request / push to main -> no automatic verification workflow
workflow_dispatch      -> integrated final Verify on explicit request
```

The durable developer/CI/promotion contract is `docs/04-system/development-operations.md`.

GitHub registers `workflow_dispatch` from the repository's default branch. A workflow present only on `Local` does not make the manual *Run workflow* action available. This is **not a blocker for ordinary Local source development**: run available scoped source checks instead. Do not fill or modify `main`, change the default branch, or bypass administration controls merely to register Actions; that remains a separate expressly approved release/administration decision. Release/update-channel workflows also require separate user authorization.

A green unrelated workflow is not proof. A skipped required check is not PASS. Focused Launcher, Paper, or visual workflows are supplementary evidence and do not become parallel repository-readiness authorities.

## 8. Failure policy

- Permission/safety denial: stop immediately.
- Missing target: verify repository/ref/path once.
- Conflict/stale state: refresh current state once, rebuild the intended change from authority.
- Same-cause failure: maximum two attempts when new evidence exists.
- Do not weaken tests or architecture rules merely to obtain green CI.

## 9. Release boundary

`main` is stable. Promotion from `Local` should contain a coherent verified state and must be explicitly requested by the maintainer. Do not use `main` as an ordinary work branch.

Normal promotion path:

```text
Local source
→ local finalization where applicable
→ integrated exact-head Verify
→ required target-machine/native acceptance
→ final proof/source audit
→ explicit promotion decision
→ main
```

Release publication/signing/update-channel changes are separate explicit operations and must not be hidden inside ordinary build/test commands.

## 10. STOP

Completion is terminal. When requested work plus relevant proof is complete, stop. Do not add speculative framework, documentation, or cleanup after acceptance is satisfied.
