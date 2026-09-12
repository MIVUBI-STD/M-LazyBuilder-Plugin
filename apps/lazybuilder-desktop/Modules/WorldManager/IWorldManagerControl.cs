namespace HaloKaryaMedia.LazyBuilder.Desktop.Modules.WorldManager;

public interface IWorldManagerControl
{
    Task<WorldControlStatus> GetStatusAsync(CancellationToken cancellationToken = default);
    Task<IReadOnlyList<ManagedWorldSummary>> ListWorldsAsync(CancellationToken cancellationToken = default);
}

public sealed record WorldControlStatus(string Status, int ProtocolVersion);

public sealed record ManagedWorldSummary(
    string Id,
    string FolderName,
    string DisplayName,
    string Kind,
    string Lifecycle,
    string RuntimeState,
    bool AutoLoad,
    string DefaultGameMode
);
