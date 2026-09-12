using System.IO.Compression;

namespace HaloKaryaMedia.LazyBuilder.Desktop.Modules.PluginManager;

internal sealed record PluginMetadata(
    string Name,
    string Version,
    string Main,
    string ApiVersion,
    IReadOnlyList<string> Dependencies
)
{
    public string CanonicalId => PluginId.Normalize(Name);
}

internal static class PluginId
{
    public static string Normalize(string value)
    {
        var chars = value.Trim().ToLowerInvariant()
            .Where(ch => char.IsLetterOrDigit(ch) || ch == '-' || ch == '_')
            .ToArray();
        if (chars.Length == 0) throw new InvalidDataException("Plugin name does not contain a valid identifier.");
        return new string(chars).Replace('_', '-');
    }
}

internal static class PluginMetadataReader
{
    public static PluginMetadata Read(string jarPath)
    {
        if (!File.Exists(jarPath)) throw new FileNotFoundException("Plugin JAR was not found.", jarPath);
        if (!string.Equals(Path.GetExtension(jarPath), ".jar", StringComparison.OrdinalIgnoreCase))
            throw new InvalidDataException("Selected file is not a JAR.");

        using var archive = ZipFile.OpenRead(jarPath);
        var entry = archive.GetEntry("plugin.yml") ?? archive.GetEntry("paper-plugin.yml")
            ?? throw new InvalidDataException("JAR does not contain plugin.yml or paper-plugin.yml.");

        using var reader = new StreamReader(entry.Open());
        string text = reader.ReadToEnd();
        var fields = ParseTopLevel(text);

        string name = Required(fields, "name");
        string version = Required(fields, "version");
        string main = Required(fields, "main");
        fields.TryGetValue("api-version", out string? apiVersion);

        var dependencies = new List<string>();
        dependencies.AddRange(ParseList(fields.GetValueOrDefault("depend")));
        dependencies.AddRange(ParseList(fields.GetValueOrDefault("dependencies")));

        return new PluginMetadata(name, version, main, apiVersion ?? string.Empty,
            dependencies.Select(PluginId.Normalize).Distinct(StringComparer.OrdinalIgnoreCase).ToArray());
    }

    private static Dictionary<string, string> ParseTopLevel(string text)
    {
        var result = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        foreach (string raw in text.Replace("\r", string.Empty).Split('\n'))
        {
            if (string.IsNullOrWhiteSpace(raw) || char.IsWhiteSpace(raw[0]) || raw.TrimStart().StartsWith('#')) continue;
            int colon = raw.IndexOf(':');
            if (colon <= 0) continue;
            string key = raw[..colon].Trim();
            string value = raw[(colon + 1)..].Trim().Trim('"', '\'');
            result[key] = value;
        }
        return result;
    }

    private static string Required(IReadOnlyDictionary<string, string> fields, string key)
    {
        if (!fields.TryGetValue(key, out string? value) || string.IsNullOrWhiteSpace(value))
            throw new InvalidDataException($"Plugin metadata is missing required field '{key}'.");
        return value;
    }

    private static IEnumerable<string> ParseList(string? value)
    {
        if (string.IsNullOrWhiteSpace(value)) yield break;
        string content = value.Trim();
        if (content.StartsWith('[') && content.EndsWith(']')) content = content[1..^1];
        foreach (string item in content.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries))
        {
            string normalized = item.Trim().Trim('"', '\'');
            if (!string.IsNullOrWhiteSpace(normalized)) yield return normalized;
        }
    }
}
