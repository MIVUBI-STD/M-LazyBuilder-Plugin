param(
    [Parameter(Mandatory=$true)][string]$InstallerPath
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$InstallerPath = (Resolve-Path $InstallerPath).Path

function Normalize-RegistryPath([object]$Value) {
    if ($null -eq $Value) { return $null }
    $text = ([string]$Value).Trim()
    if ([string]::IsNullOrWhiteSpace($text)) { return $null }
    return $text.Trim('"')
}

function Get-OptionalProperty([object]$Object, [string]$Name) {
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) { return $null }
    return $property.Value
}

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
            Where-Object { (Get-OptionalProperty $_ 'DisplayName') -eq 'LazyBuilder' } |
            Select-Object -First 1
        if ($entry) { break }
    }
    if (-not $entry) { Start-Sleep -Milliseconds 500 }
}

if (-not $entry) {
    throw 'LazyBuilder installer completed but no Windows uninstall registration was found.'
}

$candidates = @()
$installLocation = Normalize-RegistryPath (Get-OptionalProperty $entry 'InstallLocation')
if ($installLocation) {
    $candidates += (Join-Path $installLocation 'LazyBuilder.exe')
    $candidates += (Join-Path $installLocation 'lazybuilder.exe')
}
if ($env:LOCALAPPDATA) {
    $candidates += (Join-Path $env:LOCALAPPDATA 'Programs\LazyBuilder\LazyBuilder.exe')
    $candidates += (Join-Path $env:LOCALAPPDATA 'Programs\LazyBuilder\lazybuilder.exe')
}
$displayIcon = Normalize-RegistryPath (Get-OptionalProperty $entry 'DisplayIcon')
if ($displayIcon) {
    $iconPath = $displayIcon -replace ',\d+$',''
    if ($iconPath) { $candidates += $iconPath }
}

$installedExe = $candidates |
    Where-Object { $_ -and (Test-Path $_ -PathType Leaf) } |
    Select-Object -First 1

if (-not $installedExe) {
    throw "LazyBuilder is registered as installed, but its executable was not found. InstallLocation='$installLocation'"
}

Write-Host 'Launching installed app with a sanitized end-user PATH...' -ForegroundColor Cyan
$originalPath = $env:Path
$launched = $null
try {
    $env:Path = "$env:SystemRoot\System32;$env:SystemRoot"
    $launched = Start-Process -FilePath $installedExe -PassThru
    Start-Sleep -Seconds 5
    if ($launched.HasExited) {
        if ($launched.ExitCode -ne 0) {
            throw "Installed LazyBuilder exited during clean-PATH startup smoke with code $($launched.ExitCode)."
        }
        throw 'Installed LazyBuilder exited unexpectedly during clean-PATH startup smoke.'
    }
    Write-Host 'Clean-PATH startup PASS: app does not depend on developer tools from PATH.' -ForegroundColor Green
}
finally {
    $env:Path = $originalPath
    if ($launched -and -not $launched.HasExited) {
        Stop-Process -Id $launched.Id -Force -ErrorAction SilentlyContinue
    }
}

Write-Host "Installer smoke PASS: $installedExe" -ForegroundColor Green
Write-Host "Uninstall registration: $($entry.PSPath)"
