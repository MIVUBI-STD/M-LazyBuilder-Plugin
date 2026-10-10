#!/usr/bin/env python3
"""Verify LazyBuilder repository routing and canonical-owner contracts.

This intentionally checks durable repository invariants only. It does not try to
infer runtime correctness or replace domain tests.
"""

from __future__ import annotations

from pathlib import Path
import json
import re
import sys

from verify_agent_governance import verify_agent_governance

ROOT = Path(__file__).resolve().parents[1]

REQUIRED_FILES = [
    "AGENTS.md",
    "GITHUB_RULES.md",
    "CONTEXT.md",
    "DEV.cmd",
    "toolchain.json",
    "tooling/windows-toolchain/dev.ps1",
    "tooling/windows-toolchain/scripts/verify/verify-fabric.ps1",
    "tooling/windows-toolchain/scripts/distribution/package-local.ps1",
    "docs/README.md",
    "docs/04-system/README.md",
    "docs/04-system/development-discipline.md",
    "docs/04-system/skill-routing.md",
    "docs/04-system/repository-layout.md",
    "docs/04-system/development-operations.md",
    "scripts/agent_preflight.py",
    ".agents/permissions/work-lanes.json",
    ".agents/permissions/path-access.json",
    ".agents/evals/routing-cases.json",
    ".agents/evals/permission-cases.json",
    ".agents/evals/preflight-cases.json",
    "docs/05-operations/current-verification.md",
    "docs/05-operations/project-map.md",
    "docs/05-operations/README.md",
    "docs/05-operations/deployment-v1.md",
    "docs/05-operations/client-implementation-proof-handoff.md",
    "apps/launcher/README.md",
    "plugins/world-manager/README.md",
    ".github/workflows/verify.yml",
    ".github/workflows/builder-verify.yml",
    ".github/workflows/builder-benchmark.yml",
    "mods/builder-utilities/build.gradle",
    "mods/builder-utilities/src/main/resources/fabric.mod.json",
]

FORBIDDEN_RETIRED_PATHS = [
    "SETUP-DEV.cmd",
    "CHECK-DEV.cmd",
    "BUILD-LAUNCHER.cmd",
    "TEST-LOCAL.cmd",
    "UPDATE-LAUNCHER.cmd",
    "scripts/sync_versions.py",
    "scripts/verify_client_artifacts.py",
    "tooling/windows-toolchain/docs/PACKAGE-JSON-PATCH.md",
    "tooling/windows-toolchain/docs/INTEGRATION.md",
    "apps/launcher/PRE-LOCAL-TEST.md",
    ".github/workflows/_temp-map-bottom-center-artifact.yml",
]

EXPECTED_SKILLS = {
    "lazybuilder-desktop-runtime",
    "lazybuilder-plugin-management",
    "lazybuilder-world-management",
    "lazybuilder-ui",
    "lazybuilder-protocol",
    "lazybuilder-paper-plugin-development",
    "lazybuilder-fabric-mod-development",
}

FORBIDDEN_STANDALONE_SKILLS = {
    "lazybuilder-development-brief",
    "lazybuilder-java",
    "lazybuilder-paper",
    "lazybuilder-fabric",
    "lazybuilder-rust",
    "lazybuilder-tauri",
    "lazybuilder-maven",
    "lazybuilder-gradle",
    "lazybuilder-testing",
    "lazybuilder-ci",
}

REQUIRED_AGENT_PHRASES = [
    "docs/04-system/development-discipline.md",
    "docs/04-system/skill-routing.md",
    "REMOTE_GITHUB",
    "LOCAL_CODE",
    "LIVE_SERVER",
    "Failure classification",
    "Evidence Opt-In Rule",
    "Historical/supporting documents are **not default context**",
    "Work-Lane Permission Preflight",
    "ChatGPT-GitHub Work Continuity",
    "Short-prompt repository startup",
    "ChatGPT + GitHub/cloud-only",
]

REQUIRED_GITHUB_RULE_PHRASES = [
    "GitHub Actions supply-chain contract",
    "full 40-character commit SHA",
    "persist-credentials: false",
    "timeout-minutes",
    "contents: read",
    "manual-only",
    "ChatGPT no-local-PC invariant",
    "Short-prompt repository startup",
    "ChatGPT + GitHub/cloud-only",
]

REQUIRED_DOC_ENTRY_PHRASES = [
    "Supporting evidence is opt-in",
    "Do not broad-scan evidence documents for reassurance",
    "Evidence = supporting proof/history, opt-in only",
]

REQUIRED_SYSTEM_DOC_PHRASES = [
    "Supporting evidence documents under `docs/04-system/`",
    "opt-in context only",
    "Do not add another architecture lock/report",
]

REQUIRED_DISCIPLINE_PHRASES = [
    "Evidence Before Mutation",
    "Failure Classification",
    "Execution Partition",
    "Exact-Commit Proof",
    "UNKNOWN",
]

REQUIRED_OPERATIONS_PHRASES = [
    "DEV.cmd",
    "tooling/windows-toolchain/dev.ps1",
    "one developer command surface",
    "verify <paper|fabric|launcher>",
    "DEV.cmd verify paper",
    "DEV.cmd verify fabric",
    "DEV.cmd verify launcher",
    "Do not add `verify all`",
    "dist/Local/",
    "workflow_dispatch",
    "Developer failure contract",
    "Operation",
    "Evidence",
    "Recovery",
    "ChatGPT repository source work",
]

REQUIRED_DEV_FAILURE_MARKERS = [
    "LAZYBUILDER OPERATION FAILED",
    "Operation :",
    "Exit code :",
    "Evidence  :",
    "Recovery  :",
    "fix the first actionable failure",
]

