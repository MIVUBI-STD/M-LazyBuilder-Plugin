param(
    [switch]$SkipTests,
    [switch]$AllowMissingRuntime,
    [switch]$UpdateInstalled
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ($env:OS -ne 'Windows_NT') { throw 'LazyBuilder local desktop build must run on Windows.' }

$AppRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Resolve-Path (Join-Path $AppRoot '..\..')
$PreflightScript = Join-Path $RepoRoot 'tooling\windows-toolchain\scripts\build\preflight.ps1'
$FabricVerifier = Join-Path $RepoRoot 'tooling\windows-toolchain\scripts\verify\verify-fabric.ps1'
$ClientVerifier = Join-Path $RepoRoot 'tooling\windows-toolchain\scripts\verify\verify-client-artifacts.ps1'
$PackageLocal = Join-Path $RepoRoot 'tooling\windows-toolchain\scripts\distribution\package-local.ps1'
$MavenWrapper = Join-Path $RepoRoot 'mvnw.cmd'

foreach ($RequiredScript in @($PreflightScript, $FabricVerifier, $ClientVerifier, $PackageLocal)) {
    if (-not (Test-Path $RequiredScript -PathType Leaf)) { throw "Missing required build operation: $RequiredScript" }
}
if ($AllowMissingRuntime) { & $PreflightScript -RepoRoot $RepoRoot }
else { & $PreflightScript -RepoRoot $RepoRoot -RequireJava }

$CoreDir = Join-Path $AppRoot 'src-tauri\resources\core'
$ClientModsDir = Join-Path $AppRoot 'src-tauri\resources\client-mods'
$WorldJar = Join-Path $CoreDir 'World-Manager-0.1.0-SNAPSHOT.jar'
$UtilitiesJar = Join-Path $CoreDir 'Utilities-Manager-0.1.0-SNAPSHOT.jar'
$MapJar = Join-Path $ClientModsDir 'lazybuilder-map-manager-0.1.0-SNAPSHOT.jar'
$UtilityClientJar = Join-Path $ClientModsDir 'lazybuilder-utility-manager-0.1.0-SNAPSHOT.jar'
$PerformanceClientJar = Join-Path $ClientModsDir 'lazybuilder-performance-manager-0.1.0-SNAPSHOT.jar'
$WorldTargetJar = Join-Path $RepoRoot 'plugins\world-manager\target\World-Manager-0.1.0-SNAPSHOT.jar'
$UtilitiesTargetJar = Join-Path $RepoRoot 'plugins\utilities-manager\target\Utilities-Manager-0.1.0-SNAPSHOT.jar'
$MapTargetJar = Join-Path $RepoRoot 'mods\map-manager\build\libs\lazybuilder-map-manager-0.1.0-SNAPSHOT.jar'
$UtilityClientTargetJar = Join-Path $RepoRoot 'mods\utility-manager\build\libs\lazybuilder-utility-manager-0.1.0-SNAPSHOT.jar'
$PerformanceClientTargetJar = Join-Path $RepoRoot 'mods\performance-manager\build\libs\lazybuilder-performance-manager-0.1.0-SNAPSHOT.jar'
$LocalPublishDir = Join-Path $RepoRoot 'dist\Local'
$CompileOnlyDir = Join-Path $RepoRoot 'dist\CompileOnly'
$NsisDir = Join-Path $AppRoot 'src-tauri\target\release\bundle\nsis'

Write-Host ''
Write-Host $(if ($UpdateInstalled) { 'LazyBuilder Launcher - Clean Local Update' } else { 'LazyBuilder Launcher - Local Windows Build' }) -ForegroundColor Cyan
Write-Host "Repository: $RepoRoot"
Write-Host "Launcher:   $AppRoot"
Write-Host ''

if ($UpdateInstalled -and $AllowMissingRuntime) {
    throw 'Installed Launcher update is blocked in compile-only mode because the installed app must remain runtime-ready.'
}

if (-not $AllowMissingRuntime) {
    Write-Host '[runtime] Building and testing matching Paper plugins...' -ForegroundColor Cyan
    Push-Location $RepoRoot
    try {
        & $MavenWrapper --batch-mode --no-transfer-progress verify
        if ($LASTEXITCODE -ne 0) { throw "Maven verification failed with exit code $LASTEXITCODE." }
    }
    finally { Pop-Location }

    Write-Host '[runtime] Building required LazyBuilder Fabric client mods...' -ForegroundColor Cyan
    & $FabricVerifier -RepoRoot $RepoRoot
    if ($LASTEXITCODE -ne 0) { throw "Fabric verification failed with exit code $LASTEXITCODE." }

    $RequiredBuildOutputs = @(
        $WorldTargetJar,
        $UtilitiesTargetJar,
        $MapTargetJar,
        $UtilityClientTargetJar,
        $PerformanceClientTargetJar
    )
    foreach ($Output in $RequiredBuildOutputs) {
        if (-not (Test-Path $Output)) { throw "Runtime verification completed but required artifact was not found: $Output" }
    }

    New-Item -ItemType Directory -Force -Path $CoreDir | Out-Null
    New-Item -ItemType Directory -Force -Path $ClientModsDir | Out-Null

    foreach ($Prefix in @(
        'lazybuilder-map-manager-',
        'lazybuilder-utility-manager-',
        'lazybuilder-performance-manager-'
    )) {
        Get-ChildItem $ClientModsDir -Filter "$Prefix*.jar" -File -ErrorAction SilentlyContinue | Remove-Item -Force
    }

    Copy-Item $WorldTargetJar $WorldJar -Force
    Copy-Item $UtilitiesTargetJar $UtilitiesJar -Force
    Copy-Item $MapTargetJar $MapJar -Force
    Copy-Item $UtilityClientTargetJar $UtilityClientJar -Force
    Copy-Item $PerformanceClientTargetJar $PerformanceClientJar -Force

    Write-Host '[runtime] Verifying packaged Fabric client suite...' -ForegroundColor Cyan
    & $ClientVerifier -ClientModsDir $ClientModsDir -RepoRoot $RepoRoot

    Write-Host 'Matching tested server plugins and all three required client mods staged for the desktop package.' -ForegroundColor Green
    Write-Host ''
}

$MissingRuntime = @()
foreach ($Path in @($WorldJar, $UtilitiesJar, $MapJar, $UtilityClientJar, $PerformanceClientJar)) {
    if (-not (Test-Path $Path)) { $MissingRuntime += (Split-Path $Path -Leaf) }
}
if ($MissingRuntime.Count -gt 0) {
    $MissingText = $MissingRuntime -join ', '
    if (-not $AllowMissingRuntime) { throw "Runtime-ready Launcher build blocked: bundled runtime components are missing ($MissingText)." }
    Write-Warning "Compile-only mode: runtime components are missing ($MissingText)."
    Write-Warning 'The produced app must not be used for runtime or installer acceptance.'
    Write-Host ''
}

if ($UpdateInstalled) {
    $Running = @(Get-Process -Name 'lazybuilder' -ErrorAction SilentlyContinue)
    if ($Running.Count -gt 0) { throw 'LazyBuilder is currently running. Close the Launcher first, then run DEV.cmd update again.' }
}

if (Test-Path $NsisDir) { Get-ChildItem $NsisDir -Filter '*.exe' -File -ErrorAction SilentlyContinue | Remove-Item -Force }

Push-Location $AppRoot
try {
    Write-Host '[1/5] Installing exact frontend dependencies...' -ForegroundColor Cyan
    npm ci
    if ($LASTEXITCODE -ne 0) { throw "npm ci failed with exit code $LASTEXITCODE." }

    Write-Host '[2/5] Typechecking Svelte...' -ForegroundColor Cyan
    npm run typecheck
    if ($LASTEXITCODE -ne 0) { throw "Svelte typecheck failed with exit code $LASTEXITCODE." }

    Write-Host '[3/5] Checking Rust/Tauri backend...' -ForegroundColor Cyan
    npm run prepare:icons
    if ($LASTEXITCODE -ne 0) { throw "Icon preparation failed with exit code $LASTEXITCODE." }
    cargo check --locked --manifest-path src-tauri/Cargo.toml
    if ($LASTEXITCODE -ne 0) { throw "cargo check failed with exit code $LASTEXITCODE." }

    if (-not $SkipTests) {
        Write-Host '[4/5] Running Rust/Tauri tests...' -ForegroundColor Cyan
        cargo test --locked --manifest-path src-tauri/Cargo.toml
        if ($LASTEXITCODE -ne 0) { throw "cargo test failed with exit code $LASTEXITCODE." }
    } else {
        Write-Host '[4/5] Rust/Tauri tests skipped by request.' -ForegroundColor Yellow
    }

    Write-Host '[5/5] Building Windows Launcher package...' -ForegroundColor Cyan
    npx tauri build
    if ($LASTEXITCODE -ne 0) { throw "Tauri build failed with exit code $LASTEXITCODE." }

    $Exe = Join-Path $AppRoot 'src-tauri\target\release\lazybuilder.exe'
    if (-not (Test-Path $Exe)) { throw 'Tauri reported success but lazybuilder.exe was not found in the expected release directory.' }

    $Installers = @()
    if (Test-Path $NsisDir) { $Installers = @(Get-ChildItem $NsisDir -Filter '*-setup.exe' -File | Sort-Object LastWriteTime -Descending) }
    if ($Installers.Count -ne 1) { throw "Expected exactly one freshly built NSIS installer, found $($Installers.Count)." }
    $FreshInstaller = $Installers[0].FullName

    if ($UpdateInstalled) {
        Write-Host ''
        Write-Host 'Updating the installed LazyBuilder in place...' -ForegroundColor Cyan
        $UpdateProcess = Start-Process -FilePath $FreshInstaller -ArgumentList '/S' -Wait -PassThru
        if ($UpdateProcess.ExitCode -ne 0) { throw "LazyBuilder update installer exited with code $($UpdateProcess.ExitCode)." }
        Get-ChildItem $NsisDir -Filter '*.exe' -File -ErrorAction SilentlyContinue | Remove-Item -Force
        Write-Host ''
        Write-Host 'Installed Launcher updated successfully.' -ForegroundColor Green
        Write-Host 'Server workspaces, selected Modrinth profile, and LazyBuilder user data were preserved.'
        Write-Host 'Open LazyBuilder normally and continue local testing.' -ForegroundColor Cyan
        return
    }

    if ($MissingRuntime.Count -gt 0) {
        if (Test-Path $CompileOnlyDir) { Remove-Item $CompileOnlyDir -Recurse -Force }
        New-Item -ItemType Directory -Force -Path $CompileOnlyDir | Out-Null
        $DiagnosticExe = Join-Path $CompileOnlyDir 'LazyBuilder-Diagnostics.exe'
        Copy-Item $Exe $DiagnosticExe -Force
        Set-Content -Path (Join-Path $CompileOnlyDir 'README.txt') -Encoding UTF8 -Value @(
            'LazyBuilder Compile-Only Diagnostic Build',
            '',
            'This output is not a runtime-ready candidate and must not be used for installer/runtime acceptance.',
            '',
            'Run:',
            '  LazyBuilder-Diagnostics.exe'
        )
        Write-Host ''
        Write-Host 'Compile-only build complete.' -ForegroundColor Yellow
        Write-Host "Diagnostic binary: $DiagnosticExe"
        return
    }

    $CommitSha = 'local'
    try {
        $CandidateSha = (& git -C $RepoRoot rev-parse HEAD 2>$null).Trim()
        if (-not [string]::IsNullOrWhiteSpace($CandidateSha)) { $CommitSha = $CandidateSha }
    } catch {}

    & $PackageLocal -RepoRoot $RepoRoot -InstallerPath $FreshInstaller -CommitSha $CommitSha -RunNumber 'local'
    if ($LASTEXITCODE -ne 0) { throw "Local distribution packaging failed with exit code $LASTEXITCODE." }

    $DiagnosticExe = Join-Path $LocalPublishDir 'LazyBuilder-Diagnostics.exe'
    Copy-Item $Exe $DiagnosticExe -Force

    Write-Host ''
    Write-Host 'Runtime-ready Local build complete.' -ForegroundColor Green
    Write-Host "Installer: $(Join-Path $LocalPublishDir 'LazyBuilder-Setup-Local.exe')" -ForegroundColor Green
    Write-Host "Developer diagnostic binary: $DiagnosticExe"
}
finally { Pop-Location }
