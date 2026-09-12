using System.Security.Cryptography;
using System.Text.Json;

namespace HaloKaryaMedia.LazyBuilder.Desktop.Modules.WorldManager;

public sealed record WorldControlOptions
{
    public int Port { get; init; } = 17842;
    public string Token { get; init; } = "";

    public WorldControlOptions EnsureToken()
    {
        if (Port is < 1024 or > 65535)
            throw new InvalidOperationException("World control port must be between 1024 and 65535.");
        if (!string.IsNullOrWhiteSpace(Token)) return this;
        return this with { Token = Convert.ToHexString(RandomNumberGenerator.GetBytes(32)) };
    }
}

public sealed class WorldControlConfigStore
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        WriteIndented = true,
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    private readonly string _path;

    public WorldControlConfigStore(string workspaceRoot)
    {
        _path = Path.Combine(Path.GetFullPath(workspaceRoot), "tools", "lazybuilder", "world-control.json");
    }

    public async Task<WorldControlOptions> LoadOrCreateAsync(CancellationToken cancellationToken = default)
    {
        WorldControlOptions options;
        if (File.Exists(_path))
        {
            await using var input = File.OpenRead(_path);
            options = await JsonSerializer.DeserializeAsync<WorldControlOptions>(input, JsonOptions, cancellationToken)
                ?? new WorldControlOptions();
        }
        else
        {
            options = new WorldControlOptions();
        }

        var normalized = options.EnsureToken();
        if (!File.Exists(_path) || !string.Equals(options.Token, normalized.Token, StringComparison.Ordinal))
            await SaveAsync(normalized, cancellationToken);
        return normalized;
    }

    private async Task SaveAsync(WorldControlOptions options, CancellationToken cancellationToken)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(_path)!);
        string temporary = _path + ".tmp";
        await using (var output = File.Create(temporary))
            await JsonSerializer.SerializeAsync(output, options, JsonOptions, cancellationToken);
        File.Move(temporary, _path, overwrite: true);
    }
}
