namespace HaloKaryaMedia.LazyBuilder.Desktop.Modules.PluginManager;

public interface IPluginManager
{
    Task<IReadOnlyList<PluginSummary>> ListAsync(CancellationToken cancellationToken = default);
    Task<PluginInstallResult> InstallAsync(string jarPath, CancellationToken cancellationToken = default);
    Task<PluginInstallResult> UpdateAsync(string pluginId, string jarPath, CancellationToken cancellationToken = default);
    Task SetEnabledAsync(string pluginId, bool enabled, CancellationToken cancellationToken = default);
    Task RemoveAsync(string pluginId, bool removeData, CancellationToken cancellationToken = default);
}

public sealed record PluginSummary(
    string Id,
    string DisplayName,
    string Version,
    string Category,
    PluginState State
);

public enum PluginState
{
    Enabled,
    Disabled,
    NeedsRestart,
    Problem
}

public sealed record PluginInstallResult(
    bool Success,
    string PluginId,
    string? Message,
    bool RestartRequired
);
