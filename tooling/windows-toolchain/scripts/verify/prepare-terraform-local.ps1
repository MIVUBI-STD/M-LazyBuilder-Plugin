param(
    [string]$RepoRoot
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ($env:OS -ne 'Windows_NT') { throw 'LazyBuilder Terraform local preparation must run on Windows.' }
if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..')).Path
} else {
    $RepoRoot = (Resolve-Path $RepoRoot).Path
}

$MavenWrapper = Join-Path $RepoRoot 'mvnw.cmd'
$GradleWrapper = Join-Path $RepoRoot 'gradlew.bat'
$PaperJar = Join-Path $RepoRoot 'plugins\terraform-manager\target\Terraform-Manager-0.1.0-SNAPSHOT.jar'
$FabricJar = Join-Path $RepoRoot 'mods\terraform-manager\build\libs\lazybuilder-terraform-manager-0.1.0-SNAPSHOT.jar'
$StageRoot = Join-Path $RepoRoot 'dist\Terraform-Test'
$ServerStage = Join-Path $StageRoot 'server\plugins'
$ClientStage = Join-Path $StageRoot 'client\mods'

function Require-File([string]$Path, [string]$Label) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "$Label was not produced: $Path"
    }
}

function Assert-JarEntry([string]$JarPath, [string]$EntryName) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($JarPath)
    try {
        if ($null -eq $zip.GetEntry($EntryName)) {
            throw "Required JAR entry '$EntryName' is missing from $JarPath"
        }
    }
    finally {
        $zip.Dispose()
    }
}

Write-Host ''
Write-Host 'LazyBuilder Terraform - Local Preparation' -ForegroundColor Cyan
Write-Host "Repository: $RepoRoot"
Write-Host ''

Require-File $MavenWrapper 'Repository Maven wrapper'
Require-File $GradleWrapper 'Repository Gradle wrapper'

Write-Host '[1/4] Verifying shared Terraform core, protocol, and Paper plugin...' -ForegroundColor Cyan
Push-Location $RepoRoot
try {
    & $MavenWrapper --batch-mode --no-transfer-progress -pl shared/protocol,shared/terraform-core,plugins/terraform-manager -am verify
    if ($LASTEXITCODE -ne 0) { throw "Terraform Maven verification failed with exit code $LASTEXITCODE." }
}
finally { Pop-Location }

Write-Host '[2/4] Building Fabric Terraform Manager...' -ForegroundColor Cyan
Push-Location $RepoRoot
try {
    & $GradleWrapper -p 'mods/terraform-manager' --no-daemon build
    if ($LASTEXITCODE -ne 0) { throw "Terraform Fabric build failed with exit code $LASTEXITCODE." }
}
finally { Pop-Location }

Require-File $PaperJar 'Paper Terraform Manager JAR'
Require-File $FabricJar 'Fabric Terraform Manager JAR'

Write-Host '[3/4] Checking packaged descriptors...' -ForegroundColor Cyan
Assert-JarEntry $PaperJar 'plugin.yml'
Assert-JarEntry $FabricJar 'fabric.mod.json'
Assert-JarEntry $FabricJar 'lazybuilder-terraform-manager.mixins.json'

Write-Host '[4/4] Staging exact local-test artifacts...' -ForegroundColor Cyan
if (Test-Path -LiteralPath $StageRoot) { Remove-Item $StageRoot -Recurse -Force }
New-Item -ItemType Directory -Path $ServerStage -Force | Out-Null
New-Item -ItemType Directory -Path $ClientStage -Force | Out-Null
Copy-Item $PaperJar (Join-Path $ServerStage (Split-Path $PaperJar -Leaf)) -Force
Copy-Item $FabricJar (Join-Path $ClientStage (Split-Path $FabricJar -Leaf)) -Force

$readme = @(
    'LazyBuilder Terraform local test artifacts',
    '',
    'Server plugin:',
    '  server/plugins/Terraform-Manager-0.1.0-SNAPSHOT.jar',
    '',
    'Client mod:',
    '  client/mods/lazybuilder-terraform-manager-0.1.0-SNAPSHOT.jar',
    '',
    'Target runtime:',
    '  Minecraft Java 1.21.4',
    '  Fabric Loader >= 0.16.10',
    '  Fabric API 0.119.4+1.21.4',
    '  Java 21',
    '',
    'First runtime acceptance:',
    '  1. Start Paper with the Terraform Manager plugin installed.',
    '  2. Start Minecraft 1.21.4 Fabric with the Terraform Manager mod installed.',
    '  3. Join using an operator account or grant lazybuilder.terraform.use.',
    '  4. Press Right Shift to open Terraform.',
    '  5. Test Cliff, Ridge, and Mountain with Smooth, Balanced, and Rugged.',
    '  6. Verify hover preview, LMB draw/apply, RMB flip, wheel size, Shift+wheel height, and Undo.',
    '',
    'Do not promote to main until compile, load, client-server, and visual terrain behavior are confirmed.'
)
Set-Content -Path (Join-Path $StageRoot 'README.txt') -Value $readme -Encoding UTF8

Write-Host ''
Write-Host 'PASS: Terraform compile/package preparation completed.' -ForegroundColor Green
Write-Host "Paper JAR : $PaperJar"
Write-Host "Fabric JAR: $FabricJar"
Write-Host "Test stage: $StageRoot" -ForegroundColor Green
