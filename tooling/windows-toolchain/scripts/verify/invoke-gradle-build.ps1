param(
    [Parameter(Mandatory = $true)][string]$RepoRoot,
    [Parameter(Mandatory = $true)][string]$ProjectPath,
    [string[]]$Tasks = @('build'),
    [int]$MaxAttempts = 3
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$RepoRoot = (Resolve-Path $RepoRoot).Path
$GradleWrapper = Join-Path $RepoRoot 'gradlew.bat'
if (-not (Test-Path -LiteralPath $GradleWrapper -PathType Leaf)) {
    throw "Repository Gradle wrapper is missing: $GradleWrapper"
}
if ($MaxAttempts -lt 1) { throw 'MaxAttempts must be at least 1.' }

$ProjectFullPath = Join-Path $RepoRoot $ProjectPath
if (-not (Test-Path -LiteralPath $ProjectFullPath -PathType Container)) {
    throw "Gradle project path does not exist: $ProjectFullPath"
}

$TransientPattern = '(?i)(HTTP\s*)?(429|500|502|503|504)\b|Too Many Requests|Gateway Time-out|timed? out|timeout|connection reset|connection refused|temporary failure|remote host terminated'
$LastExitCode = 1

Push-Location $RepoRoot
try {
    for ($Attempt = 1; $Attempt -le $MaxAttempts; $Attempt++) {
        $LogPath = Join-Path $env:TEMP "lazybuilder-gradle-$PID-$Attempt.log"
        Remove-Item $LogPath -Force -ErrorAction SilentlyContinue

        Write-Host "[gradle] $ProjectPath attempt $Attempt/$MaxAttempts" -ForegroundColor Cyan
        & $GradleWrapper -p $ProjectPath --no-daemon @Tasks *> $LogPath
        $LastExitCode = $LASTEXITCODE

        if (Test-Path $LogPath) {
            Get-Content $LogPath | ForEach-Object { Write-Host $_ }
        }

        $Output = if (Test-Path $LogPath) { Get-Content $LogPath -Raw } else { '' }

        if ($LastExitCode -eq 0) {
            Remove-Item $LogPath -Force -ErrorAction SilentlyContinue
            return
        }

        # Windows batch/PowerShell nesting has occasionally surfaced a stale non-zero native
        # exit code even though Gradle itself completed every requested task successfully.
        # Accept only Gradle's explicit terminal success marker, and only when no failure marker
        # exists. This is deliberately narrow so real compile/test failures remain authoritative.
        $GradleReportedSuccess = $Output -match '(?m)^BUILD SUCCESSFUL(?:\s|$)'
        $GradleReportedFailure = $Output -match '(?m)^BUILD FAILED(?:\s|$)'
        if ($GradleReportedSuccess -and -not $GradleReportedFailure) {
            Write-Warning "Gradle reported BUILD SUCCESSFUL but wrapper exit code was $LastExitCode; accepting explicit Gradle success."
            Remove-Item $LogPath -Force -ErrorAction SilentlyContinue
            return
        }

        $Transient = $Output -match $TransientPattern
        Remove-Item $LogPath -Force -ErrorAction SilentlyContinue

        if (-not $Transient -or $Attempt -ge $MaxAttempts) {
            break
        }

        $DelaySeconds = 5 * $Attempt
        Write-Warning "Transient Gradle dependency/network failure detected; retrying in $DelaySeconds seconds."
        Start-Sleep -Seconds $DelaySeconds
    }
}
finally {
    Pop-Location
}

throw "Gradle verification for '$ProjectPath' failed with exit code $LastExitCode."
