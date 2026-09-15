param(
    [Parameter(Mandatory = $true)]
    [string]$ServerJar,

    [string]$WorldManagerJar = "plugins/world-manager/target/World-Manager-0.1.0-SNAPSHOT.jar",
    [string]$UtilitiesManagerJar = "plugins/utilities-manager/target/Utilities-Manager-0.1.0-SNAPSHOT.jar",
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
    if ($null -eq $resolved) {
        throw "$Label was not found: $PathValue"
    }
    return $resolved.Path
}

function Read-LatestPaperLog([string]$RuntimeRoot) {
    $logPath = Join-Path $RuntimeRoot "logs/latest.log"
    if (-not (Test-Path -LiteralPath $logPath)) { return "" }
    try {
        return Get-Content -LiteralPath $logPath -Raw -ErrorAction Stop
    } catch {
        return ""
    }
}

function Stop-SmokeProcess([System.Diagnostics.Process]$Process, [int]$TimeoutSeconds) {
    if ($Process.HasExited) { return }

    try {
        $Process.StandardInput.WriteLine("stop")
        $Process.StandardInput.Flush()
    } catch {
        Write-Warning "Could not send the Paper stop command: $($_.Exception.Message)"
    }

    if (-not $Process.WaitForExit($TimeoutSeconds * 1000)) {
        Write-Warning "Paper did not stop cleanly within $TimeoutSeconds seconds; terminating the disposable process."
        $Process.Kill($true)
        $Process.WaitForExit()
    }
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
Push-Location $repoRoot

$process = $null
try {
    $serverJarPath = Resolve-RequiredFile $ServerJar "Paper server JAR"
    $worldManagerJarPath = Resolve-RequiredFile $WorldManagerJar "World-Manager JAR"
    $utilitiesManagerJarPath = Resolve-RequiredFile $UtilitiesManagerJar "Utilities-Manager JAR"

    $runtimeRoot = [System.IO.Path]::GetFullPath((Join-Path $repoRoot $RuntimeDirectory))
    if (Test-Path -LiteralPath $runtimeRoot) {
        Remove-Item -LiteralPath $runtimeRoot -Recurse -Force
    }

    $pluginsDir = Join-Path $runtimeRoot "plugins"
    New-Item -ItemType Directory -Path $pluginsDir -Force | Out-Null

    Copy-Item -LiteralPath $worldManagerJarPath -Destination (Join-Path $pluginsDir "World-Manager.jar")
    Copy-Item -LiteralPath $utilitiesManagerJarPath -Destination (Join-Path $pluginsDir "Utilities-Manager.jar")

    Set-Content -LiteralPath (Join-Path $runtimeRoot "eula.txt") -Value "eula=true" -Encoding ascii
    @(
        "online-mode=false",
        "enable-rcon=false",
        "enable-query=false",
        "spawn-protection=0",
        "max-players=1",
        "motd=LazyBuilder runtime proof"
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
    $startInfo.Environment["LAZYBUILDER_WORLD_CONTROL_PORT"] = $ControlPort.ToString()
    $startInfo.ArgumentList.Add("-Xms512M")
    $startInfo.ArgumentList.Add("-Xmx1024M")
    $startInfo.ArgumentList.Add("-jar")
    $startInfo.ArgumentList.Add($serverJarPath)
    $startInfo.ArgumentList.Add("nogui")

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo

    if (-not $process.Start()) {
        throw "Paper process could not be started."
    }

    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($StartupTimeoutSeconds)
    $ready = $false
    while ([DateTimeOffset]::UtcNow -lt $deadline) {
        if ($process.HasExited) { break }
        $log = Read-LatestPaperLog $runtimeRoot
        if ($log -match "Done \(.+\)! For help") {
            $ready = $true
            break
        }
        Start-Sleep -Milliseconds 500
    }

    $combinedOutput = Read-LatestPaperLog $runtimeRoot
    if (-not $ready) {
        throw "Paper did not reach ready state within $StartupTimeoutSeconds seconds. Latest log:`n$combinedOutput"
    }

    $requiredSignals = @(
        "World-Manager enabled.",
        "Utilities-Manager enabled"
    )
    foreach ($signal in $requiredSignals) {
        if (-not $combinedOutput.Contains($signal)) {
            throw "Runtime proof failed: expected startup signal was not found: '$signal'"
        }
    }

    $fatalPatterns = @(
        "Error occurred while enabling World-Manager",
        "Error occurred while enabling Utilities-Manager",
        "Could not load 'plugins\\World-Manager.jar'",
        "Could not load 'plugins\\Utilities-Manager.jar'"
    )
    foreach ($pattern in $fatalPatterns) {
        if ($combinedOutput -match $pattern) {
            throw "Runtime proof found a fatal plugin startup error matching: $pattern"
        }
    }

    $headers = @{ Authorization = "Bearer $controlToken" }
    $baseUri = "http://127.0.0.1:$ControlPort"
    $statusUri = "$baseUri/v1/status"
    $worldsUri = "$baseUri/v1/worlds"

    $unauthorizedRejected = $false
    try {
        Invoke-WebRequest -Method Get -Uri $statusUri -TimeoutSec 10 -ErrorAction Stop | Out-Null
    } catch {
        if ($null -ne $_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 401) {
            $unauthorizedRejected = $true
        } else {
            throw
        }
    }
    if (-not $unauthorizedRejected) {
        throw "Runtime proof failed: local control status endpoint accepted an unauthenticated request."
    }

    $status = Invoke-ControlJson -Method "GET" -Uri $statusUri -Headers $headers
    if ($status.status -ne "ready" -or [int]$status.protocolVersion -ne 2) {
        throw "Runtime proof failed: local control status endpoint returned an unexpected contract."
    }

    $initialWorlds = Invoke-ControlJson -Method "GET" -Uri $worldsUri -Headers $headers
    if ($null -eq $initialWorlds.worlds) {
        throw "Runtime proof failed: local control worlds endpoint did not return the expected collection contract."
    }

    $created = Invoke-ControlJson -Method "POST" -Uri $worldsUri -Headers $headers -Body @{
        folderName = "runtime-proof-world"
        displayName = "Runtime Proof World"
        kind = "VOID"
    }
    if ([string]::IsNullOrWhiteSpace($created.id)) {
        throw "Runtime proof failed: Create World did not return a managed world id."
    }
    $sourceWorldId = $created.id

    $settings = Invoke-ControlJson -Method "PATCH" -Uri "$worldsUri/$sourceWorldId/settings" -Headers $headers -Body @{
        defaultGameMode = "CREATIVE"
        timeOfDayTicks = 6000
        weather = "CLEAR"
        naturalSpawning = $false
        daylightCycle = $false
        weatherCycle = $false
    }
    if ($settings.defaultGameMode -ne "CREATIVE" -or [long]$settings.timeOfDayTicks -ne 6000) {
        throw "Runtime proof failed: world settings did not persist the expected values."
    }

    $archiveStart = Invoke-ControlJson -Method "POST" -Uri "$baseUri/v1/tasks/archive" -Headers $headers -Body @{
        worldId = $sourceWorldId
    }
    [void](Wait-ControlTask -BaseUri $baseUri -Headers $headers -TaskId $archiveStart.taskId)

    $restoreStart = Invoke-ControlJson -Method "POST" -Uri "$baseUri/v1/tasks/restore" -Headers $headers -Body @{
        worldId = $sourceWorldId
    }
    [void](Wait-ControlTask -BaseUri $baseUri -Headers $headers -TaskId $restoreStart.taskId)

    $duplicateStart = Invoke-ControlJson -Method "POST" -Uri "$baseUri/v1/tasks/duplicate" -Headers $headers -Body @{
        worldId = $sourceWorldId
        destinationFolder = "runtime-proof-copy"
        displayName = "Runtime Proof Copy"
    }
    $duplicateTask = Wait-ControlTask -BaseUri $baseUri -Headers $headers -TaskId $duplicateStart.taskId
    $copyWorldId = $duplicateTask.result
    if ([string]::IsNullOrWhiteSpace($copyWorldId)) {
        throw "Runtime proof failed: duplicate task did not return the copied world id."
    }

    $backupStart = Invoke-ControlJson -Method "POST" -Uri "$baseUri/v1/tasks/backup" -Headers $headers -Body @{
        worldId = $sourceWorldId
    }
    $backupTask = Wait-ControlTask -BaseUri $baseUri -Headers $headers -TaskId $backupStart.taskId
    if ([string]::IsNullOrWhiteSpace($backupTask.result)) {
        throw "Runtime proof failed: backup task did not return a backup id."
    }

    $exportBaseName = "runtime-proof-export"
    $exportArtifact = "$exportBaseName.zip"
    $exportStart = Invoke-ControlJson -Method "POST" -Uri "$baseUri/v1/tasks/export" -Headers $headers -Body @{
        worldId = $sourceWorldId
        targetFormat = "JAVA_1_21_4"
        artifactName = $exportBaseName
    }
    $exportTask = Wait-ControlTask -BaseUri $baseUri -Headers $headers -TaskId $exportStart.taskId
    if ($exportTask.result -ne $exportArtifact) {
        throw "Runtime proof failed: native export returned an unexpected artifact result: $($exportTask.result)"
    }

    $exportPath = Join-Path $runtimeRoot "plugins/World-Manager/world/exports/$exportArtifact"
    $exportPath = Resolve-RequiredFile $exportPath "Native export artifact"
    $reimportArtifact = "runtime-proof-reimport.zip"
    $sha256 = (Get-FileHash -LiteralPath $exportPath -Algorithm SHA256).Hash.ToLowerInvariant()
    $uploadHeaders = @{
        Authorization = "Bearer $controlToken"
        "X-LazyBuilder-File-Name" = $reimportArtifact
        "X-LazyBuilder-Sha256" = $sha256
    }
    $upload = Invoke-RestMethod -Method Post -Uri "$baseUri/v1/imports/upload" `
        -Headers $uploadHeaders -InFile $exportPath -ContentType "application/octet-stream" -TimeoutSec 60
    if ($upload.fileName -ne $reimportArtifact) {
        throw "Runtime proof failed: import upload returned an unexpected file name."
    }

    $importStart = Invoke-ControlJson -Method "POST" -Uri "$baseUri/v1/tasks/import" -Headers $headers -Body @{
        artifactName = $reimportArtifact
        destinationFolder = "runtime-proof-imported"
        displayName = "Runtime Proof Imported"
    }
    $importTask = Wait-ControlTask -BaseUri $baseUri -Headers $headers -TaskId $importStart.taskId
    $importedWorldId = $importTask.result
    if ([string]::IsNullOrWhiteSpace($importedWorldId)) {
        throw "Runtime proof failed: import task did not return the imported world id."
    }

    $managed = Invoke-ControlJson -Method "GET" -Uri $worldsUri -Headers $headers
    $proofNames = @($managed.worlds | ForEach-Object { $_.displayName })
    foreach ($requiredWorld in @("Runtime Proof World", "Runtime Proof Copy", "Runtime Proof Imported")) {
        if ($proofNames -notcontains $requiredWorld) {
            throw "Runtime proof failed: managed-world list is missing '$requiredWorld'."
        }
    }

    $deleteCases = @(
        @{ id = $importedWorldId; name = "Runtime Proof Imported" },
        @{ id = $copyWorldId; name = "Runtime Proof Copy" },
        @{ id = $sourceWorldId; name = "Runtime Proof World" }
    )
    foreach ($deleteCase in $deleteCases) {
        $deleteStart = Invoke-ControlJson -Method "POST" -Uri "$baseUri/v1/tasks/delete" -Headers $headers -Body @{
            worldId = $deleteCase.id
            typedDisplayName = $deleteCase.name
        }
        [void](Wait-ControlTask -BaseUri $baseUri -Headers $headers -TaskId $deleteStart.taskId)
    }

    $finalWorlds = Invoke-ControlJson -Method "GET" -Uri $worldsUri -Headers $headers
    $remainingProofWorlds = @($finalWorlds.worlds | Where-Object {
        $_.displayName -in @("Runtime Proof World", "Runtime Proof Copy", "Runtime Proof Imported")
    })
    if ($remainingProofWorlds.Count -ne 0) {
        throw "Runtime proof failed: disposable managed worlds remained after delete verification."
    }

    Write-Host ""
    Write-Host "LazyBuilder Paper runtime proof passed."
    Write-Host "Verified: Paper boot, plugin enable, auth, status/world contracts, create, settings, archive/restore, duplicate, backup, native export, upload, import, delete."
    Write-Host "Runtime directory: $runtimeRoot"
}
finally {
    if ($null -ne $process) {
        Stop-SmokeProcess $process $ShutdownTimeoutSeconds
        $process.Dispose()
    }
    Pop-Location
}
