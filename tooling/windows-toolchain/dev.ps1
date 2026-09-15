param(
    [Parameter(Position = 0)]
    [ValidateSet('help','setup','check','build','test','update','finalize-local')]
    [string]$Command = 'help'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

$Operations = @{
    setup = Join-Path $PSScriptRoot 'scripts\bootstrap\setup-dev.ps1'
    check = Join-Path $PSScriptRoot 'scripts\bootstrap\check-tools.ps1'
    build = Join-Path $RepoRoot 'apps\launcher\build-local.ps1'
    test = Join-Path $PSScriptRoot 'scripts\verify\test-local.ps1'
}

function Invoke-Operation([string]$Name) {
    $Path = $Operations[$Name]
    if (-not $Path -or -not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "LazyBuilder operation is unavailable: $Name"
    }
    & $Path
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

switch ($Command) {
    'help' {
        Write-Host 'LazyBuilder Developer CLI'
        Write-Host 'Commands: setup, check, build, test, update, finalize-local'
    }
    'setup' { Invoke-Operation 'setup' }
    'check' { Invoke-Operation 'check' }
    'build' { Invoke-Operation 'build' }
    'test' { Invoke-Operation 'test' }
    'update' {
        & $Operations['build'] -UpdateInstalled
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
    'finalize-local' {
        Invoke-Operation 'check'
        Invoke-Operation 'build'
        Invoke-Operation 'test'
        Write-Host 'LOCAL FINALIZATION PASS'
    }
}
