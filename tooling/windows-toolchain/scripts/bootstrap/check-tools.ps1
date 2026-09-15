param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..')
$ConfigPath = Join-Path $RepoRoot 'toolchain.json'
if (-not (Test-Path $ConfigPath)) { throw "Missing toolchain manifest: $ConfigPath" }
$T = Get-Content $ConfigPath -Raw | ConvertFrom-Json

if ([int]$T.schemaVersion -ne 2) {
    throw "Unsupported toolchain.json schemaVersion '$($T.schemaVersion)'. Expected schemaVersion 2."
}
foreach ($Required in @('java','node','rust','maven','gradle','msvc','git','python')) {
    if (-not ($T.PSObject.Properties.Name -contains $Required)) {
        throw "toolchain.json is missing required policy section '$Required'."
    }
}
if (-not $T.maven.wrapperTarget -or -not $T.gradle.wrapperTarget -or -not $T.rust.toolchain) {
    throw 'toolchain.json is missing a canonical wrapper/toolchain target.'
}

function Invoke-VersionCommand([string]$Command, [string[]]$Args = @('--version')) {
    $cmd = Get-Command $Command -ErrorAction SilentlyContinue
    if (-not $cmd) { return $null }
    try {
        $lines = @(& $Command @Args 2>&1 | ForEach-Object { [string]$_ })
        if ($LASTEXITCODE -ne 0 -and $lines.Count -eq 0) { return $null }
        return ($lines -join "`n").Trim()
    }
    catch { return $null }
}

function Get-SemVerMajor([string]$VersionText) {
    if ([string]::IsNullOrWhiteSpace($VersionText)) { return $null }
    $m = [regex]::Match($VersionText, '(?<!\d)v?(\d+)(?:\.\d+){1,3}')
    if (-not $m.Success) { return $null }
    return [int]$m.Groups[1].Value
}

function Get-JavaMajor([string]$VersionText) {
    if ([string]::IsNullOrWhiteSpace($VersionText)) { return $null }
    $m = [regex]::Match($VersionText, '(?im)version\s+"(?<major>\d+)(?:\.\d+)*')
    if ($m.Success) { return [int]$m.Groups['major'].Value }
    return Get-SemVerMajor $VersionText
}

function Get-RustVersion([string]$VersionText) {
    if ([string]::IsNullOrWhiteSpace($VersionText)) { return $null }
    $m = [regex]::Match($VersionText, '(?im)^rustc\s+(?<version>\d+\.\d+\.\d+)')
    if (-not $m.Success) { return $null }
    return $m.Groups['version'].Value
}

function First-Line([string]$Text) {
    if ([string]::IsNullOrWhiteSpace($Text)) { return 'MISSING' }
    return ($Text -split "`r?`n" | Select-Object -First 1).Trim()
}

function Get-VsWherePath {
    $Candidates = @(
        (Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'),
        (Join-Path $env:ProgramFiles 'Microsoft Visual Studio\Installer\vswhere.exe')
    ) | Where-Object { $_ -and (Test-Path $_) }
    return ($Candidates | Select-Object -First 1)
}

function Get-MsvcStatus {
    $vswhere = Get-VsWherePath
    if (-not $vswhere) { return $null }
    try {
        $installation = (& $vswhere -latest -products * -requires Microsoft.VisualStudio.Workload.VCTools -property installationPath 2>$null | Select-Object -First 1)
        if ([string]::IsNullOrWhiteSpace([string]$installation)) { return $null }
        return [string]$installation
    }
    catch { return $null }
}

$rows = @()

$java = Invoke-VersionCommand 'java' @('-version')
$javaMajor = Get-JavaMajor $java
$rows += [pscustomobject]@{ Tool='Java'; Required="$($T.java.major).x $($T.java.channel)"; Found=(First-Line $java); OK=($javaMajor -eq [int]$T.java.major) }

$node = Invoke-VersionCommand 'node' @('--version')
$nodeMajor = Get-SemVerMajor $node
$rows += [pscustomobject]@{ Tool='Node.js'; Required="$($T.node.major).x $($T.node.channel)"; Found=(First-Line $node); OK=($nodeMajor -eq [int]$T.node.major) }

$npm = Invoke-VersionCommand 'npm' @('--version')
$rows += [pscustomobject]@{ Tool='npm'; Required='bundled with Node'; Found=(First-Line $npm); OK=(-not [string]::IsNullOrWhiteSpace($npm) -and (Get-SemVerMajor $npm) -ne $null) }

$rustc = Invoke-VersionCommand 'rustc' @('--version')
$rustVersion = Get-RustVersion $rustc
$rows += [pscustomobject]@{ Tool='Rust'; Required=[string]$T.rust.toolchain; Found=(First-Line $rustc); OK=($rustVersion -eq [string]$T.rust.toolchain) }

$cargo = Invoke-VersionCommand 'cargo' @('--version')
$rows += [pscustomobject]@{ Tool='Cargo'; Required='from pinned Rust'; Found=(First-Line $cargo); OK=(-not [string]::IsNullOrWhiteSpace($cargo) -and $cargo -match '(?im)^cargo\s+\d+\.\d+\.\d+') }

$git = Invoke-VersionCommand 'git' @('--version')
$rows += [pscustomobject]@{ Tool='Git'; Required=[string]$T.git.product; Found=(First-Line $git); OK=(-not [string]::IsNullOrWhiteSpace($git) -and $git -match '(?i)git version\s+\d+') }

$msvc = Get-MsvcStatus
$rows += [pscustomobject]@{ Tool='MSVC'; Required="$($T.msvc.product) + VCTools"; Found=$(if ($msvc) { $msvc } else { 'MISSING' }); OK=(-not [string]::IsNullOrWhiteSpace($msvc)) }

$mavenWrapper = Join-Path $RepoRoot 'mvnw.cmd'
$gradleWrapper = Join-Path $RepoRoot 'gradlew.bat'
$rows += [pscustomobject]@{ Tool='Maven wrapper'; Required=[string]$T.maven.wrapperTarget; Found=$(if (Test-Path $mavenWrapper) { 'repository wrapper' } else { 'MISSING' }); OK=(Test-Path $mavenWrapper) }
$rows += [pscustomobject]@{ Tool='Gradle wrapper'; Required=[string]$T.gradle.wrapperTarget; Found=$(if (Test-Path $gradleWrapper) { 'repository wrapper' } else { 'MISSING' }); OK=(Test-Path $gradleWrapper) }

Write-Host ''
Write-Host 'LazyBuilder Toolchain Check' -ForegroundColor Cyan
$rows | Format-Table -AutoSize Tool, Required, Found, OK
$failed = @($rows | Where-Object { -not $_.OK })
if ($failed.Count -gt 0) {
    Write-Host "FAILED: $($failed.Count) requirement(s) missing or outside policy." -ForegroundColor Red
    Write-Host ('Failed: ' + (($failed | ForEach-Object { $_.Tool }) -join ', ')) -ForegroundColor Red
    exit 2
}
Write-Host 'PASS: developer toolchain and repository wrappers match policy.' -ForegroundColor Green
exit 0
