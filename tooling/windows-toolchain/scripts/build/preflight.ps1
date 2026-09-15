param([Parameter(Mandatory=$true)][string]$RepoRoot)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$RepoRoot = Resolve-Path $RepoRoot
$T = Get-Content (Join-Path $RepoRoot 'toolchain.json') -Raw | ConvertFrom-Json

$required = @(
    (Join-Path $RepoRoot 'mvnw.cmd'),
    (Join-Path $RepoRoot 'gradlew.bat'),
    (Join-Path $RepoRoot 'apps\launcher\package-lock.json'),
    (Join-Path $RepoRoot 'apps\launcher\src-tauri\Cargo.lock')
)
$missing = @($required | Where-Object { -not (Test-Path $_) })
if ($missing.Count -gt 0) {
    Write-Host 'Build preflight failed. Missing reproducibility files:' -ForegroundColor Red
    $missing | ForEach-Object { Write-Host "  - $_" }
    exit 3
}

$node = (& node --version 2>$null)
if ($node -notmatch "^v$($T.node.major)\.") { throw "Node policy mismatch. Required $($T.node.major).x, found $node" }
$rust = (& rustc --version 2>$null)
if ($rust -notmatch [regex]::Escape([string]$T.rust.toolchain)) { throw "Rust policy mismatch. Required $($T.rust.toolchain), found $rust" }
$javaText = (& java -version 2>&1 | Select-Object -First 1)
if ($javaText -notmatch '"21(\.|\")') { throw "Java policy mismatch. Required Java 21, found: $javaText" }
Write-Host 'Build preflight PASS.' -ForegroundColor Green
