param(
    [Parameter(Mandatory = $true)]
    [string]$ServerJar,

    [string]$WorldManagerJar = "plugins/world-manager/target/World-Manager-0.1.0-SNAPSHOT.jar",
    [string]$UtilitiesManagerJar = "plugins/utilities-manager/target/Utilities-Manager-0.1.0-SNAPSHOT.jar",
    [string]$RuntimeDirectory = ".runtime-proof/paper-multi-instance",
    [int]$StartupTimeoutSeconds = 120,
    [int]$ShutdownTimeoutSeconds = 30,
    [int[]]$PaperPorts = @(25601, 25602, 25603),
    [int[]]$ControlPorts = @(17851, 17852, 17853),
    [string]$JavaExecutable = "java"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Resolve-RequiredFile([string]$PathValue, [string]$Label) {
    $resolved = Resolve-Path -LiteralPath $PathValue -ErrorAction SilentlyContinue
    if ($null -eq $resolved) {
        throw "$Label was not found: $PathValue"
    }
    return $resolved.Path
}

function Read-LatestPaperLog([string]$RuntimeRoot) {
    $logPath = Join-Path $RuntimeRoot "logs/latest.log"
    if (-not (Test-Path -LiteralPath $logPath)) { return "" }
    try { return Get-Content -LiteralPath $logPath -Raw -ErrorAction Stop } catch { return "" }
}

function Stop-PaperInstance([System.Diagnostics.Process]$Process, [string]$InstanceName, [int]$TimeoutSeconds) {
    if ($Process.HasExited) { return }
    try {
        $Process.StandardInput.WriteLine("stop")
        $Process.StandardInput.Flush()
    } catch {
        Write-Warning "Could not send stop to $InstanceName: $($_.Exception.Message)"
    }
    if (-not $Process.WaitForExit($TimeoutSeconds * 1000)) {
        Write-Warning "$InstanceName did not stop cleanly within $TimeoutSeconds seconds; terminating the disposable process."
        $Process.Kill($true)
        $Process.WaitForExit()
    }
}

function Wait-PaperReady([System.Diagnostics.Process]$Process, [string]$RuntimeRoot, [string]$InstanceName, [int]$TimeoutSeconds) {
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTimeOffset]::UtcNow -lt $deadline) {
        if ($Process.HasExited) { break }
        $log = Read-LatestPaperLog $RuntimeRoot
        if ($log -match "Done \(.+\)! For help") { return }
        Start-Sleep -Milliseconds 500
    }
    throw "$InstanceName did not reach Paper ready state within $TimeoutSeconds seconds. Latest log:`n$(Read-LatestPaperLog $RuntimeRoot)"
}

function Get-AuthenticatedStatus([int]$ControlPort, [string]$Token) {
    $headers = @{ Authorization = "Bearer $Token" }
    return Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:$ControlPort/v1/status" -Headers $headers -TimeoutSec 10
}

function Assert-Unauthorized([int]$ControlPort, [string]$Token, [string]$Label) {
    try {
        Invoke-WebRequest -Method Get -Uri "http://127.0.0.1:$ControlPort/v1/status" -Headers @{ Authorization = "Bearer $Token" } -TimeoutSec 10 -ErrorAction Stop | Out-Null
    } catch {
        if ($null -ne $_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 401) { return }
        throw
    }
    throw "$Label accepted a token that belongs to another Paper instance."
}

