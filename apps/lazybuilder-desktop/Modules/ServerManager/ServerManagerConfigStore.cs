using System.Text.Json;

namespace HaloKaryaMedia.LazyBuilder.Desktop.Modules.ServerManager;

public sealed class ServerManagerConfigStore
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        WriteIndented = true,
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    private readonly string _configPath;

    public ServerManagerConfigStore(string workspaceRoot)
    {
        var toolsRoot = Path.Combine(workspaceRoot, "tools", "lazybuilder");
        _configPath = Path.Combine(toolsRoot, "server-manager.json");
    }

    public async Task<ServerManagerOptions> LoadAsync(CancellationToken cancellationToken = default)
    {
        if (!File.Exists(_configPath))
        {
            var defaults = new ServerManagerOptions();
            await SaveAsync(defaults, cancellationToken);
            return defaults;
        }

        await using var stream = File.OpenRead(_configPath);
        var options = await JsonSerializer.DeserializeAsync<ServerManagerOptions>(stream, JsonOptions, cancellationToken)
            ?? new ServerManagerOptions();
        return options.Validate();
    }

    public async Task SaveAsync(ServerManagerOptions options, CancellationToken cancellationToken = default)
    {
        options.Validate();
        var directory = Path.GetDirectoryName(_configPath)!;
        Directory.CreateDirectory(directory);

        var tempPath = _configPath + ".tmp";
        await using (var stream = File.Create(tempPath))
        {
            await JsonSerializer.SerializeAsync(stream, options, JsonOptions, cancellationToken);
        }

        File.Move(tempPath, _configPath, overwrite: true);
    }
}
