param(
    [string]$RepoRoot
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ($env:OS -ne 'Windows_NT') { throw 'LazyBuilder Fabric verification must run on Windows.' }
if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
    $RepoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..')
} else {
    $RepoRoot = (Resolve-Path $RepoRoot).Path
}

$GradleWrapper = Join-Path $RepoRoot 'gradlew.bat'
if (-not (Test-Path -LiteralPath $GradleWrapper -PathType Leaf)) {
    throw "Repository Gradle wrapper is missing: $GradleWrapper"
}

# Establish the Gradle/Java runtime environment in this verification process so
# DEV.cmd -> dev.ps1 -> verify-fabric.ps1 and direct PowerShell runs are identical.
$GradleEnvironmentScript = Join-Path $RepoRoot 'tooling\windows-toolchain\scripts\wrappers\gradle-environment.ps1'
. $GradleEnvironmentScript
$GradleEnvironment = Set-LazyBuilderGradleEnvironment -RepoRoot $RepoRoot

if ($env:TEMP -ne $GradleEnvironment.Temp -or $env:TMP -ne $GradleEnvironment.Temp) {
    throw 'LazyBuilder Fabric verification did not establish the expected process-local TEMP/TMP path.'
}
if ($env:GRADLE_USER_HOME -ne $GradleEnvironment.GradleUserHome) {
    throw 'LazyBuilder Fabric verification did not establish the expected GRADLE_USER_HOME.'
}
if (-not ([string]$env:GRADLE_OPTS).Contains($GradleEnvironment.JavaTmpOption)) {
    throw 'LazyBuilder Fabric verification did not establish the expected java.io.tmpdir.'
}

$Managers = @(
    @{ Name = 'Map Manager'; Path = 'mods/map-manager' },
    @{ Name = 'Utility Manager'; Path = 'mods/utility-manager' },
    @{ Name = 'Performance Manager'; Path = 'mods/performance-manager' },
    @{ Name = 'Terraform Manager'; Path = 'mods/terraform-manager' },
    @{ Name = 'Builder Utilities'; Path = 'mods/builder-utilities' }
)

Push-Location $RepoRoot
try {
    foreach ($Manager in $Managers) {
        Write-Host "[fabric] Verifying $($Manager.Name)..." -ForegroundColor Cyan
        & $GradleWrapper -p $Manager.Path --no-daemon build
        if ($LASTEXITCODE -ne 0) {
            throw "$($Manager.Name) verification failed with exit code $LASTEXITCODE."
        }
    }
}
finally {
    Pop-Location
}

Write-Host 'PASS: required LazyBuilder Fabric client suite verified.' -ForegroundColor Green
