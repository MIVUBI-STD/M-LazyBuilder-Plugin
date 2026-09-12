namespace HaloKaryaMedia.LazyBuilder.Desktop.Modules.ServerManager;

public static class JavaRuntimeLocator
{
    public static string Resolve(string configuredPath)
    {
        if (!string.IsNullOrWhiteSpace(configuredPath))
        {
            var configured = Path.GetFullPath(configuredPath);
            if (File.Exists(configured)) return configured;
            throw new FileNotFoundException("Configured Java executable was not found.", configured);
        }

        var javaHome = Environment.GetEnvironmentVariable("JAVA_HOME");
        if (!string.IsNullOrWhiteSpace(javaHome))
        {
            var candidate = Path.Combine(javaHome, "bin", "java.exe");
            if (File.Exists(candidate)) return candidate;
        }

        var path = Environment.GetEnvironmentVariable("PATH") ?? string.Empty;
        foreach (var entry in path.Split(Path.PathSeparator, StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries))
        {
            var candidate = Path.Combine(entry.Trim('"'), "java.exe");
            if (File.Exists(candidate)) return candidate;
        }

        throw new FileNotFoundException("Java 21 executable could not be located. Set JAVA_HOME or configure JavaPath.");
    }
}
