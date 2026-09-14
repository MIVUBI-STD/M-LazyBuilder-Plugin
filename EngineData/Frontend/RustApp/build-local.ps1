param(
    [switch]$SkipTests,
    [switch]$AllowMissingCore
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Require-Command {
    param([string]$Name, [string]$Hint)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Missing required tool '$Name'. $Hint"
    }
}

if ($env:OS -ne 'Windows_NT') {
    throw 'LazyBuilder local desktop build must run on Windows.'
}

Require-Command node 'Install Node.js 24 LTS or newer.'
Require-Command npm 'Install Node.js with npm.'
Require-Command cargo 'Install Rust using rustup.'
Require-Command rustc 'Install the Rust toolchain using rustup.'

$AppRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Resolve-Path (Join-Path $AppRoot '..\..\..')
$CoreDir = Join-Path $AppRoot 'src-tauri\resources\core'
$WorldJar = Join-Path $CoreDir 'World-Manager-0.1.0-SNAPSHOT.jar'
$UtilitiesJar = Join-Path $CoreDir 'Utilities-Manager-0.1.0-SNAPSHOT.jar'
$PublishDir = Join-Path $RepoRoot 'dist\LazyBuilder'

Write-Host ''
Write-Host 'LazyBuilder Launcher - Local Windows Build' -ForegroundColor Cyan
Write-Host "Repository: $RepoRoot"
Write-Host "Launcher:   $AppRoot"
Write-Host ''

$MissingCore = @()
if (-not (Test-Path $WorldJar)) { $MissingCore += 'World-Manager-0.1.0-SNAPSHOT.jar' }
if (-not (Test-Path $UtilitiesJar)) { $MissingCore += 'Utilities-Manager-0.1.0-SNAPSHOT.jar' }

if ($MissingCore.Count -gt 0) {
    $MissingText = $MissingCore -join ', '
    if (-not $AllowMissingCore) {
        throw "Runtime-ready Launcher build blocked: matching core JARs are missing from src-tauri/resources/core ($MissingText). Stage the tested core JARs first. Use -AllowMissingCore only for an explicit compile-only Launcher check."
    }
    Write-Warning "Compile-only mode: core JARs are missing ($MissingText)."
    Write-Warning 'The produced app must not be used to validate Prepare Server or a fresh server workflow.'
    Write-Host ''
}

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

    Write-Host '[5/5] Building Windows Launcher and installer...' -ForegroundColor Cyan
    # Tauri owns the production frontend build and Windows NSIS packaging.
    npx tauri build

    $Exe = Join-Path $AppRoot 'src-tauri\target\release\lazybuilder.exe'
    $NsisDir = Join-Path $AppRoot 'src-tauri\target\release\bundle\nsis'
    if (-not (Test-Path $Exe)) {
        throw 'Tauri reported success but lazybuilder.exe was not found in the expected release directory.'
    }

    $Installers = @()
    if (Test-Path $NsisDir) {
        $Installers = @(Get-ChildItem $NsisDir -Filter '*-setup.exe' | Sort-Object LastWriteTime -Descending)
    }
    if ($Installers.Count -eq 0) {
        throw 'Tauri reported success but no NSIS installer was found in the expected bundle directory.'
    }

    # Publish a simple, stable output layout for local testing and release handoff.
    # Like Modrinth, the installer EXE is the primary user-facing artifact.
    if (Test-Path $PublishDir) {
        Remove-Item $PublishDir -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path $PublishDir | Out-Null

    $PublishedInstaller = Join-Path $PublishDir 'LazyBuilder-Setup.exe'
    Copy-Item $Installers[0].FullName $PublishedInstaller -Force

    # Keep the raw desktop binary for developer diagnostics only. The installer is
    # the recommended runtime path because Tauri resources are installed with it.
    $PublishedExe = Join-Path $PublishDir 'LazyBuilder.exe'
    Copy-Item $Exe $PublishedExe -Force

    $BuildInfo = @(
        'LazyBuilder Windows build',
        '',
        'Recommended:',
        '  LazyBuilder-Setup.exe  - install and run LazyBuilder normally',
        '',
        'Developer diagnostic binary:',
        '  LazyBuilder.exe        - raw Tauri executable',
        '',
        "Built: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')",
        "Mode: $(if ($MissingCore.Count -gt 0) { 'compile-only' } else { 'runtime-ready' })"
    ) -join [Environment]::NewLine
    Set-Content -Path (Join-Path $PublishDir 'README.txt') -Value $BuildInfo -Encoding UTF8

    Write-Host ''
    Write-Host 'Build complete.' -ForegroundColor Green
    Write-Host ''
    Write-Host 'Use this file like Modrinth:' -ForegroundColor Cyan
    Write-Host "  $PublishedInstaller" -ForegroundColor Green
    Write-Host ''
    Write-Host 'Developer binary:'
    Write-Host "  $PublishedExe"
    if ($MissingCore.Count -gt 0) {
        Write-Host 'Mode: compile-only (core runtime components missing)' -ForegroundColor Yellow
    } else {
        Write-Host 'Mode: runtime-ready Launcher package' -ForegroundColor Green
    }
    Write-Host ''
    Write-Host 'Expected runtime UX: only the LazyBuilder window is visible; Paper and Java validation run without console windows.'
}
finally {
    Pop-Location
}
