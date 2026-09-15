param(
    [Parameter(Mandatory = $true)]
    [string]$ServerJar,

    [string]$RuntimeDirectory = ".runtime-proof/paper-smoke",
    [int]$StartupTimeoutSeconds = 120,
    [int]$ShutdownTimeoutSeconds = 30,
    [int]$ControlPort = 17842,
    [string]$JavaExecutable = "java"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Resolve-RequiredFile([string]$PathValue, [string]$Label) {
    $resolved = Resolve-Path -LiteralPath $PathValue -ErrorAction SilentlyContinue
    if ($null -eq $resolved) { throw "$Label was not found: $PathValue" }
    return $resolved.Path
}

function Stop-Paper([System.Diagnostics.Process]$Process, [int]$TimeoutSeconds) {
    if ($null -eq $Process -or $Process.HasExited) { return }
    try {
        $Process.StandardInput.WriteLine("stop")
        $Process.StandardInput.Flush()
    } catch {
        Write-Warning "Could not send Paper stop command: $($_.Exception.Message)"
    }
    if (-not $Process.WaitForExit($TimeoutSeconds * 1000)) {
        $Process.Kill($true)
        $Process.WaitForExit()
    }
}

function Start-Paper(
    [string]$Java,
    [string]$Jar,
    [string]$WorkingDirectory,
    [string]$Token,
    [int]$Port
) {
    $info = [System.Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $Java
    $info.WorkingDirectory = $WorkingDirectory
    $info.UseShellExecute = $false
    $info.RedirectStandardInput = $true
    $info.RedirectStandardOutput = $false
    $info.RedirectStandardError = $false
    $info.CreateNoWindow = $true
    $info.Environment["LAZYBUILDER_WORLD_CONTROL_TOKEN"] = $Token
    $info.Environment["LAZYBUILDER_WORLD_CONTROL_PORT"] = $Port.ToString()
    $info.ArgumentList.Add("-Xms512M")
    $info.ArgumentList.Add("-Xmx1024M")
    $info.ArgumentList.Add("-jar")
    $info.ArgumentList.Add($Jar)
    $info.ArgumentList.Add("nogui")

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $info
    if (-not $process.Start()) { throw "Paper process could not be started." }
    return $process
}

function Wait-ControlReady(
    [System.Diagnostics.Process]$Process,
    [string]$BaseUri,
    [hashtable]$Headers,
    [int]$TimeoutSeconds
) {
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTimeOffset]::UtcNow -lt $deadline) {
        if ($Process.HasExited) { throw "Paper exited before local control became ready." }
        try {
            $status = Invoke-RestMethod -Method Get -Uri "$BaseUri/v1/status" -Headers $Headers -TimeoutSec 5
            if ($status.status -eq "ready" -and [int]$status.protocolVersion -eq 2) { return }
        } catch {
            Start-Sleep -Milliseconds 500
            continue
        }
        Start-Sleep -Milliseconds 500
    }
    throw "Paper local control did not become ready within $TimeoutSeconds seconds."
}

function Invoke-ControlJson(
    [string]$Method,
    [string]$Uri,
    [hashtable]$Headers,
    [object]$Body = $null,
    [int]$TimeoutSeconds = 30
) {
    $request = @{
        Method = $Method
        Uri = $Uri
        Headers = $Headers
        TimeoutSec = $TimeoutSeconds
    }
    if ($null -ne $Body) {
        $request["ContentType"] = "application/json"
        $request["Body"] = ($Body | ConvertTo-Json -Depth 8 -Compress)
    }
    return Invoke-RestMethod @request
}

