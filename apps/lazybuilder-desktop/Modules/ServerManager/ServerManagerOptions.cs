namespace HaloKaryaMedia.LazyBuilder.Desktop.Modules.ServerManager;

public sealed record ServerManagerOptions
{
    public string JavaPath { get; init; } = "";
    public string ServerDirectory { get; init; } = "server";
    public string PaperJar { get; init; } = "paper.jar";
    public int MinMemoryMb { get; init; } = 1024;
    public int MaxMemoryMb { get; init; } = 4096;
    public int GracefulStopTimeoutSeconds { get; init; } = 30;
    public int StartupTimeoutSeconds { get; init; } = 90;
    public int HealthSampleSeconds { get; init; } = 2;

    public ServerManagerOptions Validate()
    {
        if (MinMemoryMb < 256) throw new InvalidOperationException("MinMemoryMb must be at least 256 MB.");
        if (MaxMemoryMb < MinMemoryMb) throw new InvalidOperationException("MaxMemoryMb must be greater than or equal to MinMemoryMb.");
        if (GracefulStopTimeoutSeconds < 5) throw new InvalidOperationException("GracefulStopTimeoutSeconds must be at least 5 seconds.");
        if (StartupTimeoutSeconds < 10) throw new InvalidOperationException("StartupTimeoutSeconds must be at least 10 seconds.");
        if (HealthSampleSeconds < 1) throw new InvalidOperationException("HealthSampleSeconds must be at least 1 second.");
        if (string.IsNullOrWhiteSpace(ServerDirectory)) throw new InvalidOperationException("ServerDirectory is required.");
        if (string.IsNullOrWhiteSpace(PaperJar)) throw new InvalidOperationException("PaperJar is required.");
        return this;
    }
}
