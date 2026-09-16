param(
    [Parameter(Mandatory=$true)][string]$RepoRoot,
    [switch]$RequireJava
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$RepoRoot = Resolve-Path $RepoRoot
$T = Get-Content (Join-Path $RepoRoot 'toolchain.json') -Raw | ConvertFrom-Json

function Require-Command([string]$Name, [string]$Hint) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Missing required tool '$Name'. $Hint"
    }
}

function Require-Msvc {
    $vswhereCandidates = @(
        (Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'),
        (Join-Path $env:ProgramFiles 'Microsoft Visual Studio\Installer\vswhere.exe')
    ) | Where-Object { $_ -and (Test-Path $_) }
    $vswhere = $vswhereCandidates | Select-Object -First 1
    if (-not $vswhere) {
        throw 'Missing Visual Studio 2022 Build Tools discovery. Run SETUP-DEV.cmd and install Desktop development with C++.'
    }
    $installation = (& $vswhere -latest -products * -requires Microsoft.VisualStudio.Workload.VCTools -property installationPath 2>$null | Select-Object -First 1)
    if ([string]::IsNullOrWhiteSpace([string]$installation)) {
        throw 'MSVC C++ workload is missing. Run SETUP-DEV.cmd and install Visual Studio 2022 Build Tools with Desktop development with C++.'
    }
}

$requiredFiles = @(
    (Join-Path $RepoRoot 'mvnw.cmd'),
    (Join-Path $RepoRoot 'gradlew.bat'),
    (Join-Path $RepoRoot 'apps\launcher\package-lock.json'),
    (Join-Path $RepoRoot 'apps\launcher\src-tauri\Cargo.lock'),
    (Join-Path $RepoRoot 'rust-toolchain.toml'),
    (Join-Path $RepoRoot '.node-version')
)
$missing = @($requiredFiles | Where-Object { -not (Test-Path $_) })
if ($missing.Count -gt 0) {
    throw "Build preflight failed. Missing reproducibility files: $($missing -join ', ')"
}

# rustup installs its Windows shims in the per-user Cargo bin directory, but a
# PowerShell launched from a desktop shortcut may not inherit that PATH entry.
# Resolve the installed toolchain without requiring the user to repair PATH.
$CargoBin = Join-Path $env:USERPROFILE '.cargo\bin'
if (Test-Path -LiteralPath $CargoBin -PathType Container) {
    $env:PATH = "$CargoBin;$env:PATH"
}

Require-Command 'node' 'Run SETUP-DEV.cmd and install Node.js 24 LTS.'
Require-Command 'npm' 'Install Node.js with npm.'
Require-Command 'rustc' 'Install rustup; the repository pins the Rust toolchain.'
Require-Command 'cargo' 'Install rustup; the repository pins the Rust toolchain.'
Require-Msvc
if ($RequireJava) { Require-Command 'java' 'Install Eclipse Temurin/OpenJDK 21.' }

$node = [string](& node --version 2>$null)
if ($node -notmatch "^v$($T.node.major)\.") {
    throw "Node policy mismatch. Required $($T.node.major).x, found $node"
}

$rust = [string](& rustc --version 2>$null)
if ($rust -notmatch [regex]::Escape([string]$T.rust.toolchain)) {
    throw "Rust policy mismatch. Required $($T.rust.toolchain), found $rust"
}

if ($RequireJava) {
    # `java -version` writes its banner to stderr; Windows PowerShell can turn
    # that native stderr stream into a terminating error. Java 9+ exposes the
    # equivalent `--version` banner on stdout.
    $javaText = [string](& java --version 2>$null | Select-Object -First 1)
    if ($javaText -notmatch '^(?:openjdk|java)\s+21(?:\.|\s|$)') {
        throw "Java policy mismatch. Required Java 21, found: $javaText"
    }
}

Write-Host "Build preflight PASS (Node $($T.node.major).x, Rust $($T.rust.toolchain), MSVC VCTools$(if ($RequireJava) { ', Java 21' } else { '' }))." -ForegroundColor Green
