namespace HaloKaryaMedia.LazyBuilder.Desktop.Modules.ServerManager;

public interface IServerManager
{
    ServerState State { get; }
    ServerHealthSnapshot Health { get; }

    Task StartAsync(CancellationToken cancellationToken = default);
    Task StopAsync(CancellationToken cancellationToken = default);
    Task RestartAsync(CancellationToken cancellationToken = default);
}

public enum ServerState
{
    Offline,
    Starting,
    Online,
    Stopping,
    Crashed
}

public sealed record ServerHealthSnapshot(
    string Status,
    double CpuLoadPercent,
    long UsedMemoryBytes,
    long MaxMemoryBytes
);
