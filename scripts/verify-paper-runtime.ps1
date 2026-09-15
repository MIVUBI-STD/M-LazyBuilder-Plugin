param(
    [Parameter(Mandatory = $true)]
    [string]$ServerJar,

    [string]$WorldManagerJar = "plugins/world-manager/target/World-Manager-0.1.0-SNAPSHOT.jar",
    [string]$UtilitiesManagerJar = "plugins/utilities-manager/target/Utilities-Manager-0.1.0-SNAPSHOT.jar",
    [string]$RuntimeDirectory = ".runtime-proof/paper-smoke",
    [int]$StartupTimeoutSeconds = 120,
    [int]$ShutdownTimeoutSeconds = 30,
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

    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $JavaExecutable
    $startInfo.WorkingDirectory = $runtimeRoot
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.CreateNoWindow = $true
    $startInfo.ArgumentList.Add("-Xms512M")
    $startInfo.ArgumentList.Add("-Xmx1024M")
    $startInfo.ArgumentList.Add("-jar")
    $startInfo.ArgumentList.Add($serverJarPath)
    $startInfo.ArgumentList.Add("nogui")

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo

    $stdout = [System.Text.StringBuilder]::new()
    $stderr = [System.Text.StringBuilder]::new()
    $outputHandler = [System.Diagnostics.DataReceivedEventHandler]{
        param($sender, $eventArgs)
        if ($null -ne $eventArgs.Data) {
            [void]$stdout.AppendLine($eventArgs.Data)
            Write-Host $eventArgs.Data
        }
    }
    $errorHandler = [System.Diagnostics.DataReceivedEventHandler]{
        param($sender, $eventArgs)
        if ($null -ne $eventArgs.Data) {
            [void]$stderr.AppendLine($eventArgs.Data)
            Write-Host $eventArgs.Data
        }
    }
    $process.add_OutputDataReceived($outputHandler)
    $process.add_ErrorDataReceived($errorHandler)

    if (-not $process.Start()) {
        throw "Paper process could not be started."
    }
    $process.BeginOutputReadLine()
    $process.BeginErrorReadLine()

    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($StartupTimeoutSeconds)
    $ready = $false
    while ([DateTimeOffset]::UtcNow -lt $deadline) {
        if ($process.HasExited) { break }
        $combined = $stdout.ToString() + "`n" + $stderr.ToString()
        if ($combined -match "Done \(.+\)! For help") {
            $ready = $true
            break
        }
        Start-Sleep -Milliseconds 500
    }

    $combinedOutput = $stdout.ToString() + "`n" + $stderr.ToString()
    if (-not $ready) {
        throw "Paper did not reach ready state within $StartupTimeoutSeconds seconds."
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

    Write-Host ""
    Write-Host "LazyBuilder Paper runtime smoke proof passed."
    Write-Host "Runtime directory: $runtimeRoot"
}
finally {
    if ($null -ne $process) {
        Stop-SmokeProcess $process $ShutdownTimeoutSeconds
        $process.Dispose()
    }
    Pop-Location
}
