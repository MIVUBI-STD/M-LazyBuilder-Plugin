param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..')
$ConfigPath = Join-Path $RepoRoot 'toolchain.json'
if (-not (Test-Path $ConfigPath)) { throw "Missing toolchain manifest: $ConfigPath" }
$T = Get-Content $ConfigPath -Raw | ConvertFrom-Json

function Get-VersionLine([string]$Command, [string[]]$Args = @('--version')) {
    $cmd = Get-Command $Command -ErrorAction SilentlyContinue
    if (-not $cmd) { return $null }
    try { return [string](& $Command @Args 2>&1 | Select-Object -First 1) } catch { return $null }
}

function Test-Major([string]$VersionText, [int]$Expected) {
    if (-not $VersionText) { return $false }
    $m = [regex]::Match($VersionText, '(?<!\d)(\d+)(?:\.\d+)+')
    if (-not $m.Success) { return $false }
    return [int]$m.Groups[1].Value -eq $Expected
}

$rows = @()
$java = Get-VersionLine 'java' @('-version')
$rows += [pscustomobject]@{Tool='Java'; Required="21.x LTS"; Found=$(if($java){$java}else{'MISSING'}); OK=(Test-Major $java ([int]$T.java.major))}
$node = Get-VersionLine 'node' @('--version')
$rows += [pscustomobject]@{Tool='Node.js'; Required="$($T.node.major).x LTS"; Found=$(if($node){$node}else{'MISSING'}); OK=(Test-Major $node ([int]$T.node.major))}
$npm = Get-VersionLine 'npm' @('--version')
$rows += [pscustomobject]@{Tool='npm'; Required='bundled with Node'; Found=$(if($npm){$npm}else{'MISSING'}); OK=[bool]$npm}
$rustc = Get-VersionLine 'rustc' @('--version')
$rows += [pscustomobject]@{Tool='Rust'; Required=$T.rust.toolchain; Found=$(if($rustc){$rustc}else{'MISSING'}); OK=($rustc -match [regex]::Escape([string]$T.rust.toolchain))}
$cargo = Get-VersionLine 'cargo' @('--version')
$rows += [pscustomobject]@{Tool='Cargo'; Required='from pinned Rust'; Found=$(if($cargo){$cargo}else{'MISSING'}); OK=[bool]$cargo}
$git = Get-VersionLine 'git' @('--version')
$rows += [pscustomobject]@{Tool='Git'; Required='Git for Windows'; Found=$(if($git){$git}else{'MISSING'}); OK=[bool]$git}

Write-Host ''
Write-Host 'LazyBuilder Toolchain Check' -ForegroundColor Cyan
$rows | Format-Table -AutoSize Tool, Required, Found, OK
$failed = @($rows | Where-Object { -not $_.OK })
if ($failed.Count -gt 0) {
    Write-Host "FAILED: $($failed.Count) requirement(s) missing or outside policy." -ForegroundColor Red
    exit 2
}
Write-Host 'PASS: core developer toolchain matches policy.' -ForegroundColor Green
exit 0
