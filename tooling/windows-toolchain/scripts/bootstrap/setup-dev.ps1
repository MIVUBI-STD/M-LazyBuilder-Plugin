param([switch]$CheckOnly)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if ($env:OS -ne 'Windows_NT') { throw 'LazyBuilder desktop development is Windows-only.' }

$CheckScript = Join-Path $PSScriptRoot 'check-tools.ps1'
Write-Host ''
Write-Host 'LazyBuilder Developer Bootstrap' -ForegroundColor Cyan
Write-Host 'Policy: pinned majors/exact toolchains; wrappers for Maven/Gradle; no mandatory Python.'
Write-Host ''
& $CheckScript
$checkCode = $LASTEXITCODE
if ($checkCode -eq 0) { exit 0 }
if ($CheckOnly) { exit $checkCode }

Write-Host ''
Write-Host 'Environment is not ready.' -ForegroundColor Yellow
Write-Host 'Install/fix only the requirements reported above, then rerun SETUP-DEV.cmd.'
Write-Host ''
Write-Host 'Required developer foundations:' -ForegroundColor Cyan
Write-Host '  Java 21 LTS : Eclipse Temurin'
Write-Host '  Node 24 LTS : Node.js 24.x'
Write-Host '  Rust        : rustup + repository rust-toolchain.toml'
Write-Host '  MSVC        : Visual Studio 2022 Build Tools + Desktop development with C++'
Write-Host '  WebView2    : Microsoft Evergreen Runtime'
Write-Host '  Git         : Git for Windows'
Write-Host ''
Write-Host 'Maven and Gradle must be repository-wrapper owned, not global requirements.' -ForegroundColor Green
Write-Host 'Python is not part of the canonical mandatory toolchain.' -ForegroundColor Green
exit 2
