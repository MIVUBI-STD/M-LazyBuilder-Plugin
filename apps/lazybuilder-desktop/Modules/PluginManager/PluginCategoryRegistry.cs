using System.Text.Json;

namespace HaloKaryaMedia.LazyBuilder.Desktop.Modules.PluginManager;

internal sealed class PluginCategoryRegistry
{
    public static readonly string[] AllowedCategories =
    [
        "World Management",
        "Build Tools",
        "Server Utilities",
        "Performance",
        "Dependencies",
        "Other"
    ];

    private static readonly Dictionary<string, string> Known = new(StringComparer.OrdinalIgnoreCase)
    {
        ["world-manager"] = "World Management",
        ["utilities-manager"] = "Server Utilities",
        ["axiom"] = "Build Tools",
        ["axiompaper"] = "Build Tools",
        ["fastasyncworldedit"] = "Build Tools",
        ["fawe"] = "Build Tools",
        ["fastasyncvoxelsniper"] = "Build Tools",
        ["ezedits"] = "Build Tools",
        ["metabrushes"] = "Build Tools"
    };

    private readonly string _registryPath;
    private readonly Dictionary<string, string> _overrides;

    public PluginCategoryRegistry(string workspaceRoot)
    {
        _registryPath = Path.Combine(Path.GetFullPath(workspaceRoot), "tools", "lazybuilder", "plugin-registry.json");
        _overrides = LoadOverrides(_registryPath);
    }

    public string Resolve(string pluginId, string displayName)
    {
        if (_overrides.TryGetValue(pluginId, out string? category)) return category;
        if (Known.TryGetValue(pluginId, out category)) return category;

        string displayId = PluginId.Normalize(displayName);
        if (_overrides.TryGetValue(displayId, out category)) return category;
        if (Known.TryGetValue(displayId, out category)) return category;
        return "Other";
    }

    public async Task SetOverrideAsync(string pluginId, string category, CancellationToken cancellationToken = default)
    {
        string canonicalId = PluginId.Normalize(pluginId);
        if (!AllowedCategories.Contains(category, StringComparer.OrdinalIgnoreCase))
            throw new ArgumentOutOfRangeException(nameof(category), "Unknown plugin category.");

        string canonicalCategory = AllowedCategories.First(item =>
            string.Equals(item, category, StringComparison.OrdinalIgnoreCase));
        _overrides[canonicalId] = canonicalCategory;

        string directory = Path.GetDirectoryName(_registryPath)!;
        Directory.CreateDirectory(directory);
        string temp = _registryPath + ".tmp";
        await File.WriteAllTextAsync(temp,
            JsonSerializer.Serialize(_overrides, new JsonSerializerOptions { WriteIndented = true }), cancellationToken);
        File.Move(temp, _registryPath, overwrite: true);
    }

    private static Dictionary<string, string> LoadOverrides(string path)
    {
        if (!File.Exists(path)) return new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        try
        {
            string json = File.ReadAllText(path);
            var values = JsonSerializer.Deserialize<Dictionary<string, string>>(json)
                ?? new Dictionary<string, string>();
            return new Dictionary<string, string>(values, StringComparer.OrdinalIgnoreCase);
        }
        catch (JsonException)
        {
            return new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        }
    }
}
