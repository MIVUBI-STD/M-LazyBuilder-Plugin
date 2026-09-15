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

Require-Command 'node' 'Run SETUP-DEV.cmd and install Node.js 24 LTS.'
Require-Command 'npm' 'Install Node.js with npm.'
Require-Command 'rustc' 'Install rustup; the repository pins the Rust toolchain.'
Require-Command 'cargo' 'Install rustup; the repository pins the Rust toolchain.'
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
    $javaText = [string](& java -version 2>&1 | Select-Object -First 1)
    if ($javaText -notmatch '"21(\.|\")') {
        throw "Java policy mismatch. Required Java 21, found: $javaText"
    }
}

Write-Host "Build preflight PASS (Node $($T.node.major).x, Rust $($T.rust.toolchain)$(if ($RequireJava) { ', Java 21' } else { '' }))." -ForegroundColor Green
