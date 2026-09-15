param(
    [switch]$CheckOnly,
    [switch]$NoInstall
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if ($env:OS -ne 'Windows_NT') { throw 'LazyBuilder desktop development is Windows-only.' }

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..')
$CheckScript = Join-Path $PSScriptRoot 'check-tools.ps1'
$ToolchainPath = Join-Path $RepoRoot 'toolchain.json'
$T = Get-Content $ToolchainPath -Raw | ConvertFrom-Json

if ([int]$T.schemaVersion -ne 2) {
    throw "Unsupported toolchain.json schemaVersion '$($T.schemaVersion)'. Expected schemaVersion 2."
}

function Has-Command([string]$Name) {
    return $null -ne (Get-Command $Name -ErrorAction SilentlyContinue)
}

function Invoke-WingetInstall([string]$Id, [string[]]$ExtraArgs = @()) {
    if (-not (Has-Command 'winget')) {
        throw "winget is unavailable. Install App Installer from Microsoft, or install '$Id' manually."
    }
    Write-Host "Installing/repairing $Id ..." -ForegroundColor Cyan
    & winget install --id $Id --exact --source winget --accept-package-agreements --accept-source-agreements --disable-interactivity @ExtraArgs
    if ($LASTEXITCODE -ne 0) {
        throw "winget failed for $Id with exit code $LASTEXITCODE."
    }
}

function Get-WingetVersionForMajor([string]$Id, [int]$Major) {
    if (-not (Has-Command 'winget')) { return $null }
    try {
        $lines = @(& winget show --id $Id --exact --source winget --versions --accept-source-agreements 2>$null | ForEach-Object { [string]$_ })
        $candidates = @()
        foreach ($line in $lines) {
            $value = $line.Trim()
            if ($value -match "^$Major\.(?<minor>\d+)\.(?<patch>\d+)(?:\.(?<build>\d+))?$") {
                $parts = $value.Split('.') | ForEach-Object { [int]$_ }
                while ($parts.Count -lt 4) { $parts += 0 }
                $candidates += [pscustomobject]@{
                    Version = $value
                    SortKey = (($parts[0] * 1000000000L) + ($parts[1] * 1000000L) + ($parts[2] * 1000L) + $parts[3])
                }
            }
        }
        return ($candidates | Sort-Object SortKey -Descending | Select-Object -First 1).Version
    }
    catch { return $null }
}

function Invoke-WingetMajorInstall([string]$Id, [int]$Major) {
    $version = Get-WingetVersionForMajor -Id $Id -Major $Major
    if (-not $version) {
        throw "winget does not expose an installable $Id version in required major $Major. Install a supported $Major.x release manually, then rerun DEV.cmd check."
    }
    Invoke-WingetInstall $Id @('--version', $version)
}

function Get-NodeMajor {
    if (-not (Has-Command 'node')) { return $null }
    $text = [string](& node --version 2>$null)
    $m = [regex]::Match($text, '^v(?<major>\d+)\.')
    if (-not $m.Success) { return $null }
    return [int]$m.Groups['major'].Value
}

function Get-JavaMajor {
    if (-not (Has-Command 'java')) { return $null }
    $text = ((& java -version 2>&1) | ForEach-Object { [string]$_ }) -join "`n"
    $m = [regex]::Match($text, 'version\s+"(?<major>\d+)')
    if (-not $m.Success) { return $null }
    return [int]$m.Groups['major'].Value
}

function Get-VsWherePath {
    $Candidates = @(
        (Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'),
        (Join-Path $env:ProgramFiles 'Microsoft Visual Studio\Installer\vswhere.exe')
    ) | Where-Object { $_ -and (Test-Path $_) }
    return ($Candidates | Select-Object -First 1)
}

function Test-Msvc {
    $vswhere = Get-VsWherePath
    if (-not $vswhere) { return $false }
    $installation = (& $vswhere -latest -products * -requires Microsoft.VisualStudio.Workload.VCTools -property installationPath 2>$null | Select-Object -First 1)
    return -not [string]::IsNullOrWhiteSpace([string]$installation)
}

Write-Host ''
Write-Host 'LazyBuilder Developer Bootstrap' -ForegroundColor Cyan
Write-Host 'Canonical path: DEV.cmd setup -> DEV.cmd check -> development'
Write-Host 'Policy: pinned majors/exact toolchains; repository wrappers for Maven/Gradle; no mandatory Python for developer builds.'
Write-Host ''

& $CheckScript
$checkCode = $LASTEXITCODE
if ($checkCode -eq 0) { exit 0 }
if ($CheckOnly -or $NoInstall) { exit $checkCode }

Write-Host ''
Write-Host 'Environment is incomplete. Attempting deterministic repair of supported developer foundations.' -ForegroundColor Yellow
Write-Host 'Only missing or policy-mismatched foundations are touched.' -ForegroundColor DarkGray
Write-Host ''

if ((Get-JavaMajor) -ne [int]$T.java.major) {
    if ([int]$T.java.major -ne 21) {
        throw "Automatic Java bootstrap is not defined for policy major $($T.java.major). Update the bootstrap owner deliberately before changing the Java major."
    }
    Invoke-WingetInstall 'EclipseAdoptium.Temurin.21.JDK'
}

if ((Get-NodeMajor) -ne [int]$T.node.major) {
    Invoke-WingetMajorInstall 'OpenJS.NodeJS.LTS' ([int]$T.node.major)
}

if (-not (Has-Command 'git')) {
    Invoke-WingetInstall 'Git.Git'
}

if (-not (Has-Command 'rustup')) {
    Invoke-WingetInstall 'Rustlang.Rustup'
}

if (Has-Command 'rustup') {
    Write-Host "Ensuring Rust $($T.rust.toolchain) is installed..." -ForegroundColor Cyan
    & rustup toolchain install ([string]$T.rust.toolchain) --profile minimal
    if ($LASTEXITCODE -ne 0) { throw "rustup failed to install Rust $($T.rust.toolchain)." }
}

if (-not (Test-Msvc)) {
    Invoke-WingetInstall 'Microsoft.VisualStudio.2022.BuildTools' @('--override', '--wait --passive --add Microsoft.VisualStudio.Workload.VCTools --includeRecommended')
}

Write-Host ''
Write-Host 'Tool installation/repair completed. Refreshing process PATH before verification...' -ForegroundColor Cyan
$machinePath = [Environment]::GetEnvironmentVariable('Path', 'Machine')
$userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
$env:Path = "$machinePath;$userPath"

& $CheckScript
$finalCode = $LASTEXITCODE
if ($finalCode -ne 0) {
    Write-Host ''
    Write-Host 'Bootstrap installed the supported foundations, but this shell still does not satisfy policy.' -ForegroundColor Yellow
    Write-Host 'Close this window, open a new terminal, and run DEV.cmd check. If MSVC was just installed, Windows may also require a sign-out/restart.'
    exit $finalCode
}

Write-Host ''
Write-Host 'Developer environment is ready.' -ForegroundColor Green
Write-Host 'Next: DEV.cmd build when integrated package verification is needed.' -ForegroundColor Cyan
exit 0