if ($PaperPorts.Count -ne 3 -or $ControlPorts.Count -ne 3) {
    throw "Paper multi-instance runtime proof requires exactly three Paper ports and three World Manager control ports."
}
if (($PaperPorts | Select-Object -Unique).Count -ne 3) {
    throw "Paper multi-instance runtime proof requires three distinct Paper ports."
}
if (($ControlPorts | Select-Object -Unique).Count -ne 3) {
    throw "Paper multi-instance runtime proof requires three distinct World Manager control ports."
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Push-Location $repoRoot

$instances = @()
try {
    $serverJarPath = Resolve-RequiredFile $ServerJar "Paper server JAR"
    $worldManagerJarPath = Resolve-RequiredFile $WorldManagerJar "World-Manager JAR"
    $utilitiesManagerJarPath = Resolve-RequiredFile $UtilitiesManagerJar "Utilities-Manager JAR"

    $runtimeBase = [System.IO.Path]::GetFullPath((Join-Path $repoRoot $RuntimeDirectory))
    if (Test-Path -LiteralPath $runtimeBase) {
        Remove-Item -LiteralPath $runtimeBase -Recurse -Force
    }
    New-Item -ItemType Directory -Path $runtimeBase -Force | Out-Null

    for ($index = 0; $index -lt 3; $index++) {
        $instanceNumber = $index + 1
        $instanceName = "paper-instance-$instanceNumber"
        $runtimeRoot = Join-Path $runtimeBase $instanceName
        $pluginsDir = Join-Path $runtimeRoot "plugins"
        New-Item -ItemType Directory -Path $pluginsDir -Force | Out-Null

        Copy-Item -LiteralPath $worldManagerJarPath -Destination (Join-Path $pluginsDir "World-Manager.jar")
        Copy-Item -LiteralPath $utilitiesManagerJarPath -Destination (Join-Path $pluginsDir "Utilities-Manager.jar")
        Set-Content -LiteralPath (Join-Path $runtimeRoot "eula.txt") -Value "eula=true" -Encoding ascii
        @(
            "server-port=$($PaperPorts[$index])",
            "online-mode=false",
            "enable-rcon=false",
            "enable-query=false",
            "spawn-protection=0",
            "max-players=1",
            "motd=LazyBuilder Paper multi-instance runtime proof $instanceNumber"
        ) | Set-Content -LiteralPath (Join-Path $runtimeRoot "server.properties") -Encoding ascii

        $controlToken = [Guid]::NewGuid().ToString("N")
        $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
        $startInfo.FileName = $JavaExecutable
        $startInfo.WorkingDirectory = $runtimeRoot
        $startInfo.UseShellExecute = $false
        $startInfo.RedirectStandardInput = $true
        $startInfo.RedirectStandardOutput = $false
        $startInfo.RedirectStandardError = $false
        $startInfo.CreateNoWindow = $true
        $startInfo.Environment["LAZYBUILDER_WORLD_CONTROL_TOKEN"] = $controlToken
        $startInfo.Environment["LAZYBUILDER_WORLD_CONTROL_PORT"] = $ControlPorts[$index].ToString()
        $startInfo.Arguments = "-Xms384M -Xmx768M -jar `"$serverJarPath`" --port $($PaperPorts[$index]) nogui"

        $process = [System.Diagnostics.Process]::new()
        $process.StartInfo = $startInfo
        if (-not $process.Start()) {
            throw "$instanceName could not be started."
        }

        $instance = [pscustomobject]@{
            Name = $instanceName
            RuntimeRoot = $runtimeRoot
            Process = $process
            PaperPort = $PaperPorts[$index]
            ControlPort = $ControlPorts[$index]
            ControlToken = $controlToken
        }
        $instances += $instance

        Wait-PaperReady -Process $process -RuntimeRoot $runtimeRoot -InstanceName $instanceName -TimeoutSeconds $StartupTimeoutSeconds

        $log = Read-LatestPaperLog $runtimeRoot
        foreach ($signal in @("World-Manager enabled.", "Utilities-Manager enabled")) {
            if (-not $log.Contains($signal)) {
                throw "$instanceName is missing required startup signal: $signal"
            }
        }
        $status = Get-AuthenticatedStatus -ControlPort $instance.ControlPort -Token $instance.ControlToken
        if ($status.status -ne "ready" -or [int]$status.protocolVersion -ne 2) {
            throw "$instanceName returned an unexpected World Manager control status contract."
        }

        foreach ($running in $instances) {
            if ($running.Process.HasExited) {
                throw "$($running.Name) exited while bringing $instanceName online."
            }
        }
        Write-Host "$instanceName ready: Paper :$($instance.PaperPort), World Manager control :$($instance.ControlPort)"
    }

    for ($sourceIndex = 0; $sourceIndex -lt $instances.Count; $sourceIndex++) {
        for ($targetIndex = 0; $targetIndex -lt $instances.Count; $targetIndex++) {
            if ($sourceIndex -eq $targetIndex) { continue }
            Assert-Unauthorized `
                -ControlPort $instances[$targetIndex].ControlPort `
                -Token $instances[$sourceIndex].ControlToken `
                -Label "$($instances[$targetIndex].Name) World Manager control endpoint"
        }
    }

    $middle = $instances[1]
    Stop-PaperInstance -Process $middle.Process -InstanceName $middle.Name -TimeoutSeconds $ShutdownTimeoutSeconds
    foreach ($survivorIndex in @(0, 2)) {
        $survivor = $instances[$survivorIndex]
        if ($survivor.Process.HasExited) {
            throw "$($survivor.Name) exited when $($middle.Name) stopped."
        }
        $status = Get-AuthenticatedStatus -ControlPort $survivor.ControlPort -Token $survivor.ControlToken
        if ($status.status -ne "ready") {
            throw "$($survivor.Name) control endpoint stopped being ready after $($middle.Name) shutdown."
        }
    }

    Write-Host ""
    Write-Host "LazyBuilder Paper multi-instance runtime proof passed."
    Write-Host "Verified: sequential 1 -> 2 -> 3 Paper coexistence, distinct Paper ports, distinct authenticated World Manager control ports, cross-token rejection, and isolated shutdown of the middle instance."
    Write-Host "This proof validates Paper multi-instance coexistence only; Launcher runtime ownership and the three-server capacity ceiling remain separate Launcher contracts."
    Write-Host "Runtime directory: $runtimeBase"
}
finally {
    foreach ($instance in $instances) {
        if ($null -ne $instance.Process) {
            Stop-PaperInstance -Process $instance.Process -InstanceName $instance.Name -TimeoutSeconds $ShutdownTimeoutSeconds
            $instance.Process.Dispose()
        }
    }
    Pop-Location
}
