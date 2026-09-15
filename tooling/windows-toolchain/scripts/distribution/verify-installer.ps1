param(
    [Parameter(Mandatory=$true)][string]$InstallerPath
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$InstallerPath = (Resolve-Path $InstallerPath).Path

Write-Host "Smoke installing: $InstallerPath" -ForegroundColor Cyan
$process = Start-Process -FilePath $InstallerPath -ArgumentList '/S' -Wait -PassThru
if ($process.ExitCode -ne 0) {
    throw "LazyBuilder installer exited with code $($process.ExitCode)."
}

$uninstallRoots = @(
    'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall',
    'HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall',
    'HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall'
)

$entry = $null
for ($attempt = 0; $attempt -lt 20 -and -not $entry; $attempt++) {
    foreach ($root in $uninstallRoots) {
        if (-not (Test-Path $root)) { continue }
        $entry = Get-ChildItem $root -ErrorAction SilentlyContinue |
            Get-ItemProperty -ErrorAction SilentlyContinue |
            Where-Object { $_.DisplayName -eq 'LazyBuilder' } |
            Select-Object -First 1
        if ($entry) { break }
    }
    if (-not $entry) { Start-Sleep -Milliseconds 500 }
}

if (-not $entry) {
    throw 'LazyBuilder installer completed but no Windows uninstall registration was found.'
}

$candidates = @()
if ($entry.InstallLocation) {
    $candidates += (Join-Path ([string]$entry.InstallLocation) 'LazyBuilder.exe')
    $candidates += (Join-Path ([string]$entry.InstallLocation) 'lazybuilder.exe')
}
if ($env:LOCALAPPDATA) {
    $candidates += (Join-Path $env:LOCALAPPDATA 'Programs\LazyBuilder\LazyBuilder.exe')
    $candidates += (Join-Path $env:LOCALAPPDATA 'Programs\LazyBuilder\lazybuilder.exe')
}
if ($entry.DisplayIcon) {
    $iconPath = ([string]$entry.DisplayIcon).Trim('"') -replace ',\d+$',''
    if ($iconPath) { $candidates += $iconPath }
}

$installedExe = $candidates |
    Where-Object { $_ -and (Test-Path $_ -PathType Leaf) } |
    Select-Object -First 1

if (-not $installedExe) {
    throw "LazyBuilder is registered as installed, but its executable was not found. InstallLocation='$($entry.InstallLocation)'"
}

Write-Host "Installer smoke PASS: $installedExe" -ForegroundColor Green
Write-Host "Uninstall registration: $($entry.PSPath)"
