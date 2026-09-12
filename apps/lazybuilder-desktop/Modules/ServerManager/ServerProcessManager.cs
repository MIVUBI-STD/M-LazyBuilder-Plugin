using System.Diagnostics;

namespace HaloKaryaMedia.LazyBuilder.Desktop.Modules.ServerManager;

public sealed class ServerProcessManager : IServerManager, IAsyncDisposable
{
    private readonly string _workspaceRoot;
    private readonly ServerManagerOptions _options;
    private readonly SemaphoreSlim _lifecycleGate = new(1, 1);

    private Process? _process;
    private CancellationTokenSource? _monitorCancellation;
    private Task? _monitorTask;
    private bool _expectedStop;
    private DateTime _lastCpuSampleAt;
    private TimeSpan _lastCpuTime;

    public ServerState State { get; private set; } = ServerState.Offline;
    public ServerHealthSnapshot Health { get; private set; } = new("Offline", 0, 0, 0);

    public event EventHandler? SnapshotChanged;

    public ServerProcessManager(string workspaceRoot, ServerManagerOptions options)
    {
        _workspaceRoot = Path.GetFullPath(workspaceRoot);
        _options = options.Validate();
    }

    public async Task StartAsync(CancellationToken cancellationToken = default)
    {
        await _lifecycleGate.WaitAsync(cancellationToken);
        try
        {
            if (_process is { HasExited: false }) return;

            string serverDirectory = Path.GetFullPath(Path.Combine(_workspaceRoot, _options.ServerDirectory));
            string paperJar = Path.Combine(serverDirectory, _options.PaperJar);
            if (!File.Exists(paperJar)) throw new FileNotFoundException("Paper server JAR was not found.", paperJar);

            string javaPath = await JavaRuntimeLocator.ResolveAsync(_options.JavaPath, cancellationToken);
            _expectedStop = false;
            SetState(ServerState.Starting);

            var process = new Process
            {
                StartInfo = new ProcessStartInfo
                {
                    FileName = javaPath,
                    WorkingDirectory = serverDirectory,
                    UseShellExecute = false,
                    RedirectStandardInput = true,
                    RedirectStandardOutput = true,
                    RedirectStandardError = true,
                    CreateNoWindow = true,
                    Arguments = $"-Xms{_options.MinMemoryMb}M -Xmx{_options.MaxMemoryMb}M -jar \"{paperJar}\" nogui"
                },
                EnableRaisingEvents = true
            };

            process.OutputDataReceived += OnOutputDataReceived;
            process.ErrorDataReceived += OnErrorDataReceived;
            process.Exited += OnProcessExited;

            if (!process.Start())
            {
                process.Dispose();
                SetState(ServerState.Crashed);
                throw new InvalidOperationException("Paper process could not be started.");
            }

            _process = process;
            process.BeginOutputReadLine();
            process.BeginErrorReadLine();
            ResetCpuSample(process);
            StartMonitor(process);
        }
        catch
        {
            if (State == ServerState.Starting) SetState(ServerState.Crashed);
            throw;
        }
        finally
        {
            _lifecycleGate.Release();
        }
    }

    public async Task StopAsync(CancellationToken cancellationToken = default)
    {
        await _lifecycleGate.WaitAsync(cancellationToken);
        try
        {
            var process = _process;
            if (process is null || process.HasExited)
            {
                SetState(ServerState.Offline);
                return;
            }

            _expectedStop = true;
            SetState(ServerState.Stopping);

            try
            {
                await process.StandardInput.WriteLineAsync("stop");
                await process.StandardInput.FlushAsync(cancellationToken);
            }
            catch (InvalidOperationException)
            {
                // Process may have exited between the state check and stdin write.
            }

            using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
            timeout.CancelAfter(TimeSpan.FromSeconds(_options.GracefulStopTimeoutSeconds));

            try
            {
                await process.WaitForExitAsync(timeout.Token);
            }
            catch (OperationCanceledException) when (!cancellationToken.IsCancellationRequested)
            {
                if (!process.HasExited)
                {
                    process.Kill(entireProcessTree: true);
                    await process.WaitForExitAsync(cancellationToken);
                }
            }

            SetState(ServerState.Offline);
        }
        finally
        {
            _lifecycleGate.Release();
        }
    }

    public async Task RestartAsync(CancellationToken cancellationToken = default)
    {
        await StopAsync(cancellationToken);
        await StartAsync(cancellationToken);
    }

