namespace HaloKaryaMedia.LazyBuilder.Desktop.Modules.PluginManager;

internal static class PluginCategoryRegistry
{
    private static readonly Dictionary<string, string> Categories = new(StringComparer.OrdinalIgnoreCase)
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

    public static string Resolve(string pluginId, string displayName)
    {
        if (Categories.TryGetValue(pluginId, out string? category)) return category;

        string displayId = PluginId.Normalize(displayName);
        if (Categories.TryGetValue(displayId, out category)) return category;
        return "Other";
    }
}
