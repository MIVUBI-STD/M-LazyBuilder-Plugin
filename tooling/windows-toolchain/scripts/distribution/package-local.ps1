param(
    [Parameter(Mandatory=$true)][string]$RepoRoot,
    [Parameter(Mandatory=$true)][string]$InstallerPath,
    [string]$DiagnosticExecutablePath,
    [string]$CommitSha = $env:GITHUB_SHA,
    [string]$RunNumber = $env:GITHUB_RUN_NUMBER
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$RepoRoot = (Resolve-Path $RepoRoot).Path
$InstallerPath = (Resolve-Path $InstallerPath).Path

if (-not $DiagnosticExecutablePath) {
    $DefaultDiagnostic = Join-Path $RepoRoot 'apps\launcher\src-tauri\target\release\lazybuilder.exe'
    if (Test-Path $DefaultDiagnostic -PathType Leaf) { $DiagnosticExecutablePath = $DefaultDiagnostic }
}
if ($DiagnosticExecutablePath) {
    $DiagnosticExecutablePath = (Resolve-Path $DiagnosticExecutablePath).Path
}

$Version = (Get-Content (Join-Path $RepoRoot 'VERSION') -Raw).Trim()
if (-not $Version) { throw 'VERSION is empty.' }
if (-not $CommitSha) { $CommitSha = 'local' }
if (-not $RunNumber) { $RunNumber = 'manual' }

$ShortSha = if ($CommitSha.Length -ge 7) { $CommitSha.Substring(0, 7) } else { $CommitSha }
$OutputDir = Join-Path $RepoRoot 'dist\Local'
if (Test-Path $OutputDir) { Remove-Item $OutputDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$CanonicalInstaller = Join-Path $OutputDir 'LazyBuilder-Setup-Local.exe'
Copy-Item $InstallerPath $CanonicalInstaller -Force

$PublishedFiles = @($CanonicalInstaller)
if ($DiagnosticExecutablePath) {
    $CanonicalDiagnostic = Join-Path $OutputDir 'LazyBuilder-Diagnostics.exe'
    Copy-Item $DiagnosticExecutablePath $CanonicalDiagnostic -Force
    $PublishedFiles += $CanonicalDiagnostic
}

# The same five tested JARs bundled in the installer are also published as
# standalone installation artifacts. Reject stale/additional client mods.
$Snapshot = "$Version-SNAPSHOT"
$RuntimeGroups = @(
    [pscustomobject]@{
        SourceDir = (Join-Path $RepoRoot 'apps\launcher\src-tauri\resources\core')
        TargetDir = 'Paper-Plugins'
        Expected = @("World-Manager-$Snapshot.jar", "Utilities-Manager-$Snapshot.jar")
    },
    [pscustomobject]@{
        SourceDir = (Join-Path $RepoRoot 'apps\launcher\src-tauri\resources\client-mods')
        TargetDir = 'Fabric-Mods'
        Expected = @(
            "lazybuilder-map-manager-$Snapshot.jar",
            "lazybuilder-utility-manager-$Snapshot.jar",
            "lazybuilder-performance-manager-$Snapshot.jar"
        )
    }
)
$RuntimeCopies = @()
foreach ($Group in $RuntimeGroups) {
    if (-not (Test-Path -LiteralPath $Group.SourceDir -PathType Container)) {
        throw "Bundled runtime source is missing: $($Group.SourceDir)"
    }
    $Actual = @(Get-ChildItem -LiteralPath $Group.SourceDir -Filter '*.jar' -File | ForEach-Object Name)
    $Unexpected = @($Actual | Where-Object { $_ -notin $Group.Expected })
    $Missing = @($Group.Expected | Where-Object { $_ -notin $Actual })
    if ($Unexpected.Count -gt 0 -or $Missing.Count -gt 0 -or $Actual.Count -ne $Group.Expected.Count) {
        throw "Runtime bundle $($Group.TargetDir) is not the exact expected JAR set. Missing: $($Missing -join ', '); unexpected: $($Unexpected -join ', ')"
    }
    $DestinationDir = Join-Path $OutputDir $Group.TargetDir
    New-Item -ItemType Directory -Force -Path $DestinationDir | Out-Null
    foreach ($Name in $Group.Expected) {
        $Source = Join-Path $Group.SourceDir $Name
        $Destination = Join-Path $DestinationDir $Name
        Copy-Item -LiteralPath $Source -Destination $Destination
        $PublishedFiles += $Destination
        $RuntimeCopies += [pscustomobject]@{ Source = $Source; Destination = $Destination }
    }
}

function Get-Sha256([string]$Path) {
    $stream = [System.IO.File]::OpenRead($Path)
    $hasher = [System.Security.Cryptography.SHA256]::Create()
    try {
        $bytes = $hasher.ComputeHash($stream)
        return ([System.BitConverter]::ToString($bytes)).Replace('-', '').ToLowerInvariant()
    }
    finally {
        $hasher.Dispose()
        $stream.Dispose()
    }
}

# A copy differing from the runtime embedded in the installer is never published.
foreach ($Copy in $RuntimeCopies) {
    if ((Get-Sha256 $Copy.Source) -ne (Get-Sha256 $Copy.Destination)) {
        throw "Runtime artifact mismatch between bundled and distributable JAR: $($Copy.Source)"
    }
}

$Artifacts = @()
$ChecksumLines = @()
foreach ($File in $PublishedFiles) {
    $Name = $File.Substring($OutputDir.Length + 1).Replace('\', '/')
    $Digest = Get-Sha256 $File
    $Artifacts += [ordered]@{
        file = $Name
        sha256 = $Digest
        bytes = (Get-Item $File).Length
    }
    $ChecksumLines += "$Digest  $Name"
}

$Manifest = [ordered]@{
    schemaVersion = 2
    product = 'LazyBuilder'
    channel = 'local'
    version = $Version
    commit = $CommitSha
    shortCommit = $ShortSha
    workflowRun = [string]$RunNumber
    minecraft = '1.21.4'
    generatedUtc = [DateTime]::UtcNow.ToString('o')
    artifacts = $Artifacts
}

$Manifest | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $OutputDir 'build-info.json') -Encoding UTF8
$ChecksumLines | Set-Content (Join-Path $OutputDir 'SHA256SUMS.txt') -Encoding ASCII

$Readme = @(
    'LazyBuilder Local Test Build',
    '',
    'Primary installer:',
    '  LazyBuilder-Setup-Local.exe',
    '',
    $(if ($DiagnosticExecutablePath) { 'Developer diagnostic binary:' } else { $null }),
    $(if ($DiagnosticExecutablePath) { '  LazyBuilder-Diagnostics.exe' } else { $null }),
    $(if ($DiagnosticExecutablePath) { '' } else { $null }),
    "Version: $Version",
    "Commit: $CommitSha",
    "Workflow run: $RunNumber",
    '',
    'This package is for the Local development/test channel. Developer build tools are not required on the test machine.',
    'Standalone plugins: Paper-Plugins/ (Paper 1.21.4). Standalone client mods: Fabric-Mods/ (Fabric Minecraft 1.21.4).',
    'Use the installer for the managed LazyBuilder workspace; do not install Paper JARs into Fabric or Fabric JARs into Paper.',
    'The five standalone JARs are byte-identical to the matching resources bundled into this installer.',
    'build-info.json identifies all packaged binaries; CI may add build-provenance.json with independent verification claims.'
) | Where-Object { $null -ne $_ }
Set-Content (Join-Path $OutputDir 'README.txt') -Value ($Readme -join [Environment]::NewLine) -Encoding UTF8

Write-Host "Local distribution package ready: $OutputDir" -ForegroundColor Green
foreach ($Artifact in $Artifacts) {
    Write-Host "$($Artifact.file) SHA-256: $($Artifact.sha256)"
}