    private void OnOutputDataReceived(object sender, DataReceivedEventArgs args)
    {
        var line = args.Data;
        if (string.IsNullOrWhiteSpace(line)) return;

        if (State == ServerState.Starting && line.Contains("Done (", StringComparison.OrdinalIgnoreCase))
        {
            SetState(ServerState.Online);
        }
    }

    private void OnErrorDataReceived(object sender, DataReceivedEventArgs args)
    {
        // stderr is consumed to avoid pipe backpressure. UI logging remains an Advanced concern.
    }

    private void OnProcessExited(object? sender, EventArgs args)
    {
        var process = sender as Process;
        if (process is null) return;

        StopMonitor();
        SetState(_expectedStop ? ServerState.Offline : ServerState.Crashed);
        SetHealth(State == ServerState.Crashed ? "Critical" : "Offline", 0, 0, _options.MaxMemoryMb * 1024L * 1024L);
    }

    private void StartMonitor(Process process)
    {
        StopMonitor();
        _monitorCancellation = new CancellationTokenSource();
        _monitorTask = MonitorAsync(process, _monitorCancellation.Token);
    }

    private void StopMonitor()
    {
        _monitorCancellation?.Cancel();
        _monitorCancellation?.Dispose();
        _monitorCancellation = null;
        _monitorTask = null;
    }

    private async Task MonitorAsync(Process process, CancellationToken cancellationToken)
    {
        using var timer = new PeriodicTimer(TimeSpan.FromSeconds(_options.HealthSampleSeconds));
        try
        {
            while (await timer.WaitForNextTickAsync(cancellationToken))
            {
                if (process.HasExited) return;
                SampleHealth(process);

                if (State == ServerState.Starting && process.StartTime.ToUniversalTime().AddSeconds(_options.StartupTimeoutSeconds) < DateTime.UtcNow)
                {
                    SetHealth("Warning", Health.CpuLoadPercent, Health.UsedMemoryBytes, Health.MaxMemoryBytes);
                }
            }
        }
        catch (OperationCanceledException)
        {
            // Normal monitor shutdown.
        }
        catch (InvalidOperationException)
        {
            // Process exited during a sample.
        }
    }

    private void ResetCpuSample(Process process)
    {
        _lastCpuSampleAt = DateTime.UtcNow;
        _lastCpuTime = process.TotalProcessorTime;
        SampleHealth(process);
    }

    private void SampleHealth(Process process)
    {
        process.Refresh();
        var now = DateTime.UtcNow;
        var cpuTime = process.TotalProcessorTime;
        double wallMilliseconds = Math.Max(1, (now - _lastCpuSampleAt).TotalMilliseconds);
        double cpuMilliseconds = Math.Max(0, (cpuTime - _lastCpuTime).TotalMilliseconds);
        double cpuPercent = Math.Clamp(cpuMilliseconds / (wallMilliseconds * Environment.ProcessorCount) * 100.0, 0, 100);

        _lastCpuSampleAt = now;
        _lastCpuTime = cpuTime;

        long usedMemory = process.WorkingSet64;
        long maxMemory = _options.MaxMemoryMb * 1024L * 1024L;
        double memoryPercent = maxMemory == 0 ? 0 : usedMemory * 100.0 / maxMemory;

        string status = State switch
        {
            ServerState.Crashed => "Critical",
            ServerState.Offline => "Offline",
            _ when cpuPercent >= 90 || memoryPercent >= 90 => "Critical",
            _ when cpuPercent >= 75 || memoryPercent >= 75 || State == ServerState.Starting => "Warning",
            _ => "Good"
        };

        SetHealth(status, cpuPercent, usedMemory, maxMemory);
    }

    private void SetState(ServerState state)
    {
        State = state;
        SnapshotChanged?.Invoke(this, EventArgs.Empty);
    }

    private void SetHealth(string status, double cpuPercent, long usedMemory, long maxMemory)
    {
        Health = new ServerHealthSnapshot(status, cpuPercent, usedMemory, maxMemory);
        SnapshotChanged?.Invoke(this, EventArgs.Empty);
    }

    public async ValueTask DisposeAsync()
    {
        try
        {
            if (_process is { HasExited: false }) await StopAsync();
        }
        catch
        {
            if (_process is { HasExited: false }) _process.Kill(entireProcessTree: true);
        }
        finally
        {
            StopMonitor();
            _process?.Dispose();
            _lifecycleGate.Dispose();
        }
    }
}
