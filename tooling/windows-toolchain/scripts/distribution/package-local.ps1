param(
    [Parameter(Mandatory=$true)][string]$RepoRoot,
    [Parameter(Mandatory=$true)][string]$InstallerPath,
    [string]$CommitSha = $env:GITHUB_SHA,
    [string]$RunNumber = $env:GITHUB_RUN_NUMBER
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$RepoRoot = (Resolve-Path $RepoRoot).Path
$InstallerPath = (Resolve-Path $InstallerPath).Path
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

$Checksum = Get-Sha256 $CanonicalInstaller
$Manifest = [ordered]@{
    schemaVersion = 1
    product = 'LazyBuilder'
    channel = 'local'
    version = $Version
    commit = $CommitSha
    shortCommit = $ShortSha
    workflowRun = [string]$RunNumber
    installer = 'LazyBuilder-Setup-Local.exe'
    sha256 = $Checksum
    minecraft = '1.21.4'
    generatedUtc = [DateTime]::UtcNow.ToString('o')
}

$Manifest | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $OutputDir 'build-info.json') -Encoding UTF8
"$Checksum  LazyBuilder-Setup-Local.exe" | Set-Content (Join-Path $OutputDir 'SHA256SUMS.txt') -Encoding ASCII

$Readme = @(
    'LazyBuilder Local Test Build',
    '',
    'Run only:',
    '  LazyBuilder-Setup-Local.exe',
    '',
    "Version: $Version",
    "Commit: $CommitSha",
    "Workflow run: $RunNumber",
    '',
    'This package is built for Local testing. Developer build tools are not required on the test machine.'
) -join [Environment]::NewLine
Set-Content (Join-Path $OutputDir 'README.txt') -Value $Readme -Encoding UTF8

Write-Host "Local distribution package ready: $OutputDir" -ForegroundColor Green
Write-Host "Installer SHA-256: $Checksum"
