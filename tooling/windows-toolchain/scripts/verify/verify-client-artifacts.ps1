param([Parameter(Mandatory=$true)][string]$ClientModsDir)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (-not (Test-Path $ClientModsDir)) { throw "Client mod directory does not exist: $ClientModsDir" }

$requiredPrefixes = @(
    'lazybuilder-map-manager-',
    'lazybuilder-utility-manager-',
    'lazybuilder-performance-manager-'
)
$failures = @()
foreach ($prefix in $requiredPrefixes) {
    $matches = @(Get-ChildItem $ClientModsDir -Filter "$prefix*.jar" -File -ErrorAction SilentlyContinue)
    if ($matches.Count -ne 1) { $failures += "$prefix expected exactly one JAR, found $($matches.Count)"; continue }
    if ($matches[0].Length -le 0) { $failures += "$($matches[0].Name) is empty" }
}
if ($failures.Count -gt 0) {
    Write-Host 'Client artifact verification FAILED:' -ForegroundColor Red
    $failures | ForEach-Object { Write-Host "  - $_" }
    exit 4
}
Write-Host 'Client artifact verification PASS.' -ForegroundColor Green
exit 0