function Wait-ControlTask(
    [string]$BaseUri,
    [hashtable]$Headers,
    [string]$TaskId,
    [int]$TimeoutSeconds = 90
) {
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTimeOffset]::UtcNow -lt $deadline) {
        $task = Invoke-ControlJson -Method "GET" -Uri "$BaseUri/v1/tasks/$TaskId" -Headers $Headers
        switch ($task.state) {
            "SUCCEEDED" { return $task }
            "FAILED" { throw "World task $TaskId failed: $($task.error)" }
        }
        Start-Sleep -Milliseconds 250
    }
    throw "World task $TaskId did not finish within $TimeoutSeconds seconds."
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$serverJarPath = Resolve-RequiredFile $ServerJar "Paper server JAR"
$runtimeRoot = [System.IO.Path]::GetFullPath((Join-Path $repoRoot $RuntimeDirectory))
if (-not (Test-Path -LiteralPath $runtimeRoot)) {
    throw "Runtime directory from lifecycle proof is missing: $runtimeRoot"
}

$token = [Guid]::NewGuid().ToString("N")
$headers = @{ Authorization = "Bearer $token" }
$baseUri = "http://127.0.0.1:$ControlPort"
$worldsUri = "$baseUri/v1/worlds"
$process = $null
$restartWorldId = $null

try {
    $process = Start-Paper -Java $JavaExecutable -Jar $serverJarPath -WorkingDirectory $runtimeRoot -Token $token -Port $ControlPort
    Wait-ControlReady -Process $process -BaseUri $baseUri -Headers $headers -TimeoutSeconds $StartupTimeoutSeconds

    $created = Invoke-ControlJson -Method "POST" -Uri $worldsUri -Headers $headers -Body @{
        folderName = "runtime-proof-restart"
        displayName = "Runtime Proof Restart"
        kind = "VOID"
    }
    $restartWorldId = $created.id
    if ([string]::IsNullOrWhiteSpace($restartWorldId)) {
        throw "Restart proof failed: persistent probe world did not receive an id."
    }

    $beforeRestart = Invoke-ControlJson -Method "GET" -Uri $worldsUri -Headers $headers
    $before = @($beforeRestart.worlds | Where-Object { $_.id -eq $restartWorldId })
    if ($before.Count -ne 1) {
        throw "Restart proof failed: probe world was not visible before restart."
    }

    Stop-Paper $process $ShutdownTimeoutSeconds
    $process.Dispose()
    $process = $null

    Start-Sleep -Seconds 2

    $process = Start-Paper -Java $JavaExecutable -Jar $serverJarPath -WorkingDirectory $runtimeRoot -Token $token -Port $ControlPort
    Wait-ControlReady -Process $process -BaseUri $baseUri -Headers $headers -TimeoutSeconds $StartupTimeoutSeconds

    $afterRestart = Invoke-ControlJson -Method "GET" -Uri $worldsUri -Headers $headers
    $after = @($afterRestart.worlds | Where-Object { $_.id -eq $restartWorldId })
    if ($after.Count -ne 1 -or $after[0].displayName -ne "Runtime Proof Restart") {
        throw "Restart proof failed: managed world registry did not survive Paper restart consistently."
    }

    $settings = Invoke-ControlJson -Method "GET" -Uri "$worldsUri/$restartWorldId/settings" -Headers $headers
    if ($settings.id -ne $restartWorldId) {
        throw "Restart proof failed: persisted world could not be resolved through settings after restart."
    }

    $deleteStart = Invoke-ControlJson -Method "POST" -Uri "$baseUri/v1/tasks/delete" -Headers $headers -Body @{
        worldId = $restartWorldId
        typedDisplayName = "Runtime Proof Restart"
    }
    [void](Wait-ControlTask -BaseUri $baseUri -Headers $headers -TaskId $deleteStart.taskId)

    $final = Invoke-ControlJson -Method "GET" -Uri $worldsUri -Headers $headers
    $remaining = @($final.worlds | Where-Object { $_.id -eq $restartWorldId })
    if ($remaining.Count -ne 0) {
        throw "Restart proof failed: deleted restart probe remained registered."
    }

    Write-Host "LazyBuilder Paper restart persistence proof passed."
    Write-Host "Verified: clean shutdown, restart, registry reload, filesystem/world resolution, settings access, post-restart deletion."
}
finally {
    if ($null -ne $process) {
        Stop-Paper $process $ShutdownTimeoutSeconds
        $process.Dispose()
    }
}
