param(
    [switch]$SkipTests,
    [switch]$AllowMissingCore,
    [switch]$UpdateInstalled
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
$RepoRoot = Resolve-Path (Join-Path $AppRoot '..\..')
$CoreDir = Join-Path $AppRoot 'src-tauri\resources\core'
$WorldJar = Join-Path $CoreDir 'World-Manager-0.1.0-SNAPSHOT.jar'
$UtilitiesJar = Join-Path $CoreDir 'Utilities-Manager-0.1.0-SNAPSHOT.jar'
$WorldTargetJar = Join-Path $RepoRoot 'plugins\world-manager\target\World-Manager-0.1.0-SNAPSHOT.jar'
$UtilitiesTargetJar = Join-Path $RepoRoot 'plugins\utilities-manager\target\Utilities-Manager-0.1.0-SNAPSHOT.jar'
$PublishDir = Join-Path $RepoRoot 'dist\LazyBuilder'
$NsisDir = Join-Path $AppRoot 'src-tauri\target\release\bundle\nsis'

Write-Host ''
if ($UpdateInstalled) {
    Write-Host 'LazyBuilder Launcher - Clean Local Update' -ForegroundColor Cyan
} else {
    Write-Host 'LazyBuilder Launcher - Local Windows Build' -ForegroundColor Cyan
}
Write-Host "Repository: $RepoRoot"
Write-Host "Launcher:   $AppRoot"
Write-Host ''

if ($UpdateInstalled -and $AllowMissingCore) {
    throw 'Installed Launcher update is blocked in compile-only mode because the installed app must remain runtime-ready.'
}

if (-not $AllowMissingCore) {
    Require-Command java 'Install or activate Java 21.'
    Require-Command mvn 'Install Apache Maven and make mvn available on PATH.'

    Write-Host '[core] Building and testing matching Paper core plugins...' -ForegroundColor Cyan
    Push-Location $RepoRoot
    try {
        mvn --batch-mode --no-transfer-progress verify
        if ($LASTEXITCODE -ne 0) {
            throw "Maven core verification failed with exit code $LASTEXITCODE."
        }
    }
    finally {
        Pop-Location
    }

    if (-not (Test-Path $WorldTargetJar)) {
        throw 'Maven verification completed but the World-Manager JAR was not found.'
    }
    if (-not (Test-Path $UtilitiesTargetJar)) {
        throw 'Maven verification completed but the Utilities-Manager JAR was not found.'
    }

    New-Item -ItemType Directory -Force -Path $CoreDir | Out-Null
    Copy-Item $WorldTargetJar $WorldJar -Force
    Copy-Item $UtilitiesTargetJar $UtilitiesJar -Force
    Write-Host 'Matching tested core JARs staged for the desktop package.' -ForegroundColor Green
    Write-Host ''
}

$MissingCore = @()
if (-not (Test-Path $WorldJar)) { $MissingCore += 'World-Manager-0.1.0-SNAPSHOT.jar' }
if (-not (Test-Path $UtilitiesJar)) { $MissingCore += 'Utilities-Manager-0.1.0-SNAPSHOT.jar' }

if ($MissingCore.Count -gt 0) {
    $MissingText = $MissingCore -join ', '
    if (-not $AllowMissingCore) {
        throw "Runtime-ready Launcher build blocked: matching core JARs are missing from src-tauri/resources/core ($MissingText)."
    }
    Write-Warning "Compile-only mode: core JARs are missing ($MissingText)."
    Write-Warning 'The produced app must not be used to validate Prepare Server or a fresh server workflow.'
    Write-Host ''
}

if ($UpdateInstalled) {
    $Running = @(Get-Process -Name 'lazybuilder' -ErrorAction SilentlyContinue)
    if ($Running.Count -gt 0) {
        throw 'LazyBuilder is currently running. Close the Launcher first, then run UPDATE-LAUNCHER.cmd again.'
    }
}

if (Test-Path $PublishDir) {
    Remove-Item $PublishDir -Recurse -Force
}
if (Test-Path $NsisDir) {
    Get-ChildItem $NsisDir -Filter '*.exe' -File -ErrorAction SilentlyContinue | Remove-Item -Force
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

    Write-Host '[5/5] Building Windows Launcher package...' -ForegroundColor Cyan
    npx tauri build

    $Exe = Join-Path $AppRoot 'src-tauri\target\release\lazybuilder.exe'
    if (-not (Test-Path $Exe)) {
        throw 'Tauri reported success but lazybuilder.exe was not found in the expected release directory.'
    }

    $Installers = @()
    if (Test-Path $NsisDir) {
        $Installers = @(Get-ChildItem $NsisDir -Filter '*-setup.exe' -File | Sort-Object LastWriteTime -Descending)
    }
    if ($Installers.Count -ne 1) {
        throw "Expected exactly one freshly built NSIS installer, found $($Installers.Count)."
    }
    $FreshInstaller = $Installers[0].FullName

    if ($UpdateInstalled) {
        Write-Host ''
        Write-Host 'Updating the installed LazyBuilder in place...' -ForegroundColor Cyan
        $UpdateProcess = Start-Process -FilePath $FreshInstaller -ArgumentList '/S' -Wait -PassThru
        if ($UpdateProcess.ExitCode -ne 0) {
            throw "LazyBuilder update installer exited with code $($UpdateProcess.ExitCode)."
        }

        if (Test-Path $PublishDir) {
            Remove-Item $PublishDir -Recurse -Force
        }
        Get-ChildItem $NsisDir -Filter '*.exe' -File -ErrorAction SilentlyContinue | Remove-Item -Force

        Write-Host ''
        Write-Host 'Installed Launcher updated successfully.' -ForegroundColor Green
        Write-Host 'No new installer was kept. Previous local installer handoff files were removed.' -ForegroundColor Green
        Write-Host 'Server workspaces and LazyBuilder user data were not deleted.'
        Write-Host ''
        Write-Host 'Open LazyBuilder normally from the Start Menu/shortcut and continue local testing.' -ForegroundColor Cyan
        return
    }

    New-Item -ItemType Directory -Force -Path $PublishDir | Out-Null
    $PublishedInstaller = Join-Path $PublishDir 'LazyBuilder-Setup.exe'
    Copy-Item $FreshInstaller $PublishedInstaller -Force

    $PublishedExe = Join-Path $PublishDir 'LazyBuilder.exe'
    Copy-Item $Exe $PublishedExe -Force

    $BuildInfo = @(
        'LazyBuilder Windows build',
        '',
        'Recommended:',
        '  LazyBuilder-Setup.exe  - install/update and run LazyBuilder normally',
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
    Write-Host 'Installer:' -ForegroundColor Cyan
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
