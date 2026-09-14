param(
    [switch]$SkipTests,
    [switch]$AllowMissingCore,
    [switch]$UpdateInstalled
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Require-Command {
    param([string]$Name, [string]$Hint)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) { throw "Missing required tool '$Name'. $Hint" }
}

if ($env:OS -ne 'Windows_NT') { throw 'LazyBuilder local desktop build must run on Windows.' }

Require-Command node 'Install Node.js 24 LTS or newer.'
Require-Command npm 'Install Node.js with npm.'
Require-Command cargo 'Install Rust using rustup.'
Require-Command rustc 'Install the Rust toolchain using rustup.'

$AppRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Resolve-Path (Join-Path $AppRoot '..\..')
$CoreDir = Join-Path $AppRoot 'src-tauri\resources\core'
$ClientModsDir = Join-Path $AppRoot 'src-tauri\resources\client-mods'
$WorldJar = Join-Path $CoreDir 'World-Manager-0.1.0-SNAPSHOT.jar'
$UtilitiesJar = Join-Path $CoreDir 'Utilities-Manager-0.1.0-SNAPSHOT.jar'
$MapJar = Join-Path $ClientModsDir 'lazybuilder-map-manager-0.1.0-SNAPSHOT.jar'
$UtilityClientJar = Join-Path $ClientModsDir 'lazybuilder-utility-manager-0.1.0-SNAPSHOT.jar'
$PerformanceJar = Join-Path $ClientModsDir 'lazybuilder-performance-manager-0.1.0-SNAPSHOT.jar'
$WorldTargetJar = Join-Path $RepoRoot 'plugins\world-manager\target\World-Manager-0.1.0-SNAPSHOT.jar'
$UtilitiesTargetJar = Join-Path $RepoRoot 'plugins\utilities-manager\target\Utilities-Manager-0.1.0-SNAPSHOT.jar'
$MapTargetJar = Join-Path $RepoRoot 'mods\map-manager\build\libs\lazybuilder-map-manager-0.1.0-SNAPSHOT.jar'
$UtilityClientTargetJar = Join-Path $RepoRoot 'mods\utility-manager\build\libs\lazybuilder-utility-manager-0.1.0-SNAPSHOT.jar'
$PerformanceTargetJar = Join-Path $RepoRoot 'mods\performance-manager\build\libs\lazybuilder-performance-manager-0.1.0-SNAPSHOT.jar'
$PublishDir = Join-Path $RepoRoot 'dist\LazyBuilder'
$NsisDir = Join-Path $AppRoot 'src-tauri\target\release\bundle\nsis'

Write-Host ''
Write-Host $(if ($UpdateInstalled) { 'LazyBuilder Launcher - Clean Local Update' } else { 'LazyBuilder Launcher - Local Windows Build' }) -ForegroundColor Cyan
Write-Host "Repository: $RepoRoot"
Write-Host "Launcher:   $AppRoot"
Write-Host ''

if ($UpdateInstalled -and $AllowMissingCore) {
    throw 'Installed Launcher update is blocked in compile-only mode because the installed app must remain runtime-ready.'
}

if (-not $AllowMissingCore) {
    Require-Command java 'Install or activate Java 21.'
    Require-Command mvn 'Install Apache Maven and make mvn available on PATH.'
    Require-Command gradle 'Install Gradle 8.12 and make gradle available on PATH.'

    Write-Host '[runtime] Building and testing matching Paper plugins...' -ForegroundColor Cyan
    Push-Location $RepoRoot
    try {
        mvn --batch-mode --no-transfer-progress verify
        if ($LASTEXITCODE -ne 0) { throw "Maven verification failed with exit code $LASTEXITCODE." }

        Write-Host '[runtime] Building LazyBuilder Fabric client mods...' -ForegroundColor Cyan
        gradle -p mods/map-manager --no-daemon build
        if ($LASTEXITCODE -ne 0) { throw "Map Manager build failed with exit code $LASTEXITCODE." }
        gradle -p mods/utility-manager --no-daemon build
        if ($LASTEXITCODE -ne 0) { throw "Utility Manager build failed with exit code $LASTEXITCODE." }
        gradle -p mods/performance-manager --no-daemon build
        if ($LASTEXITCODE -ne 0) { throw "Performance Manager build failed with exit code $LASTEXITCODE." }
    }
    finally { Pop-Location }

    $RequiredBuildOutputs = @($WorldTargetJar, $UtilitiesTargetJar, $MapTargetJar, $UtilityClientTargetJar, $PerformanceTargetJar)
    foreach ($Output in $RequiredBuildOutputs) {
        if (-not (Test-Path $Output)) { throw "Runtime verification completed but required artifact was not found: $Output" }
    }

    New-Item -ItemType Directory -Force -Path $CoreDir | Out-Null
    New-Item -ItemType Directory -Force -Path $ClientModsDir | Out-Null
    Copy-Item $WorldTargetJar $WorldJar -Force
    Copy-Item $UtilitiesTargetJar $UtilitiesJar -Force
    Copy-Item $MapTargetJar $MapJar -Force
    Copy-Item $UtilityClientTargetJar $UtilityClientJar -Force
    Copy-Item $PerformanceTargetJar $PerformanceJar -Force
    Write-Host 'Matching tested server plugins and client mods staged for the desktop package.' -ForegroundColor Green
    Write-Host ''
}

