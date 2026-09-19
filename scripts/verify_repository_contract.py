#!/usr/bin/env python3
"""Verify LazyBuilder repository routing and canonical-owner contracts.

This intentionally checks durable repository invariants only. It does not try to
infer runtime correctness or replace domain tests.
"""

from __future__ import annotations

from pathlib import Path
import re
import sys

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
    "docs/04-system/development-operations.md",
    "docs/05-operations/current-verification.md",
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
]

REQUIRED_GITHUB_RULE_PHRASES = [
    "GitHub Actions supply-chain contract",
    "full 40-character commit SHA",
    "persist-credentials: false",
    "timeout-minutes",
    "contents: read",
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

REQUIRED_VERIFY_IGNORE_PATTERNS = [
    "docs/04-system/*-audit.md",
    "docs/04-system/*-lock.md",
    "docs/04-system/*-status.md",
    "docs/05-operations/*-testing-notes.md",
    "docs/05-operations/*-handoff.md",
    "docs/05-operations/remote-github-complete.md",
]

FORBIDDEN_BROAD_VERIFY_IGNORES = [
    "apps/**",
    "plugins/**",
    "mods/**",
    "shared/**",
    "tooling/**",
    "scripts/**",
    ".agents/**",
    "docs/**",
    "*.yml",
    "*.yaml",
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

    for phrase in REQUIRED_DISCIPLINE_PHRASES:
        if phrase not in discipline:
            fail(errors, f"development discipline missing contract section/marker: {phrase}")

    for phrase in REQUIRED_OPERATIONS_PHRASES:
        if phrase not in operations:
            fail(errors, f"development operations missing canonical marker: {phrase}")

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

    for development_lane in ("mods/builder-utilities", "mods/terraform-manager"):
        if development_lane in fabric_verifier:
            fail(errors, f"default Fabric verification must not treat development lane as core: {development_lane}")

    default_modules = root_pom.split("<profiles>", 1)[0]
    for legacy_module in ("shared/terraform-core", "plugins/terraform-manager"):
        if f"<module>{legacy_module}</module>" in default_modules:
            fail(errors, f"default Maven reactor must not include legacy Terraform module: {legacy_module}")
    if "<id>legacy-terraform</id>" not in root_pom:
        fail(errors, "root Maven reactor must preserve an explicit legacy-terraform profile")

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
    if "legacy/prototype" not in module_boundaries.lower():
        fail(errors, "module boundaries must classify Terraform as legacy/prototype rather than a parallel production owner")
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

    if "evidence-only" not in verification_doc.lower() or "workflow_dispatch" not in verification_doc:
        fail(errors, "current verification authority must document evidence-only scoping and manual full Verify")

    for pattern in REQUIRED_VERIFY_IGNORE_PATTERNS:
        if verify_workflow.count(pattern) < 2:
            fail(errors, f"Verify workflow must scope evidence-only path ignore for push and PR: {pattern}")

    for pattern in FORBIDDEN_BROAD_VERIFY_IGNORES:
        quoted_single = f"'{pattern}'"
        quoted_double = f'"{pattern}"'
        if quoted_single in verify_workflow or quoted_double in verify_workflow:
            fail(errors, f"Verify workflow must not broadly ignore source/canonical paths: {pattern}")

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
        "future.cancel(false)",
        "awaitAlreadyStarted",
        "Cancellation lost because Paper already started",
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
        "task.committed && task.cleanupWorkspace != null",
        "files.deleteWorkspace(task.cleanupWorkspace)",
    ):
        if marker not in world_backup_service:
            fail(errors, f"Backup post-commit cleanup retry contract missing marker: {marker}")

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

    if errors:
        print("Repository contract verification FAILED:", file=sys.stderr)
        for error in errors:
            print(f" - {error}", file=sys.stderr)
        return 1

    print("Repository contract verification PASS")
    print("Canonical skills:", ", ".join(sorted(EXPECTED_SKILLS)))
    print("Supporting evidence context: opt-in only")
    print("Targeted verify scopes: paper, fabric, launcher")
    print("GitHub Actions: full-SHA pins + read-only token + bounded job timeouts")
    print("Full Verify scoping: evidence-only exclusions, source/canonical paths protected")
    print("Developer failures: operation + exit code + evidence + recovery")
    print("Developer command surface: DEV.cmd -> tooling/windows-toolchain/dev.ps1")
    print("Local distribution owner: tooling/windows-toolchain/scripts/distribution/package-local.ps1")
    print("Local distribution path: dist/Local")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
