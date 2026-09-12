using System.Diagnostics;
using System.Text.RegularExpressions;

namespace HaloKaryaMedia.LazyBuilder.Desktop.Modules.ServerManager;

public static partial class JavaRuntimeValidator
{
    [GeneratedRegex(@"(?:openjdk\s+)?(?<major>\d+)(?:\.\d+)*", RegexOptions.IgnoreCase)]
    private static partial Regex VersionRegex();

    public static async Task ValidateJava21Async(string javaPath, CancellationToken cancellationToken = default)
    {
        using var process = new Process
        {
            StartInfo = new ProcessStartInfo
            {
                FileName = javaPath,
                Arguments = "--version",
                UseShellExecute = false,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                CreateNoWindow = true
            }
        };

        if (!process.Start()) throw new InvalidOperationException("Java runtime could not be started for validation.");

        Task<string> stdout = process.StandardOutput.ReadToEndAsync(cancellationToken);
        Task<string> stderr = process.StandardError.ReadToEndAsync(cancellationToken);
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        timeout.CancelAfter(TimeSpan.FromSeconds(5));
        await process.WaitForExitAsync(timeout.Token);

        string text = (await stdout) + Environment.NewLine + (await stderr);
        var match = VersionRegex().Match(text);
        if (!match.Success || !int.TryParse(match.Groups["major"].Value, out int major))
            throw new InvalidOperationException("Could not determine the configured Java runtime version.");

        if (major != 21)
            throw new InvalidOperationException($"LazyBuilder Paper 1.21.4 requires Java 21. Detected Java {major}.");
    }
}
