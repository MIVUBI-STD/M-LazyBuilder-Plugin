namespace HaloKaryaMedia.LazyBuilder.Desktop;

internal static class WorkspaceRootLocator
{
    private const string OverrideVariable = "LAZYBUILDER_WORKSPACE_ROOT";

    public static string Resolve()
    {
        string? overrideRoot = Environment.GetEnvironmentVariable(OverrideVariable);
        if (!string.IsNullOrWhiteSpace(overrideRoot))
            return Path.GetFullPath(overrideRoot);

        return Path.GetFullPath(AppContext.BaseDirectory);
    }
}