REQUIRED_DEV_VERIFY_MARKERS = [
    "'verify'",
    "verify requires exactly one scope: paper, fabric, or launcher",
    "Invoke-TargetedVerification",
    "verify-paper",
    "verify-fabric",
    "verify-launcher",
    "npm run verify:source",
]

FORBIDDEN_DEV_VERIFY_MARKERS = [
    "verify all",
    "verify-all",
]

ACTION_SHA_RE = re.compile(r"^[0-9a-f]{40}$")


def fail(errors: list[str], message: str) -> None:
    errors.append(message)


def read_text(relative: str, errors: list[str]) -> str:
    path = ROOT / relative
    if not path.is_file():
        fail(errors, f"missing required file: {relative}")
        return ""
    try:
        return path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        fail(errors, f"required text file is not UTF-8: {relative}")
        return ""


def skill_name(skill_file: Path) -> str | None:
    try:
        text = skill_file.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        return None
    match = re.search(r"(?m)^name:\s*([^\s]+)\s*$", text)
    return match.group(1) if match else None


def verify_workflow_security(workflow: Path, errors: list[str]) -> None:
    text = workflow.read_text(encoding="utf-8")
    relative = workflow.relative_to(ROOT)

    header = re.search(r"(?m)^on:\s*$", text)
    if not header:
        fail(errors, f"workflow must declare block-form manual trigger: {relative}")
    else:
        block = text[header.end():].splitlines()
        events = []
        for line in block:
            if line and not line[0].isspace() and not line.lstrip().startswith("#"):
                break
            match = re.match(r"^  ([a-z_][a-z_0-9-]*):", line)
            if match:
                events.append(match.group(1))
        if events != ["workflow_dispatch"]:
            fail(errors, f"workflow must be workflow_dispatch-only: {relative}: {events}")


    if not re.search(r"(?m)^permissions:\s*\n\s{2}contents:\s*read\s*$", text):
        fail(errors, f"workflow must declare least-privilege contents: read: {relative}")

    runs_on_count = len(re.findall(r"(?m)^\s{4}runs-on:\s*", text))
    timeout_count = len(re.findall(r"(?m)^\s{4}timeout-minutes:\s*\d+\s*$", text))
    if runs_on_count == 0 or timeout_count < runs_on_count:
        fail(errors, f"every workflow job must declare timeout-minutes: {relative}")

    checkout_count = 0
    for match in re.finditer(r"(?m)^\s*-\s*uses:\s*([^\s#]+)", text):
        target = match.group(1)
        if target.startswith("./"):
            continue
        if "@" not in target:
            fail(errors, f"external action reference has no immutable ref: {relative}: {target}")
            continue
        action, ref = target.rsplit("@", 1)
        if not ACTION_SHA_RE.fullmatch(ref):
            fail(errors, f"external action must be pinned to a full commit SHA: {relative}: {target}")
        if action == "actions/checkout":
            checkout_count += 1

    if checkout_count and text.count("persist-credentials: false") < checkout_count:
        fail(errors, f"every checkout must disable persisted credentials: {relative}")

    if re.search(r"(?ms)^\s{2}push:\s*\n\s{4}branches:\s*\[Local\]", text):
        fail(errors, f"ordinary Local push must not trigger durable CI workflow: {relative}")
    if re.search(r"(?ms)^\s{2}push:\s*\n\s{4}branches:\s*\n\s{6}-\s*Local\s*$", text):
        fail(errors, f"ordinary Local push must not trigger durable CI workflow: {relative}")


