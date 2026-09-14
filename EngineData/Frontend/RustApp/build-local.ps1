param(
    [switch]$SkipTests
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

Write-Host ''
Write-Host 'LazyBuilder Launcher - Local Windows Build' -ForegroundColor Cyan
Write-Host "Repository: $RepoRoot"
Write-Host "Launcher:   $AppRoot"
Write-Host ''

if (-not (Test-Path $WorldJar) -or -not (Test-Path $UtilitiesJar)) {
    Write-Warning 'Matching core JARs are not staged in src-tauri/resources/core.'
    Write-Warning 'The Launcher can still be compiled, but Prepare Server will need matching core JARs before a full runtime test.'
    Write-Warning 'This script intentionally does not build Paper/Fabric modules.'
    Write-Host ''
}

Push-Location $AppRoot
try {
    Write-Host '[1/6] Installing exact frontend dependencies...' -ForegroundColor Cyan
    npm ci

    Write-Host '[2/6] Typechecking Svelte...' -ForegroundColor Cyan
    npm run typecheck

    Write-Host '[3/6] Building frontend...' -ForegroundColor Cyan
    npm run build:frontend

    Write-Host '[4/6] Checking Rust/Tauri backend...' -ForegroundColor Cyan
    npm run prepare:icons
    cargo check --locked --manifest-path src-tauri/Cargo.toml

    if (-not $SkipTests) {
        Write-Host '[5/6] Running Rust/Tauri tests...' -ForegroundColor Cyan
        cargo test --locked --manifest-path src-tauri/Cargo.toml
    } else {
        Write-Host '[5/6] Rust/Tauri tests skipped by request.' -ForegroundColor Yellow
    }

    Write-Host '[6/6] Building Windows application + NSIS installer...' -ForegroundColor Cyan
    npm run build:app

    $Exe = Join-Path $AppRoot 'src-tauri\target\release\lazybuilder.exe'
    $NsisDir = Join-Path $AppRoot 'src-tauri\target\release\bundle\nsis'

    Write-Host ''
    Write-Host 'Build complete.' -ForegroundColor Green
    if (Test-Path $Exe) {
        Write-Host "Executable: $Exe"
    }
    if (Test-Path $NsisDir) {
        Get-ChildItem $NsisDir -Filter '*-setup.exe' | ForEach-Object {
            Write-Host "Installer:  $($_.FullName)"
        }
    }
    Write-Host ''
    Write-Host 'Expected runtime UX: only the LazyBuilder window is visible; Paper and Java validation run without console windows.'
}
finally {
    Pop-Location
}
