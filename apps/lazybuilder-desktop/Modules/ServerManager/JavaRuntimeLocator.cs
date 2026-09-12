using System.Diagnostics;
using System.Text.RegularExpressions;

namespace HaloKaryaMedia.LazyBuilder.Desktop.Modules.ServerManager;

public static class JavaRuntimeLocator
{
    public static async Task<string> ResolveAsync(string configuredPath, CancellationToken cancellationToken = default)
    {
        string java = Locate(configuredPath);
        await ValidateJava21Async(java, cancellationToken);
        return java;
    }

    private static string Locate(string configuredPath)
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

        throw new FileNotFoundException("Java executable could not be located. Set JAVA_HOME or configure JavaPath.");
    }

    private static async Task ValidateJava21Async(string javaPath, CancellationToken cancellationToken)
    {
        using var process = new Process
        {
            StartInfo = new ProcessStartInfo
            {
                FileName = javaPath,
                Arguments = "-version",
                UseShellExecute = false,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                CreateNoWindow = true
            }
        };

        if (!process.Start())
            throw new InvalidOperationException("Java runtime could not be started for version validation.");

        Task<string> stdout = process.StandardOutput.ReadToEndAsync(cancellationToken);
        Task<string> stderr = process.StandardError.ReadToEndAsync(cancellationToken);
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        timeout.CancelAfter(TimeSpan.FromSeconds(10));

        try
        {
            await process.WaitForExitAsync(timeout.Token);
        }
        catch (OperationCanceledException) when (!cancellationToken.IsCancellationRequested)
        {
            if (!process.HasExited) process.Kill(entireProcessTree: true);
            throw new InvalidOperationException("Java version validation timed out.");
        }

        string output = (await stdout) + "\n" + (await stderr);
        var match = Regex.Match(output, "version\\s+\\\"(?<major>\\d+)", RegexOptions.IgnoreCase);
        if (!match.Success || !int.TryParse(match.Groups["major"].Value, out int major))
            throw new InvalidOperationException("Unable to determine the installed Java version.");
        if (major != 21)
            throw new InvalidOperationException($"LazyBuilder Paper 1.21.4 requires Java 21. Detected Java {major}.");
    }
}
