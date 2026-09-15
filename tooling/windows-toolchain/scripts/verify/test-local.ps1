param(
    [switch]$Build,
    [switch]$SkipInstaller,
    [switch]$SkipRestart,
    [switch]$PaperOnly
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ($env:OS -ne 'Windows_NT') { throw 'LazyBuilder local acceptance must run on Windows.' }

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..')).Path
$BuildScript = Join-Path $RepoRoot 'apps\launcher\build-local.ps1'
$RuntimeProof = Join-Path $RepoRoot 'scripts\verify-paper-runtime.ps1'
$RestartProof = Join-Path $RepoRoot 'scripts\verify-paper-restart.ps1'
$InstallerProof = Join-Path $RepoRoot 'tooling\windows-toolchain\scripts\distribution\verify-installer.ps1'
$PaperCacheDir = Join-Path $RepoRoot '.runtime-proof\paper-cache'
$PaperJar = Join-Path $PaperCacheDir 'paper-1.21.4.jar'
$WorldJar = Join-Path $RepoRoot 'plugins\world-manager\target\World-Manager-0.1.0-SNAPSHOT.jar'
$UtilitiesJar = Join-Path $RepoRoot 'plugins\utilities-manager\target\Utilities-Manager-0.1.0-SNAPSHOT.jar'
$Installer = Join-Path $RepoRoot 'dist\LazyBuilder\LazyBuilder-Setup.exe'

function Require-File([string]$Path, [string]$Hint) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Required file is missing: $Path`n$Hint"
    }
}

function Ensure-StablePaper {
    if (Test-Path -LiteralPath $PaperJar -PathType Leaf) {
        Write-Host "[paper] Reusing cached Paper 1.21.4 runtime: $PaperJar" -ForegroundColor DarkGray
        return
    }

    New-Item -ItemType Directory -Force -Path $PaperCacheDir | Out-Null
    $headers = @{ 'User-Agent' = 'LazyBuilder-Local-Acceptance/0.1 (https://github.com/halokaryamedia-source/LazyBuilder-Plugin)' }
    Write-Host '[paper] Resolving stable Paper 1.21.4 build...' -ForegroundColor Cyan
    $builds = Invoke-RestMethod -Headers $headers -Uri 'https://fill.papermc.io/v3/projects/paper/versions/1.21.4/builds' -TimeoutSec 30
    $stable = $builds | Where-Object { $_.channel -eq 'STABLE' } | Select-Object -First 1
    if ($null -eq $stable) { throw 'PaperMC did not return a stable Paper 1.21.4 build.' }
    $download = $stable.downloads.'server:default'.url
    if ([string]::IsNullOrWhiteSpace($download)) { throw 'PaperMC stable build did not expose a server download URL.' }
    Invoke-WebRequest -Headers $headers -Uri $download -OutFile $PaperJar -TimeoutSec 120
    Require-File $PaperJar 'Paper download did not produce the expected file.'
}

Write-Host ''
Write-Host 'LazyBuilder Local Acceptance' -ForegroundColor Cyan
Write-Host "Repository: $RepoRoot"
Write-Host ''

if ($Build) {
    Write-Host '[build] Running canonical runtime-ready launcher build first...' -ForegroundColor Cyan
    & $BuildScript
    if ($LASTEXITCODE -ne 0) { throw "Canonical build failed with exit code $LASTEXITCODE." }
}

Require-File $WorldJar 'Run BUILD-LAUNCHER.cmd first, or rerun TEST-LOCAL.cmd with -Build.'
Require-File $UtilitiesJar 'Run BUILD-LAUNCHER.cmd first, or rerun TEST-LOCAL.cmd with -Build.'
Ensure-StablePaper

Write-Host '[1/3] Paper runtime behavior proof...' -ForegroundColor Cyan
& $RuntimeProof -ServerJar $PaperJar -WorldManagerJar $WorldJar -UtilitiesManagerJar $UtilitiesJar
if ($LASTEXITCODE -ne 0) { throw "Paper runtime proof failed with exit code $LASTEXITCODE." }

if ($SkipRestart) {
    Write-Host '[2/3] Paper restart persistence proof skipped by request.' -ForegroundColor Yellow
} else {
    Write-Host '[2/3] Paper restart persistence proof...' -ForegroundColor Cyan
    & $RestartProof -ServerJar $PaperJar
    if ($LASTEXITCODE -ne 0) { throw "Paper restart proof failed with exit code $LASTEXITCODE." }
}

if ($PaperOnly -or $SkipInstaller) {
    Write-Host '[3/3] Installed Launcher acceptance skipped by request.' -ForegroundColor Yellow
} else {
    Require-File $Installer 'Run BUILD-LAUNCHER.cmd first, or rerun TEST-LOCAL.cmd with -Build.'
    Write-Host '[3/3] Installed Launcher clean-PATH smoke...' -ForegroundColor Cyan
    & $InstallerProof -InstallerPath $Installer
    if ($LASTEXITCODE -ne 0) { throw "Installer acceptance failed with exit code $LASTEXITCODE." }
}

Write-Host ''
Write-Host 'PASS: LazyBuilder local acceptance completed for the selected proof lanes.' -ForegroundColor Green
Write-Host 'Runtime proof artifacts remain under .runtime-proof for diagnosis.' -ForegroundColor DarkGray
