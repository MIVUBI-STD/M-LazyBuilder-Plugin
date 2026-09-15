param(
    [Parameter(ValueFromRemainingArguments=$true)]
    [string[]]$PassthroughArgs
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..')
$Toolchain = Get-Content (Join-Path $RepoRoot 'toolchain.json') -Raw | ConvertFrom-Json
$Version = [string]$Toolchain.gradle.wrapperTarget
if (-not $Version) { throw 'toolchain.json does not define gradle.wrapperTarget.' }

$LocalBase = if ($env:LOCALAPPDATA) { $env:LOCALAPPDATA } else { $env:TEMP }
$CacheRoot = Join-Path $LocalBase "LazyBuilder\build-tools\gradle\$Version"
$InstallDir = Join-Path $CacheRoot "gradle-$Version"
$Executable = Join-Path $InstallDir 'bin\gradle.bat'
$Archive = Join-Path $CacheRoot "gradle-$Version-bin.zip"
$ChecksumFile = "$Archive.sha256"
$ArchiveUrl = "https://services.gradle.org/distributions/gradle-$Version-bin.zip"
$ChecksumUrl = "$ArchiveUrl.sha256"
$LockPath = Join-Path $CacheRoot '.install.lock'

function Invoke-Download([string]$Uri, [string]$OutFile) {
    Write-Host "Downloading $Uri" -ForegroundColor Cyan
    Invoke-WebRequest -UseBasicParsing -Uri $Uri -OutFile $OutFile
}

function Ensure-Gradle {
    if (Test-Path $Executable) { return }
    New-Item -ItemType Directory -Force -Path $CacheRoot | Out-Null

    $lock = $null
    try {
        $lock = [System.IO.File]::Open($LockPath, 'OpenOrCreate', 'ReadWrite', 'None')
        if (Test-Path $Executable) { return }

        Invoke-Download $ArchiveUrl $Archive
        Invoke-Download $ChecksumUrl $ChecksumFile
        $Expected = ((Get-Content $ChecksumFile -Raw).Trim() -split '\s+')[0].ToLowerInvariant()
        $Actual = (Get-FileHash -Algorithm SHA256 -Path $Archive).Hash.ToLowerInvariant()
        if ($Actual -ne $Expected) {
            Remove-Item $Archive -Force -ErrorAction SilentlyContinue
            throw "Gradle $Version checksum verification failed. Expected $Expected, got $Actual."
        }

        if (Test-Path $InstallDir) { Remove-Item $InstallDir -Recurse -Force }
        Expand-Archive -Path $Archive -DestinationPath $CacheRoot -Force
        if (-not (Test-Path $Executable)) { throw "Gradle extraction completed but executable was not found: $Executable" }
    }
    finally {
        if ($lock) { $lock.Dispose() }
    }
}

Ensure-Gradle
& $Executable @PassthroughArgs
exit $LASTEXITCODE