def main() -> int:
    errors: list[str] = []

    for relative in REQUIRED_FILES:
        read_text(relative, errors)

    for relative in FORBIDDEN_RETIRED_PATHS:
        if (ROOT / relative).exists():
            fail(errors, f"retired/duplicate operational path must stay removed: {relative}")

    workflows = ROOT / ".github" / "workflows"
    if workflows.is_dir():
        for workflow in sorted(workflows.iterdir()):
            if not workflow.is_file():
                continue
            if workflow.name.lower().startswith(("temp", "_temp")):
                fail(errors, f"temporary workflow must not live in the durable workflow directory: {workflow.name}")
                continue
            if workflow.suffix in {".yml", ".yaml"}:
                verify_workflow_security(workflow, errors)

    agents = read_text("AGENTS.md", errors)
    github_rules = read_text("GITHUB_RULES.md", errors)
    docs_entry = read_text("docs/README.md", errors)
    system_doc = read_text("docs/04-system/README.md", errors)
    verification_doc = read_text("docs/05-operations/current-verification.md", errors)
    operations_readme = read_text("docs/05-operations/README.md", errors)
    project_map = read_text("docs/05-operations/project-map.md", errors)
    root_readme = read_text("README.md", errors)
    deployment_guide = read_text("docs/05-operations/deployment-v1.md", errors)
    client_proof_guide = read_text("docs/05-operations/client-implementation-proof-handoff.md", errors)
    launcher_guide = read_text("apps/launcher/README.md", errors)
    paper_world_guide = read_text("plugins/world-manager/README.md", errors)
    world_lifecycle_guide = read_text("docs/02-world-management/manage-world.md", errors)
    world_conversion_guide = read_text("docs/02-world-management/conversion.md", errors)
    world_product_guide = read_text("docs/02-world-management/README.md", errors)
    world_delete_service = read_text(
        "plugins/world-manager/src/main/java/com/halokaryamedia/lazybuilder/world/application/WorldDeleteService.java",
        errors,
    )
    conversion_update_policy = read_text(
        "plugins/world-manager/src/main/java/com/halokaryamedia/lazybuilder/world/conversion/ConversionRuntimePolicy.java",
        errors,
    )
    map_action_protocol = read_text(
        "shared/protocol/src/main/java/com/halokaryamedia/lazybuilder/world/map/MapActionWireProtocol.java",
        errors,
    )
    scalability_contract = read_text("docs/launcher-scalability-contract.json", errors)
    mob_config = read_text("plugins/world-manager/src/main/resources/config.yml", errors)
    layout_doc = read_text("docs/04-system/repository-layout.md", errors)
    product_doc = read_text("docs/01-product/README.md", errors)
    module_boundaries = read_text("docs/04-system/module-boundaries.md", errors)
    client_architecture = read_text("docs/04-system/client-manager-architecture-lock.md", errors)
    stable_context = read_text("CONTEXT.md", errors)
    root_pom = read_text("pom.xml", errors)
    builder_manifest = read_text("mods/builder-utilities/src/main/resources/fabric.mod.json", errors)
    builder_properties = read_text("mods/builder-utilities/gradle.properties", errors)
    builder_compatibility = read_text(
        "mods/builder-utilities/src/main/java/com/halokaryamedia/lazybuilder/builder/axiom/AxiomCompatibility.java",
        errors,
    )
    builder_workflow = read_text(".github/workflows/builder-verify.yml", errors)
    builder_benchmark_workflow = read_text(".github/workflows/builder-benchmark.yml", errors)
    discipline = read_text("docs/04-system/development-discipline.md", errors)
    routing = read_text("docs/04-system/skill-routing.md", errors)
    operations = read_text("docs/04-system/development-operations.md", errors)
    dev_orchestrator = read_text("tooling/windows-toolchain/dev.ps1", errors)
    fabric_verifier = read_text("tooling/windows-toolchain/scripts/verify/verify-fabric.ps1", errors)
    client_artifact_verifier = read_text(
        "tooling/windows-toolchain/scripts/verify/verify-client-artifacts.ps1",
        errors,
    )
    verify_workflow = read_text(".github/workflows/verify.yml", errors)
    build_local = read_text("apps/launcher/build-local.ps1", errors)
    test_local = read_text("tooling/windows-toolchain/scripts/verify/test-local.ps1", errors)
    publisher = read_text("tooling/windows-toolchain/scripts/distribution/package-local.ps1", errors)
    safe_artifact_name = read_text(
        "plugins/world-manager/src/main/java/com/halokaryamedia/lazybuilder/world/files/SafeArtifactName.java",
        errors,
    )
    transfer_sessions = read_text(
        "plugins/world-manager/src/main/java/com/halokaryamedia/lazybuilder/world/transfer/TransferSessionService.java",
        errors,
    )
    import_artifacts = read_text(
        "plugins/world-manager/src/main/java/com/halokaryamedia/lazybuilder/world/files/LocalWorldImportArtifactStore.java",
        errors,
    )
    export_artifacts = read_text(
        "plugins/world-manager/src/main/java/com/halokaryamedia/lazybuilder/world/files/LocalWorldExportArtifactStore.java",
        errors,
    )
    export_service = read_text(
        "plugins/world-manager/src/main/java/com/halokaryamedia/lazybuilder/world/application/WorldExportService.java",
        errors,
    )
    local_world_file_repository = read_text(
        "plugins/world-manager/src/main/java/com/halokaryamedia/lazybuilder/world/files/LocalWorldFileRepository.java",
        errors,
    )
    world_registry_persistence = read_text(
        "plugins/world-manager/src/main/java/com/halokaryamedia/lazybuilder/world/registry/YamlWorldRegistryPersistence.java",
        errors,
    )
    conversion_runtime_store = read_text(
        "plugins/world-manager/src/main/java/com/halokaryamedia/lazybuilder/world/conversion/LocalConversionRuntimeStore.java",
        errors,
    )
    world_record = read_text(
        "plugins/world-manager/src/main/java/com/halokaryamedia/lazybuilder/world/registry/WorldRecord.java",
        errors,
    )
    world_registry = read_text(
        "plugins/world-manager/src/main/java/com/halokaryamedia/lazybuilder/world/registry/WorldRegistry.java",
        errors,
    )
    world_control_protocol = read_text(
        "shared/protocol/src/main/java/com/halokaryamedia/lazybuilder/world/control/WorldControlWireProtocol.java",
        errors,
    )
    world_operation_type = read_text(
        "plugins/world-manager/src/main/java/com/halokaryamedia/lazybuilder/world/application/WorldOperationType.java",
        errors,
    )
    world_settings_service = read_text(
        "plugins/world-manager/src/main/java/com/halokaryamedia/lazybuilder/world/application/WorldSettingsService.java",
        errors,
    )
    world_registry_transactions = read_text(
        "plugins/world-manager/src/main/java/com/halokaryamedia/lazybuilder/world/registry/WorldRegistryTransactions.java",
        errors,
    )
    world_creation_service = read_text(
        "plugins/world-manager/src/main/java/com/halokaryamedia/lazybuilder/world/application/WorldCreationService.java",
        errors,
    )
    world_delete_service = read_text(
        "plugins/world-manager/src/main/java/com/halokaryamedia/lazybuilder/world/application/WorldDeleteService.java",
        errors,
    )
    world_duplicate_service = read_text(
        "plugins/world-manager/src/main/java/com/halokaryamedia/lazybuilder/world/application/WorldDuplicateService.java",
        errors,
    )
    world_import_service = read_text(
        "plugins/world-manager/src/main/java/com/halokaryamedia/lazybuilder/world/application/WorldImportService.java",
        errors,
    )
    world_lifecycle_service = read_text(
        "plugins/world-manager/src/main/java/com/halokaryamedia/lazybuilder/world/application/WorldLifecycleService.java",
        errors,
    )
    world_task_runner = read_text(
        "plugins/world-manager/src/main/java/com/halokaryamedia/lazybuilder/world/task/WorldTaskRunner.java",
        errors,
    )
    paper_dispatcher = read_text(
        "plugins/world-manager/src/main/java/com/halokaryamedia/lazybuilder/world/paper/PaperMainThreadDispatcher.java",
        errors,
    )
    heavy_operation_orchestrator = read_text(
        "plugins/world-manager/src/main/java/com/halokaryamedia/lazybuilder/world/paper/WorldHeavyOperationOrchestrator.java",
        errors,
    )
    paper_local_control = read_text(
        "plugins/world-manager/src/main/java/com/halokaryamedia/lazybuilder/world/paper/PaperLocalControlServer.java",
        errors,
    )
    world_backup_service = read_text(
        "plugins/world-manager/src/main/java/com/halokaryamedia/lazybuilder/world/application/WorldBackupService.java",
        errors,
    )

    for phrase in REQUIRED_AGENT_PHRASES:
        if phrase not in agents:
            fail(errors, f"AGENTS.md missing canonical routing marker: {phrase}")

    for phrase in REQUIRED_GITHUB_RULE_PHRASES:
        if phrase not in github_rules:
            fail(errors, f"GITHUB_RULES.md missing Actions supply-chain marker: {phrase}")

    for phrase in REQUIRED_DOC_ENTRY_PHRASES:
        if phrase not in docs_entry:
            fail(errors, f"docs/README.md missing context-economy marker: {phrase}")

    for phrase in REQUIRED_SYSTEM_DOC_PHRASES:
        if phrase not in system_doc:
            fail(errors, f"system documentation missing evidence-boundary marker: {phrase}")

    # The canonical repository layout must not resurrect retired root entrypoints
    # or hide the separate Builder extension and repository-owned toolchain.
    for path in ("DEV.cmd", "mods/builder-utilities/", "tooling/windows-toolchain/", "GITHUB_RULES.md"):
        if path not in layout_doc:
            fail(errors, f"repository layout omits canonical source/entrypoint: {path}")
    for obsolete in ("BUILD-LAUNCHER.cmd", "UPDATE-LAUNCHER.cmd"):
        if obsolete in layout_doc:
            fail(errors, f"repository layout still references retired root entrypoint: {obsolete}")

    for phrase in REQUIRED_DISCIPLINE_PHRASES:
        if phrase not in discipline:
            fail(errors, f"development discipline missing contract section/marker: {phrase}")

    for phrase in REQUIRED_OPERATIONS_PHRASES:
        if phrase not in operations:
            fail(errors, f"development operations missing canonical marker: {phrase}")

    # Active operations must route through the existing developer shim.
    # Retired names remain historical Git records, not current instructions.
    if "DEV.cmd" not in operations_readme or "tooling/windows-toolchain/dev.ps1" not in operations_readme:
        fail(errors, "current operations must route developer commands through DEV.cmd")
    for retired in ("CHECK-DEV.cmd", "SETUP-DEV.cmd", "BUILD-LAUNCHER.cmd", "UPDATE-LAUNCHER.cmd"):
        if retired in operations_readme:
            fail(errors, f"current operations still advertises retired command: {retired}")
    if "## Current continuation phase" in stable_context:
        fail(errors, "CONTEXT.md must not duplicate current operations/work-state authority")
    if "ChatGPT source-work boundary" not in verification_doc:
        fail(errors, "verification authority must distinguish remote source completion from native acceptance")

    # The overview is one derived graph, not a second phase/status database.
    for label, content, link in (
        ("root README", root_readme, "docs/05-operations/project-map.md"),
        ("documentation entry", docs_entry, "05-operations/project-map.md"),
        ("operations entry", operations_readme, "(project-map.md)"),
    ):
        if link not in content:
            fail(errors, f"{label} does not link the single canonical project map")
    if project_map.count("```mermaid") != 2:
        fail(errors, "project map requires exactly two product/dependency graphs")
    for marker in (
        "## 1. Product topology",
        "## 2. Work breakdown structure (WBS)",
        "## 3. Dependency graph",
        "## 4. What does",
        "## 5. Standard progress answer",
        "G0", "S1", "S2", "S3", "S4", "V1", "V2", "G1", "A1", "R1", "F0",
        "PAUSED", "UNKNOWN / NOT EXECUTED", "current-verification.md",
    ):
        if marker not in project_map:
            fail(errors, f"project map lost required owner/gate marker: {marker}")
    if re.search(r"(?m)^- \[[ xX]\]", project_map):
        fail(errors, "project map must not become a second mutable checklist")
    if re.search(r"(?i)\b[0-9a-f]{40}\b", project_map):
        fail(errors, "project map must not pin the supposedly current SHA")
    map_folder = ROOT / "docs" / "05-operations"
    for destination in re.findall(r"\[[^\]]+\]\(([^)]+)\)", project_map):
        link = destination.split("#", 1)[0]
        if not link or "://" in link:
            continue
        target = (map_folder / link).resolve()
        if not target.is_relative_to(ROOT.resolve()) or not target.is_file():
            fail(errors, f"project map link does not resolve: {destination}")

    # Current guides are required to agree with their implementation owners.
    # Historical snapshots are not rewritten and cannot override these contracts.
    control_version = re.search(r"(?m)^\s*public static final int VERSION = (\d+);", world_control_protocol)
    map_version = re.search(r"(?m)^\s*public static final int VERSION = (\d+);", map_action_protocol)
    if not control_version or not map_version:
        fail(errors, "Canonical Minecraft protocol versions are not readable from source")
    else:
        expected_pair = f"World Control V{control_version.group(1)} / Map Action V{map_version.group(1)}"
        for relative, content in (
            ("apps/launcher/README.md", launcher_guide),
            ("docs/05-operations/client-implementation-proof-handoff.md", client_proof_guide),
        ):
            if "World Control V" not in content or "Map Action V" not in content:
                fail(errors, f"Current guide must identify both canonical protocols: {relative}")
            elif (f"World Control V{control_version.group(1)}" not in content
                  or f"Map Action V{map_version.group(1)}" not in content):
                fail(errors, f"Current guide disagrees with shared protocol source: {relative}")

    default_conversion = re.search(
        r"(?s)static ConversionRuntimePolicy defaults\(\).*?UpdateMode\.([A-Z_]+),",
        conversion_update_policy,
    )
    if not default_conversion:
        fail(errors, "Canonical conversion update default is not readable from source")
    elif f"`{default_conversion.group(1)}`" not in paper_world_guide:
        fail(errors, "World-Manager guide conversion default differs from current source")
    if "enabled-worlds: []" not in mob_config or "enabled-worlds: []" not in paper_world_guide:
        fail(errors, "Aggressive mob policy must remain visibly opt-in per managed world")

    # Keep canonical World Manager product semantics and detailed contract in
    # lockstep with current service owners, rather than relying on stale terms.
    for marker in ("## Product goal and scope (WM-01)", "## Duplicate", "## Delete", "## Archive / Restore"):
        if marker not in world_lifecycle_guide:
            fail(errors, f"World lifecycle guide missing current owner: {marker}")
    for retired in ("## Clone", "acquire CLONE lease", "autoLoad=false", "Auto Load OFF"):
        if retired in world_lifecycle_guide:
            fail(errors, f"World lifecycle guide retains retired contract: {retired}")
    if 'world.displayName().equals(typedDisplayName)' not in world_delete_service:
        fail(errors, "Delete confirmation semantics changed; reconcile World Manager contract")
    if "visible world display name" not in world_lifecycle_guide:
        fail(errors, "Delete guide must require the canonical display name, not folder identity")
    if "WM-01 goal" not in world_product_guide:
        fail(errors, "World Manager product goal must remain explicit in its owning README")
    # Import replay and Desktop discard must not lose the same request's
    # ownership guard merely because an unrelated task becomes terminal.
    paper_local_control_import = paper_local_control.split(
        "private synchronized void handleImportTaskStart(", 1
    )[-1].split("private void handleDeleteTaskStart(", 1)[0]
    if "registry.findByImportRequestId(entry.getKey())" not in paper_local_control_import:
        fail(errors, "Import retirement requires canonical durable world provenance")
    if "WorldTaskState.SUCCEEDED" not in paper_local_control_import:
        fail(errors, "Import retirement must preserve active/failed task admissions")
    if "importAdmissions.remove(removable)" not in paper_local_control_import:
        fail(errors, "Completed requests need bounded safe retirement, not unbounded blocking")
    if "Import admission history is full; unresolved requests require reconciliation" not in paper_local_control_import:
        fail(errors, "Import admission capacity must fail closed when no retired candidate is proven")
    if "importAdmissions.values().stream()" not in paper_local_control:
        fail(errors, "Desktop discard must consult the accepted-import ownership history")
    if "private synchronized void handleImportTaskStart(" not in paper_local_control:
        fail(errors, "Desktop import admission must remain serialized against discard")
    if "synchronized (this)" not in paper_local_control or "importUploads.discardUnclaimed(artifactName)" not in paper_local_control:
        fail(errors, "Desktop discard must serialize the ownership check with the transfer discard")
    replay_at = paper_local_control_import.find("ImportAdmission previous = importAdmissions.get(requestId);")
    claim_at = paper_local_control_import.find(
        "importUploads.claimForImport(artifactName, requestId, destinationFolder, displayName)"
    )
    submit_at = paper_local_control_import.find("queued = taskRunner.submit(")
    retain_at = paper_local_control_import.find("importAdmissions.put(requestId,")
    if min(replay_at, claim_at, submit_at, retain_at) < 0 or not (replay_at < claim_at < submit_at < retain_at):
        fail(errors, "Import admission must replay before claim and record only accepted tasks")
    if "MAX_IMPORT_ADMISSIONS = WorldTaskRegistry.DEFAULT_HISTORY_LIMIT" not in paper_local_control:
        fail(errors, "Import admission capacity must remain bounded to the canonical task history")
    if "Import request ID reused with different parameters" not in paper_local_control_import:
        fail(errors, "Import request replay must reject mismatched parameters")
    if "importUploads.returnForRetry(artifactName)" not in paper_local_control_import:
        fail(errors, "Import task failure must retain its source")
    if "importUploads.cancelUnacceptedImport(artifactName, requestId)" not in paper_local_control_import:
        fail(errors, "Rejected task admission must undo its durable intent")
    if "registry.findByImportRequestId(requestId)" not in paper_local_control_import:
        fail(errors, "Import retry must consult the durable world registry")
    if "claimCompletedUploadForImport(" not in transfer_sessions:
        fail(errors, "Desktop admission must bind request identity to the transfer marker")


    if default_conversion:
        mode = default_conversion.group(1)
        if f"`{mode}`" not in world_conversion_guide:
            fail(errors, "Detailed conversion guide disagrees with source default")
        if mode == "AUTOMATIC_STABLE" and "mode             = notify only" in world_conversion_guide:
            fail(errors, "Detailed conversion guide retains obsolete notify-only default")
    if "WorldImportService" not in world_conversion_guide or "source uploaded artifact" not in world_conversion_guide:
        fail(errors, "Conversion guide must retain current shared import artifact ownership")

    try:
        maximum_servers = json.loads(scalability_contract)["maxConcurrentServers"]
    except (ValueError, KeyError, TypeError) as exception:
        fail(errors, f"Invalid canonical Launcher scalability contract: {exception}")
    else:
        if maximum_servers != 3 or f"up to {maximum_servers}" not in deployment_guide:
            fail(errors, "Deployment guide must match canonical concurrent server capacity")
    if "source remediation and synchronization" not in deployment_guide:
        fail(errors, "Deployment guide must not claim target-machine acceptance is active")
    for marker in (
        "mods/performance-manager",
        "Map Manager + Utility Manager + Performance Manager",
        "three required JARs",
    ):
        if marker not in client_proof_guide:
            fail(errors, f"Current client proof guide omits three-manager V1 contract: {marker}")
    for obsolete in (
        "only Map Manager + Utility Manager are required",
        "two required JARs",
        "two-component publish",
        "only those two V1 components",
    ):
        if obsolete in client_proof_guide:
            fail(errors, f"Current client proof guide contains retired two-manager instruction: {obsolete}")

    for marker in REQUIRED_DEV_FAILURE_MARKERS:
        if marker not in dev_orchestrator:
            fail(errors, f"developer orchestrator missing actionable failure marker: {marker}")

    for marker in REQUIRED_DEV_VERIFY_MARKERS:
        if marker not in dev_orchestrator:
            fail(errors, f"developer orchestrator missing bounded verify marker: {marker}")

    for marker in FORBIDDEN_DEV_VERIFY_MARKERS:
        if marker in dev_orchestrator.lower():
            fail(errors, f"developer orchestrator must not add an overlapping integrated verify command: {marker}")

    core_managers = ("mods/map-manager", "mods/utility-manager", "mods/performance-manager")
    for manager in core_managers:
        if manager not in fabric_verifier or "--no-daemon build" not in fabric_verifier:
            fail(errors, f"canonical Fabric verification lane missing manager: {manager}")

    if "mods/builder-utilities" in fabric_verifier:
        fail(errors, "default Fabric verification must not treat Builder Utilities as a core manager")

    for retired_path in (
        "mods/terraform-manager",
        "plugins/terraform-manager",
        "shared/terraform-core",
        "shared/protocol/src/main/java/com/halokaryamedia/lazybuilder/terraform",
        "tooling/windows-toolchain/scripts/verify/prepare-terraform-local.ps1",
        "docs/04-system/terraform-manager-architecture-lock.md",
    ):
        if (ROOT / retired_path).exists():
            fail(errors, f"retired Terraform source/tooling must not return to active repository ownership: {retired_path}")
    if "legacy-terraform" in root_pom or "terraform-core" in root_pom or "plugins/terraform-manager" in root_pom:
        fail(errors, "root Maven reactor must not retain the retired Terraform build profile")

    if "lazybuilder-builder-utilities-" not in build_local or "lazybuilder-terraform-manager-" not in build_local:
        fail(errors, "Launcher build staging must explicitly purge non-core LazyBuilder client JARs before packaging")
    if "Copy-Item $Builder" in build_local or "Copy-Item $Terraform" in build_local:
        fail(errors, "Launcher runtime-ready build must not stage Builder/Terraform artifacts into V1 Client Setup")
    if "lazybuilder-builder-utilities-" in verify_workflow:
        fail(errors, "integrated Verify must not stage Builder Utilities into the V1 core client artifact")
    if "lazybuilder-terraform-manager-" in verify_workflow:
        fail(errors, "Terraform legacy lane must not enter the integrated V1 core artifact path")
    if "$BuilderExpected" not in client_artifact_verifier or "$CoreExpected" not in client_artifact_verifier:
        fail(errors, "client artifact verifier must keep core and Builder extension artifact sets separate")

    architecture_contracts = {
        "docs/01-product/README.md": product_doc,
        "docs/04-system/module-boundaries.md": module_boundaries,
        "docs/04-system/client-manager-architecture-lock.md": client_architecture,
        "docs/05-operations/current-verification.md": verification_doc,
        "CONTEXT.md": stable_context,
    }
    for relative, content in architecture_contracts.items():
        if "Builder Utilities" not in content:
            fail(errors, f"canonical architecture doc missing Builder Utilities lane: {relative}")
        if "Axiom" not in content:
            fail(errors, f"canonical architecture doc missing Axiom ownership boundary: {relative}")

    if "Builder Utilities is intentionally separate from Client Setup" not in stable_context:
        fail(errors, "CONTEXT.md must keep Builder Utilities outside the required Client Setup bundle")
    if "not a fourth core Manager" not in product_doc:
        fail(errors, "product authority must distinguish Builder Utilities from the three core Managers")
    if "retired" not in module_boundaries.lower() or "git history" not in module_boundaries.lower():
        fail(errors, "module boundaries must record Terraform retirement and Git-history archival")
    axiom_range_match = re.search(r"(?m)^axiom_supported_range=(.+)$", builder_properties)
    if not axiom_range_match:
        fail(errors, "Builder Utilities must define axiom_supported_range in gradle.properties")
    else:
        axiom_range = axiom_range_match.group(1).strip()
        if "${axiom_supported_range}" not in builder_manifest:
            fail(errors, "fabric.mod.json must consume the canonical axiom_supported_range property")
        if f'SUPPORTED_RANGE = "{axiom_range}"' not in builder_compatibility:
            fail(errors, "Axiom runtime diagnostics must match the canonical supported range")
        if axiom_range not in stable_context:
            fail(errors, "CONTEXT.md must state the canonical Axiom compatibility range")
        if "axiom_supported_range" not in client_artifact_verifier:
            fail(errors, "client artifact verifier must read the canonical Axiom range")
        if "$AxiomSupportedRange" not in client_artifact_verifier:
            fail(errors, "client artifact verifier must compare packaged metadata to the canonical Axiom range")
    if "mods/builder-utilities" not in builder_workflow:
        fail(errors, "Builder verification workflow must target the Builder Utilities source owner")
    if "benchmarkGolden" not in builder_benchmark_workflow:
        fail(errors, "Builder Benchmark workflow must run the canonical benchmarkGolden task")
    if "not Minecraft runtime proof" not in builder_benchmark_workflow:
        fail(errors, "Builder Benchmark workflow must preserve the synthetic-proof boundary")

    if "manual-only" not in verification_doc.lower() or "workflow_dispatch" not in verification_doc:
        fail(errors, "current verification authority must document evidence-only scoping and manual full Verify")

    if "paths-ignore:" in verify_workflow:
        fail(errors, "manual Verify must not retain dead automatic-event path filters")

    if "verify-fabric.ps1 -RepoRoot" not in verify_workflow:
        fail(errors, "Verify workflow must reuse the canonical Fabric verification lane")
    if "npm run verify:source" not in verify_workflow:
        fail(errors, "Verify workflow must reuse the canonical Launcher source verification lane")
    if "$serverDownload.checksums.sha256" not in verify_workflow:
        fail(errors, "Paper runtime proof must read the SHA-256 published by PaperMC")
    if "Get-FileHash -Path $paperPath -Algorithm SHA256" not in verify_workflow:
        fail(errors, "Paper runtime proof must verify the downloaded Paper JAR before execution")

    if "dist\\LazyBuilder" in build_local or "dist\\LazyBuilder" in test_local:
        fail(errors, "legacy dist/LazyBuilder Local output path must not return")
    if "dist\\Local" not in build_local or "dist\\Local" not in test_local:
        fail(errors, "canonical Local build/test paths must resolve through dist/Local")

    if "& $FabricVerifier -RepoRoot $RepoRoot" not in build_local:
        fail(errors, "Launcher local build must delegate Fabric verification to verify-fabric.ps1")

    publisher_markers = [
        "dist\\Local",
        "LazyBuilder-Setup-Local.exe",
        "LazyBuilder-Diagnostics.exe",
        "build-info.json",
        "SHA256SUMS.txt",
    ]
    for marker in publisher_markers:
        if marker not in publisher:
            fail(errors, f"canonical Local publisher missing package marker: {marker}")

    if "$PackageLocal" not in build_local or "& $PackageLocal" not in build_local:
        fail(errors, "Launcher local build must delegate runtime-ready publication to package-local.ps1")

    for marker in (
        "name.indexOf(':') >= 0",
        'name.endsWith(".")',
        'name.endsWith(" ")',
        "CONIN$",
        "CONOUT$",
        "MAX_FILE_NAME_CHARS",
    ):
        if marker not in safe_artifact_name:
            fail(errors, f"portable artifact filename policy missing safety marker: {marker}")

    for relative, content in (
        ("TransferSessionService", transfer_sessions),
        ("LocalWorldImportArtifactStore", import_artifacts),
        ("LocalWorldExportArtifactStore", export_artifacts),
        ("WorldExportService", export_service),
    ):
        if "SafeArtifactName.requirePortable" not in content:
            fail(errors, f"{relative} must delegate artifact filename validation to SafeArtifactName")

    for marker in (
        "public synchronized List<WorldRecord> load()",
        "public synchronized void save(List<WorldRecord> worlds)",
    ):
        if marker not in world_registry_persistence:
            fail(errors, f"World registry persistence serialization contract missing marker: {marker}")

    for marker in (
        "synchronized (persistence)",
        "synchronized (registry)",
        "registry.register(world)",
        "registry.updateMetadata(updated)",
        "registry.remove(worldId)",
        "persistence.save(registry.all())",
    ):
        if marker not in world_registry_transactions:
            fail(errors, f"World registry atomic transaction contract missing marker: {marker}")

    for relative, content in (
        ("WorldCreationService", world_creation_service),
        ("WorldDeleteService", world_delete_service),
        ("WorldDuplicateService", world_duplicate_service),
        ("WorldImportService", world_import_service),
        ("WorldLifecycleService", world_lifecycle_service),
        ("WorldSettingsService", world_settings_service),
    ):
        if "WorldRegistryTransactions." not in content:
            fail(errors, f"{relative} must use the canonical durable registry transaction owner")
        if "persistence.save(" in content:
            fail(errors, f"{relative} must not bypass WorldRegistryTransactions with a raw persistence save")

    for marker in (
        "markCreateRuntimeCreated(operationId, record.folderName())",
    ):
        if marker not in world_creation_service:
            fail(errors, f"Create runtime ownership transition missing marker: {marker}")

    for marker in (
        'CREATE_STATE_INTENT = "INTENT"',
        'CREATE_STATE_RUNTIME_CREATED = "RUNTIME_CREATED"',
        "readCreateMarker(marker)",
        "markerState != CreateMarkerState.RUNTIME_CREATED",
        "channel.force(true)",
    ):
        if marker not in local_world_file_repository:
            fail(errors, f"Create recovery ownership proof missing marker: {marker}")

    for marker in (
        "MAX_REGISTRY_BYTES",
        "recoverInterruptedPublish()",
        'getFileName() + ".previous"',
        'getFileName() + ".tmp"',
        "channel.force(true)",
        "recovery evidence is unresolved",
        'SCHEMA_PATH = "schema-version"',
        "SCHEMA_VERSION = 1",
        "schema-version must be an integer",
        "missing the required worlds section",
    ):
        if marker not in world_registry_persistence:
            fail(errors, f"World registry crash-safety/schema contract missing marker: {marker}")

    for marker in (
        "verifyArtifactDigest",
        "requireManifestCompatibility",
        "ConverterAdapter.ADAPTER_CONTRACT",
        "Conversion runtime SHA-256 mismatch",
        "requireVerified(current)",
        "requireVerified(candidate)",
        "requireVerified(previous)",
    ):
        if marker not in conversion_runtime_store:
            fail(errors, f"conversion runtime persisted-trust contract missing marker: {marker}")

    for marker in (
        "MAX_FOLDER_NAME_CHARS = 255",
        "MAX_DISPLAY_NAME_CHARS = 256",
        "displayName must not contain control characters",
        "folderName exceeds the ",
        "displayName exceeds the ",
    ):
        if marker not in world_record:
            fail(errors, f"durable world-name validation contract missing marker: {marker}")

    if "MAX_MANAGED_WORLDS = 4_096" not in world_registry:
        fail(errors, "WorldRegistry must preserve the managed-world capacity ceiling")
    if "MAX_WORLDS = 4_096" not in world_control_protocol:
        fail(errors, "World-control protocol capacity must match the managed-world ceiling")

    if "SETTINGS" not in world_operation_type:
        fail(errors, "World operation coordinator must preserve SETTINGS exclusivity")
    for marker in (
        "operations.acquire(worldId, WorldOperationType.SETTINGS)",
        "runtimeService.loadDuringOperation(worldId)",
        "withSettingsLease",
    ):
        if marker not in world_settings_service:
            fail(errors, f"World Settings concurrency contract missing marker: {marker}")

    for marker in (
        "applyBatch(",
        "rollbackBatchRuntime",
        "restoreGameRule",
        "originalRuntime = runtime.readSettings",
    ):
        if marker not in world_settings_service:
            fail(errors, f"World Settings batch rollback contract missing marker: {marker}")

    for marker in (
        "queuedOrRunningWorlds",
        "reserveWorld(worldId)",
        "releaseWorld(worldId)",
        "World already has a queued or running task",
    ):
        if marker not in world_task_runner:
            fail(errors, f"World task queue-admission contract missing marker: {marker}")

    for marker in (
        "submitTracked",
        "cancelBeforeStart",
        "DispatchState.QUEUED",
        "DispatchState.RUNNING",
        "DispatchState.CANCELLED",
        "compareAndSet(DispatchState.QUEUED",
        "dispatch.outcome",
        "awaitAlreadyStarted",
    ):
        if marker not in paper_dispatcher:
            fail(errors, f"Paper dispatch ownership contract missing marker: {marker}")

    for marker in (
        "String finalMessage = registry.find(taskId)",
        "WorldTaskSnapshot::message",
        "registry.succeed(taskId, result == null ? \"\" : result, finalMessage)",
    ):
        if marker not in world_task_runner:
            fail(errors, f"World task committed-result messaging contract missing marker: {marker}")

    for marker in (
        "task.committed() && task.closed()",
        "task.completed() && task.closed()",
        "source runtime restoration failed; check the source world state",
    ):
        if marker not in heavy_operation_orchestrator:
            fail(errors, f"Heavy-operation committed outcome contract missing marker: {marker}")

    for marker in (
        "backupTask.committed() && backupTask.closed()",
        "Backup committed, but source runtime restoration failed",
    ):
        if marker not in paper_local_control:
            fail(errors, f"Backup committed outcome contract missing marker: {marker}")

    for marker in (
        "task.cleanupWorkspace = staged",
        "if (task.cleanupWorkspace != null)",
        "files.deleteWorkspace(task.cleanupWorkspace)",
    ):
        if marker not in world_backup_service:
            fail(errors, f"Backup failure/commit cleanup retry contract missing marker: {marker}")

    skills_root = ROOT / ".agents" / "skills"
    if not skills_root.is_dir():
        fail(errors, "missing .agents/skills directory")
        discovered: set[str] = set()
    else:
        discovered = set()
        for child in skills_root.iterdir():
            if not child.is_dir():
                continue
            skill_file = child / "SKILL.md"
            if not skill_file.is_file():
                fail(errors, f"skill directory has no SKILL.md: {child.relative_to(ROOT)}")
                continue
            declared = skill_name(skill_file)
            if not declared:
                fail(errors, f"skill has no frontmatter name: {skill_file.relative_to(ROOT)}")
                continue
            if declared != child.name:
                fail(errors, f"skill directory/name mismatch: {child.name} declares {declared}")
            discovered.add(declared)

    missing_skills = EXPECTED_SKILLS - discovered
    unexpected_forbidden = FORBIDDEN_STANDALONE_SKILLS & discovered
    if missing_skills:
        fail(errors, f"missing canonical skills: {', '.join(sorted(missing_skills))}")
    if unexpected_forbidden:
        fail(errors, "forbidden implementation/meta skills found: " + ", ".join(sorted(unexpected_forbidden)))

    for skill in sorted(EXPECTED_SKILLS):
        if skill not in agents:
            fail(errors, f"AGENTS.md does not route canonical skill: {skill}")
        if skill not in routing:
            fail(errors, f"skill-routing.md does not describe canonical skill: {skill}")

    if "Do not create standalone Skills for Java, Rust, TypeScript, Maven, Gradle, Tauri, Svelte, testing, or CI alone." not in agents:
        fail(errors, "AGENTS.md must preserve the no-language/build-tool-skill rule")
    if "Do not create additional Skills merely for Java, Rust, TypeScript, Maven, Gradle, Tauri, Svelte, testing, CI" not in routing:
        fail(errors, "skill-routing.md must preserve the no-redundant-mechanic-skill rule")

    verify_agent_governance(ROOT, errors)

    if errors:
        print("Repository contract verification FAILED:", file=sys.stderr)
        for error in errors:
            print(f" - {error}", file=sys.stderr)
        return 1

    print("Repository contract verification PASS")
    print("Canonical skills:", ", ".join(sorted(EXPECTED_SKILLS)))
    print("Supporting evidence context: opt-in only")
    print("Targeted verify scopes: paper, fabric, launcher")
    print("GitHub Actions: workflow_dispatch-only + full-SHA pins + least privilege")
    print("Agent routing/permission fixtures: static contract evaluation")
    print("Full Verify: manually dispatched, no automatic path scoping")
    print("Developer failures: operation + exit code + evidence + recovery")
    print("Developer command surface: DEV.cmd -> tooling/windows-toolchain/dev.ps1")
    print("Local distribution owner: tooling/windows-toolchain/scripts/distribution/package-local.ps1")
    print("Local distribution path: dist/Local")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
