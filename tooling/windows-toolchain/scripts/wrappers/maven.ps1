$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$PassthroughArgs = @($args)

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..')
$Toolchain = Get-Content (Join-Path $RepoRoot 'toolchain.json') -Raw | ConvertFrom-Json
$Version = [string]$Toolchain.maven.wrapperTarget
if (-not $Version) { throw 'toolchain.json does not define maven.wrapperTarget.' }

$LocalBase = if ($env:LOCALAPPDATA) { $env:LOCALAPPDATA } else { $env:TEMP }
$CacheRoot = Join-Path $LocalBase "LazyBuilder\build-tools\maven\$Version"
$InstallDir = Join-Path $CacheRoot "apache-maven-$Version"
$Executable = Join-Path $InstallDir 'bin\mvn.cmd'
$Archive = Join-Path $CacheRoot "apache-maven-$Version-bin.zip"
$ChecksumFile = "$Archive.sha512"
$BaseUrl = "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$Version"
$ArchiveUrl = "$BaseUrl/apache-maven-$Version-bin.zip"
$ChecksumUrl = "$ArchiveUrl.sha512"
$LockPath = Join-Path $CacheRoot '.install.lock'

function Invoke-Download([string]$Uri, [string]$OutFile) {
    Write-Host "Downloading $Uri" -ForegroundColor Cyan
    Invoke-WebRequest -UseBasicParsing -Uri $Uri -OutFile $OutFile
}

function Ensure-Maven {
    if (Test-Path $Executable) { return }
    New-Item -ItemType Directory -Force -Path $CacheRoot | Out-Null

    $lock = $null
    try {
        $lock = [System.IO.File]::Open($LockPath, 'OpenOrCreate', 'ReadWrite', 'None')
        if (Test-Path $Executable) { return }

        Invoke-Download $ArchiveUrl $Archive
        Invoke-Download $ChecksumUrl $ChecksumFile
        $Expected = ((Get-Content $ChecksumFile -Raw).Trim() -split '\s+')[0].ToLowerInvariant()
        $Actual = (Get-FileHash -Algorithm SHA512 -Path $Archive).Hash.ToLowerInvariant()
        if ($Actual -ne $Expected) {
            Remove-Item $Archive -Force -ErrorAction SilentlyContinue
            throw "Maven $Version checksum verification failed. Expected $Expected, got $Actual."
        }

        if (Test-Path $InstallDir) { Remove-Item $InstallDir -Recurse -Force }
        Expand-Archive -Path $Archive -DestinationPath $CacheRoot -Force
        if (-not (Test-Path $Executable)) { throw "Maven extraction completed but executable was not found: $Executable" }
    }
    finally {
        if ($lock) { $lock.Dispose() }
    }
}

Ensure-Maven
& $Executable @PassthroughArgs
exit $LASTEXITCODE
