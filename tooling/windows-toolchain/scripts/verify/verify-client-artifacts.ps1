param(
    [Parameter(Mandatory=$true)][string]$ClientModsDir,
    [string]$RepoRoot
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Add-Type -AssemblyName System.IO.Compression.FileSystem

if (-not $RepoRoot) { $RepoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..') }
$RepoRoot = Resolve-Path $RepoRoot
$ClientModsDir = Resolve-Path $ClientModsDir
$ProductVersion = (Get-Content (Join-Path $RepoRoot 'VERSION') -Raw).Trim()
$SnapshotVersion = "$ProductVersion-SNAPSHOT"

$Expected = @(
    [pscustomobject]@{ File="lazybuilder-map-manager-$SnapshotVersion.jar"; Id='lazybuilder_map_manager'; Name='LazyBuilder Map Manager' },
    [pscustomobject]@{ File="lazybuilder-utility-manager-$SnapshotVersion.jar"; Id='lazybuilder_utility_manager'; Name='LazyBuilder Utility Manager' },
    [pscustomobject]@{ File="lazybuilder-performance-manager-$SnapshotVersion.jar"; Id='lazybuilder_performance_manager'; Name='LazyBuilder Performance Manager' }
)
$Terraform = [pscustomobject]@{ File="lazybuilder-terraform-manager-$SnapshotVersion.jar"; Id='lazybuilder-terraform-manager'; Name='LazyBuilder Terraform Manager' }

function Fail([string]$Message) { throw "Client artifact verification failed: $Message" }

function Read-ZipEntryText($Zip, [string]$EntryName) {
    $entry = $Zip.GetEntry($EntryName)
    if (-not $entry) { return $null }
    $reader = [System.IO.StreamReader]::new($entry.Open(), [System.Text.Encoding]::UTF8)
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
}

function Get-ClientEntrypoints($Metadata) {
    $values = @()
    $entrypointsProperty = $Metadata.PSObject.Properties['entrypoints']
    if (-not $entrypointsProperty) { return @($values) }
    $entrypoints = $entrypointsProperty.Value
    if ($null -eq $entrypoints) { return @($values) }
    $clientProperty = $entrypoints.PSObject.Properties['client']
    if (-not $clientProperty) { return @($values) }
    foreach ($entry in @($clientProperty.Value)) {
        if ($entry -is [string]) { $values += $entry }
        elseif ($entry -and $entry.PSObject.Properties['value'] -and $entry.value -is [string]) { $values += $entry.value }
    }
    return @($values)
}

function Get-MixinConfigs($Metadata) {
    $values = @()
    $mixinsProperty = $Metadata.PSObject.Properties['mixins']
    if (-not $mixinsProperty) { return @($values) }
    foreach ($entry in @($mixinsProperty.Value)) {
        if ($entry -is [string]) { $values += $entry }
        elseif ($entry -and $entry.PSObject.Properties['config'] -and $entry.config -is [string]) { $values += $entry.config }
    }
    return @($values)
}

function Verify-Jar($Spec) {
    $path = Join-Path $ClientModsDir $Spec.File
    if (-not (Test-Path $path)) { Fail "missing required JAR: $($Spec.File)" }

    $zip = $null
    try {
        $zip = [System.IO.Compression.ZipFile]::OpenRead($path)
        $metadataText = Read-ZipEntryText $zip 'fabric.mod.json'
        if (-not $metadataText) { Fail "$($Spec.File) has no fabric.mod.json" }
        $metadata = $metadataText | ConvertFrom-Json

        if ([string]$metadata.id -ne $Spec.Id) { Fail "$($Spec.File) id is '$($metadata.id)', expected '$($Spec.Id)'" }
        if ([string]$metadata.name -ne $Spec.Name) { Fail "$($Spec.File) name is '$($metadata.name)', expected '$($Spec.Name)'" }
        if ([string]$metadata.version -ne $SnapshotVersion) { Fail "$($Spec.File) version is '$($metadata.version)', expected '$SnapshotVersion'" }
        if ([string]$metadata.environment -ne 'client') { Fail "$($Spec.File) must remain client-only" }

        $entrypoints = @(Get-ClientEntrypoints $metadata)
        if ($entrypoints.Count -eq 0) { Fail "$($Spec.File) has no client entrypoint" }
        foreach ($value in $entrypoints) {
            $className = ([string]$value -split '::', 2)[0]
            $classPath = ($className -replace '\.', '/') + '.class'
            if (-not $zip.GetEntry($classPath)) { Fail "$($Spec.File) entrypoint class is missing: $classPath" }
        }

        foreach ($config in @(Get-MixinConfigs $metadata)) {
            if (-not $zip.GetEntry([string]$config)) { Fail "$($Spec.File) mixin config is missing: $config" }
        }

        if ($Spec.Id -eq 'lazybuilder-terraform-manager') {
            if ([string]$metadata.depends.fabricloader -ne '>=0.16.10') { Fail "$($Spec.File) Fabric Loader contract drifted" }
            if ([string]$metadata.depends.minecraft -ne '1.21.4') { Fail "$($Spec.File) Minecraft contract drifted" }
            if ([string]$metadata.depends.java -ne '>=21') { Fail "$($Spec.File) Java contract drifted" }
        }
    }
    catch [System.IO.InvalidDataException] {
        Fail "$($Spec.File) is not a valid JAR/ZIP: $($_.Exception.Message)"
    }
    finally {
        if ($zip) { $zip.Dispose() }
    }
}

$actual = @(Get-ChildItem $ClientModsDir -Filter '*.jar' -File | ForEach-Object Name | Sort-Object)
$expectedNames = @($Expected | ForEach-Object File | Sort-Object)
$missing = @($expectedNames | Where-Object { $_ -notin $actual })
$allowed = @($expectedNames + $Terraform.File)
$unexpected = @($actual | Where-Object { $_ -notin $allowed })
if ($missing.Count -gt 0) { Fail "missing required JARs: $($missing -join ', ')" }
if ($unexpected.Count -gt 0) { Fail "unexpected client JARs: $($unexpected -join ', ')" }

foreach ($spec in $Expected) { Verify-Jar $spec }
$terraformPath = Join-Path $ClientModsDir $Terraform.File
if (Test-Path $terraformPath) { Verify-Jar $Terraform }
Write-Host "Client artifacts OK: required suite verified for LazyBuilder $ProductVersion" -ForegroundColor Green
