param(
    [Parameter(Position = 0)]
    [ValidateSet('help','setup','check','build','test','update','finalize-local')]
    [string]$Command = 'help',

    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$CommandArgs = @()
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ($env:OS -ne 'Windows_NT') {
    throw 'LazyBuilder developer operations are supported on Windows only.'
}

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$PowerShellExe = (Get-Command 'powershell.exe' -ErrorAction Stop).Source

$Operations = @{
    setup = Join-Path $PSScriptRoot 'scripts\bootstrap\setup-dev.ps1'
    check = Join-Path $PSScriptRoot 'scripts\bootstrap\check-tools.ps1'
    build = Join-Path $RepoRoot 'apps\launcher\build-local.ps1'
    test  = Join-Path $PSScriptRoot 'scripts\verify\test-local.ps1'
}

$OperationHints = @{
    setup = @{
        Evidence = 'toolchain.json + terminal output from the failed installer/tool check'
        Recovery = 'DEV.cmd setup  (or DEV.cmd check after opening a fresh terminal if PATH/tool installation just changed)'
    }
    check = @{
        Evidence = 'toolchain.json + failed row in the LazyBuilder Toolchain Check table'
        Recovery = 'DEV.cmd setup'
    }
    build = @{
        Evidence = 'first failing Maven/Gradle/npm/Cargo/Tauri step; package outputs are under dist/ only after successful publication'
        Recovery = 'Fix the first failing owner, then run DEV.cmd build again'
    }
    test = @{
        Evidence = '.runtime-proof/ for Paper proof state/logs; dist/Local/ for installer/package inputs'
        Recovery = 'Fix the first failing proof lane, then run DEV.cmd test again (use DEV.cmd test -Build if build outputs are stale/missing)'
    }
}

function Write-FailureSummary {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][int]$ExitCode
    )

    $Hint = $OperationHints[$Name]
    Write-Host ''
    Write-Host 'LAZYBUILDER OPERATION FAILED' -ForegroundColor Red
    Write-Host "Operation : $Name"
    Write-Host "Exit code : $ExitCode"
    if ($Hint) {
        Write-Host "Evidence  : $($Hint.Evidence)"
        Write-Host "Recovery  : $($Hint.Recovery)"
    }
    Write-Host 'Rule      : fix the first actionable failure; do not add a fallback path merely to bypass it.' -ForegroundColor DarkGray
}

function Invoke-Operation {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [string[]]$Arguments = @()
    )

    $Path = $Operations[$Name]
    if (-not $Path -or -not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "LazyBuilder operation is unavailable: $Name"
    }

    Write-Host ''
    Write-Host "==> $Name" -ForegroundColor Cyan

    & $PowerShellExe -NoProfile -ExecutionPolicy Bypass -File $Path @Arguments
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        Write-FailureSummary -Name $Name -ExitCode $exitCode
        throw "LazyBuilder operation '$Name' failed with exit code $exitCode."
    }
}

function Show-Help {
    Write-Host 'LazyBuilder Developer CLI' -ForegroundColor Cyan
    Write-Host ''
    Write-Host 'Usage:'
    Write-Host '  DEV.cmd <command> [arguments]'
    Write-Host ''
    Write-Host 'Commands:'
    Write-Host '  setup           Bootstrap/repair the supported Windows developer environment'
    Write-Host '  check           Validate toolchain and repository build prerequisites'
    Write-Host '  build           Build/test/package the runtime-ready Launcher and managed components'
    Write-Host '  test            Run local runtime/installer acceptance using current build outputs'
    Write-Host '  update          Build and update the installed Local Launcher'
    Write-Host '  finalize-local  Run check -> build -> test as the local pre-CI acceptance gate'
    Write-Host '  help            Show this command reference'
    Write-Host ''
    Write-Host 'Arguments after the command are forwarded to that operation.'
    Write-Host 'Failures stop at the first failed operation and print evidence/recovery guidance.'
}

switch ($Command) {
    'help' {
        Show-Help
    }
    'setup' {
        Invoke-Operation -Name 'setup' -Arguments $CommandArgs
    }
    'check' {
        Invoke-Operation -Name 'check' -Arguments $CommandArgs
    }
    'build' {
        Invoke-Operation -Name 'build' -Arguments $CommandArgs
    }
    'test' {
        Invoke-Operation -Name 'test' -Arguments $CommandArgs
    }
    'update' {
        Invoke-Operation -Name 'build' -Arguments (@('-UpdateInstalled') + $CommandArgs)
    }
    'finalize-local' {
        if ($CommandArgs.Count -gt 0) {
            throw 'finalize-local does not accept passthrough arguments. Run the individual command when custom flags are required.'
        }
        Invoke-Operation -Name 'check'
        Invoke-Operation -Name 'build'
        Invoke-Operation -Name 'test'
        Write-Host ''
        Write-Host 'LOCAL FINALIZATION PASS' -ForegroundColor Green
    }
}