$MissingRuntime = @()
foreach ($Path in @($WorldJar, $UtilitiesJar, $MapJar, $UtilityClientJar, $PerformanceJar)) {
    if (-not (Test-Path $Path)) { $MissingRuntime += (Split-Path $Path -Leaf) }
}
if ($MissingRuntime.Count -gt 0) {
    $MissingText = $MissingRuntime -join ', '
    if (-not $AllowMissingCore) { throw "Runtime-ready Launcher build blocked: bundled runtime components are missing ($MissingText)." }
    Write-Warning "Compile-only mode: runtime components are missing ($MissingText)."
    Write-Warning 'The produced app must not be used for fresh server or Client Setup runtime validation.'
    Write-Host ''
}

if ($UpdateInstalled) {
    $Running = @(Get-Process -Name 'lazybuilder' -ErrorAction SilentlyContinue)
    if ($Running.Count -gt 0) { throw 'LazyBuilder is currently running. Close the Launcher first, then run UPDATE-LAUNCHER.cmd again.' }
}

if (Test-Path $PublishDir) { Remove-Item $PublishDir -Recurse -Force }
if (Test-Path $NsisDir) { Get-ChildItem $NsisDir -Filter '*.exe' -File -ErrorAction SilentlyContinue | Remove-Item -Force }

Push-Location $AppRoot
try {
    Write-Host '[1/5] Installing exact frontend dependencies...' -ForegroundColor Cyan
    npm ci
    Write-Host '[2/5] Typechecking Svelte...' -ForegroundColor Cyan
    npm run typecheck
    Write-Host '[3/5] Checking Rust/Tauri backend...' -ForegroundColor Cyan
    npm run prepare:icons
    cargo check --locked --manifest-path src-tauri/Cargo.toml

    if (-not $SkipTests) {
        Write-Host '[4/5] Running Rust/Tauri tests...' -ForegroundColor Cyan
        cargo test --locked --manifest-path src-tauri/Cargo.toml
    } else {
        Write-Host '[4/5] Rust/Tauri tests skipped by request.' -ForegroundColor Yellow
    }

    Write-Host '[5/5] Building Windows Launcher package...' -ForegroundColor Cyan
    npx tauri build

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
        if (Test-Path $PublishDir) { Remove-Item $PublishDir -Recurse -Force }
        Get-ChildItem $NsisDir -Filter '*.exe' -File -ErrorAction SilentlyContinue | Remove-Item -Force
        Write-Host ''
        Write-Host 'Installed Launcher updated successfully.' -ForegroundColor Green
        Write-Host 'Server workspaces, selected Modrinth profile, and LazyBuilder user data were preserved.'
        Write-Host 'Open LazyBuilder normally and continue local testing.' -ForegroundColor Cyan
        return
    }

    New-Item -ItemType Directory -Force -Path $PublishDir | Out-Null
    $PublishedInstaller = Join-Path $PublishDir 'LazyBuilder-Setup.exe'
    $PublishedExe = Join-Path $PublishDir 'LazyBuilder.exe'
    Copy-Item $FreshInstaller $PublishedInstaller -Force
    Copy-Item $Exe $PublishedExe -Force

    $BuildInfo = @(
        'LazyBuilder Windows build', '',
        'Recommended:', '  LazyBuilder-Setup.exe  - install/update and run LazyBuilder normally', '',
        'Developer diagnostic binary:', '  LazyBuilder.exe        - raw Tauri executable', '',
        "Built: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')",
        "Mode: $(if ($MissingRuntime.Count -gt 0) { 'compile-only' } else { 'runtime-ready (server + Modrinth Client Setup)' })"
    ) -join [Environment]::NewLine
    Set-Content -Path (Join-Path $PublishDir 'README.txt') -Value $BuildInfo -Encoding UTF8

    Write-Host ''
    Write-Host 'Build complete.' -ForegroundColor Green
    Write-Host "Installer: $PublishedInstaller" -ForegroundColor Green
    Write-Host "Developer binary: $PublishedExe"
    Write-Host $(if ($MissingRuntime.Count -gt 0) { 'Mode: compile-only' } else { 'Mode: runtime-ready Launcher package' }) -ForegroundColor $(if ($MissingRuntime.Count -gt 0) { 'Yellow' } else { 'Green' })
}
finally { Pop-Location }
