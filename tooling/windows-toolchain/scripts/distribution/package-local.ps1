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

$Artifacts = @()
$ChecksumLines = @()
foreach ($File in $PublishedFiles) {
    $Name = Split-Path $File -Leaf
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
    'build-info.json identifies package contents; CI may add build-provenance.json with independent verification claims.'
) | Where-Object { $null -ne $_ }
Set-Content (Join-Path $OutputDir 'README.txt') -Value ($Readme -join [Environment]::NewLine) -Encoding UTF8

Write-Host "Local distribution package ready: $OutputDir" -ForegroundColor Green
foreach ($Artifact in $Artifacts) {
    Write-Host "$($Artifact.file) SHA-256: $($Artifact.sha256)"
}
