namespace HaloKaryaMedia.LazyBuilder.Desktop.Modules.PluginManager;

public sealed class FileSystemPluginManager : IPluginManager
{
    private readonly string _pluginsDirectory;
    private readonly string _disabledDirectory;
    private readonly string _backupDirectory;
    private readonly SemaphoreSlim _mutationGate = new(1, 1);

    public FileSystemPluginManager(string workspaceRoot)
    {
        string root = Path.GetFullPath(workspaceRoot);
        _pluginsDirectory = Path.Combine(root, "server", "plugins");
        _disabledDirectory = Path.Combine(root, "server", "plugins-disabled");
        _backupDirectory = Path.Combine(root, "tools", "lazybuilder", "plugin-backups");
    }

    public Task<IReadOnlyList<PluginSummary>> ListAsync(CancellationToken cancellationToken = default)
    {
        cancellationToken.ThrowIfCancellationRequested();

        var scans = ScanDirectoryDetailed(_pluginsDirectory, PluginState.Enabled)
            .Concat(ScanDirectoryDetailed(_disabledDirectory, PluginState.Disabled))
            .ToList();

        var valid = scans.Where(item => item.Metadata is not null).Select(item => item.ToInstalled()).ToList();
        var enabledIds = valid.Where(item => item.State == PluginState.Enabled)
            .Select(item => item.Metadata.CanonicalId)
            .ToHashSet(StringComparer.OrdinalIgnoreCase);
        var allIds = valid.Select(item => item.Metadata.CanonicalId)
            .ToHashSet(StringComparer.OrdinalIgnoreCase);

        var result = new List<PluginSummary>();

        foreach (var group in valid.GroupBy(item => item.Metadata.CanonicalId, StringComparer.OrdinalIgnoreCase)
                     .OrderBy(group => group.Key, StringComparer.OrdinalIgnoreCase))
        {
            var candidates = group.ToList();
            var primary = candidates.OrderByDescending(item => item.State == PluginState.Enabled).First();
            PluginState state = primary.State;
            string? problem = null;

            if (candidates.Count > 1)
            {
                state = PluginState.Problem;
                problem = "Duplicate plugin JARs detected: " + string.Join(", ",
                    candidates.Select(item => $"{item.Metadata.Version} ({Path.GetFileName(item.Path)})"));
            }
            else if (primary.State == PluginState.Enabled)
            {
                var missing = primary.Metadata.Dependencies.Where(dep => !allIds.Contains(dep)).ToArray();
                var disabled = primary.Metadata.Dependencies.Where(dep => allIds.Contains(dep) && !enabledIds.Contains(dep)).ToArray();
                if (missing.Length > 0)
                {
                    state = PluginState.Problem;
                    problem = "Missing required dependencies: " + string.Join(", ", missing);
                }
                else if (disabled.Length > 0)
                {
                    state = PluginState.Problem;
                    problem = "Required dependencies are disabled: " + string.Join(", ", disabled);
                }
            }

            result.Add(new PluginSummary(
                primary.Metadata.CanonicalId,
                primary.Metadata.Name,
                primary.Metadata.Version,
                PluginCategoryRegistry.Resolve(primary.Metadata.CanonicalId, primary.Metadata.Name),
                state,
                problem
            ));
        }

        foreach (var broken in scans.Where(item => item.Metadata is null))
        {
            result.Add(new PluginSummary(
                ProblemIdFromPath(broken.Path),
                Path.GetFileNameWithoutExtension(broken.Path),
                "Unknown",
                "Other",
                PluginState.Problem,
                broken.Error ?? "Plugin metadata could not be read."
            ));
        }

        return Task.FromResult<IReadOnlyList<PluginSummary>>(result
            .OrderBy(item => item.Category, StringComparer.OrdinalIgnoreCase)
            .ThenBy(item => item.DisplayName, StringComparer.OrdinalIgnoreCase)
            .ToArray());
    }

    public async Task<PluginInstallResult> InstallAsync(string jarPath, CancellationToken cancellationToken = default)
    {
        await _mutationGate.WaitAsync(cancellationToken);
        try
        {
            PluginMetadata incoming;
            try
            {
                incoming = PluginMetadataReader.Read(jarPath);
                ValidateTargetCompatibility(incoming);
            }
            catch (Exception exception) when (exception is InvalidDataException or IOException)
            {
                return new PluginInstallResult(false, ProblemIdFromPath(jarPath), exception.Message, false);
            }

            var existing = FindAllById(incoming.CanonicalId).ToList();
            if (existing.Count > 0)
            {
                var current = existing[0].Metadata.Version;
                return new PluginInstallResult(false, incoming.CanonicalId,
                    $"Plugin is already installed (current {current}, selected {incoming.Version}). Use Update instead.", false);
            }

            string? dependencyProblem = GetDependencyProblem(incoming);
            if (dependencyProblem is not null)
                return new PluginInstallResult(false, incoming.CanonicalId, dependencyProblem, false);

            Directory.CreateDirectory(_pluginsDirectory);
            string destination = Path.Combine(_pluginsDirectory, SafeJarName(incoming.Name, incoming.Version));
            await CopyAtomicAsync(jarPath, destination, cancellationToken);
            return new PluginInstallResult(true, incoming.CanonicalId, "Plugin installed. Restart required.", true);
        }
        finally
        {
            _mutationGate.Release();
        }
    }

    public async Task<PluginInstallResult> UpdateAsync(string pluginId, string jarPath, CancellationToken cancellationToken = default)
    {
        await _mutationGate.WaitAsync(cancellationToken);
        try
        {
            string canonicalId = PluginId.Normalize(pluginId);
            PluginMetadata incoming;
            try
            {
                incoming = PluginMetadataReader.Read(jarPath);
                ValidateTargetCompatibility(incoming);
            }
            catch (Exception exception) when (exception is InvalidDataException or IOException)
            {
                return new PluginInstallResult(false, canonicalId, exception.Message, false);
            }

            if (!string.Equals(canonicalId, incoming.CanonicalId, StringComparison.OrdinalIgnoreCase))
                return new PluginInstallResult(false, canonicalId, "Selected JAR belongs to a different plugin.", false);

            var existing = FindAllById(canonicalId).ToList();
            if (existing.Count == 0)
                return new PluginInstallResult(false, canonicalId, "Plugin is not installed.", false);
            if (existing.Count > 1)
                return new PluginInstallResult(false, canonicalId,
                    "Duplicate plugin versions must be resolved before updating. Keep only one JAR, then retry.", false);

            string? dependencyProblem = GetDependencyProblem(incoming, canonicalId);
            if (dependencyProblem is not null)
                return new PluginInstallResult(false, canonicalId, dependencyProblem, false);

            var current = existing[0];
            Directory.CreateDirectory(_backupDirectory);
            string backupName = $"{Path.GetFileNameWithoutExtension(current.Path)}-{DateTime.UtcNow:yyyyMMddHHmmss}.jar";
            File.Copy(current.Path, Path.Combine(_backupDirectory, backupName), overwrite: false);

            string targetDirectory = current.State == PluginState.Disabled ? _disabledDirectory : _pluginsDirectory;
            Directory.CreateDirectory(targetDirectory);
            string destination = Path.Combine(targetDirectory, SafeJarName(incoming.Name, incoming.Version));
            await CopyAtomicAsync(jarPath, destination, cancellationToken);

            if (!Path.GetFullPath(current.Path).Equals(Path.GetFullPath(destination), StringComparison.OrdinalIgnoreCase))
                File.Delete(current.Path);

            return new PluginInstallResult(true, canonicalId,
                $"Updated {current.Metadata.Version} → {incoming.Version}. Restart required.", true);
        }
        finally
        {
            _mutationGate.Release();
        }
    }

    public async Task SetEnabledAsync(string pluginId, bool enabled, CancellationToken cancellationToken = default)
    {
        await _mutationGate.WaitAsync(cancellationToken);
        try
        {
            string canonicalId = PluginId.Normalize(pluginId);
            var existing = FindAllById(canonicalId).ToList();
            if (existing.Count != 1)
                throw new InvalidOperationException(existing.Count == 0 ? "Plugin is not installed." : "Duplicate plugin versions must be resolved first.");

            var current = existing[0];
            if ((enabled && current.State == PluginState.Enabled) || (!enabled && current.State == PluginState.Disabled)) return;

            if (enabled)
            {
                string? dependencyProblem = GetDependencyProblem(current.Metadata, canonicalId);
                if (dependencyProblem is not null) throw new InvalidOperationException(dependencyProblem);
            }

            string targetDirectory = enabled ? _pluginsDirectory : _disabledDirectory;
            Directory.CreateDirectory(targetDirectory);
            string destination = Path.Combine(targetDirectory, Path.GetFileName(current.Path));
            if (File.Exists(destination)) throw new IOException("Target plugin file already exists.");
            File.Move(current.Path, destination);
        }
        finally
        {
            _mutationGate.Release();
        }
    }

    public async Task RemoveAsync(string pluginId, bool removeData, CancellationToken cancellationToken = default)
    {
        await _mutationGate.WaitAsync(cancellationToken);
        try
        {
            string canonicalId = PluginId.Normalize(pluginId);
            var existing = FindAllById(canonicalId).ToList();
            if (existing.Count == 0) return;
            if (existing.Count > 1) throw new InvalidOperationException("Duplicate plugin versions must be resolved before removal.");

            var current = existing[0];
            File.Delete(current.Path);

            if (removeData)
            {
                string dataPath = Path.GetFullPath(Path.Combine(_pluginsDirectory, current.Metadata.Name));
                string pluginsRoot = Path.GetFullPath(_pluginsDirectory) + Path.DirectorySeparatorChar;
                if (!dataPath.StartsWith(pluginsRoot, StringComparison.OrdinalIgnoreCase))
                    throw new InvalidOperationException("Refusing to remove plugin data outside the plugins directory.");
                if (Directory.Exists(dataPath)) Directory.Delete(dataPath, recursive: true);
            }
        }
        finally
        {
            _mutationGate.Release();
        }
    }

    private IEnumerable<InstalledPlugin> ScanDirectory(string directory, PluginState state) =>
        ScanDirectoryDetailed(directory, state)
            .Where(item => item.Metadata is not null)
            .Select(item => item.ToInstalled());

    private IEnumerable<ScanResult> ScanDirectoryDetailed(string directory, PluginState state)
    {
        if (!Directory.Exists(directory)) yield break;
        foreach (string path in Directory.EnumerateFiles(directory, "*.jar", SearchOption.TopDirectoryOnly))
        {
            PluginMetadata? metadata = null;
            string? error = null;
            try { metadata = PluginMetadataReader.Read(path); }
            catch (Exception exception) when (exception is InvalidDataException or IOException or UnauthorizedAccessException)
            {
                error = exception.Message;
            }
            yield return new ScanResult(path, metadata, state, error);
        }
    }

    private IEnumerable<InstalledPlugin> FindAllById(string canonicalId) =>
        ScanDirectory(_pluginsDirectory, PluginState.Enabled)
            .Concat(ScanDirectory(_disabledDirectory, PluginState.Disabled))
            .Where(item => string.Equals(item.Metadata.CanonicalId, canonicalId, StringComparison.OrdinalIgnoreCase));

    private string? GetDependencyProblem(PluginMetadata metadata, string? selfId = null)
    {
        foreach (string dependency in metadata.Dependencies)
        {
            if (selfId is not null && string.Equals(dependency, selfId, StringComparison.OrdinalIgnoreCase)) continue;
            var candidates = FindAllById(dependency).ToList();
            if (candidates.Count == 0) return $"Missing required dependency: {dependency}.";
            if (candidates.Count > 1) return $"Required dependency {dependency} has duplicate JARs.";
            if (candidates[0].State != PluginState.Enabled) return $"Required dependency {dependency} is disabled.";
        }
        return null;
    }

    private static void ValidateTargetCompatibility(PluginMetadata metadata)
    {
        if (string.IsNullOrWhiteSpace(metadata.ApiVersion)) return;
        if (!Version.TryParse(metadata.ApiVersion, out var version)) return;
        if (version.Major > 1 || (version.Major == 1 && version.Minor > 21))
            throw new InvalidDataException($"Plugin targets API {metadata.ApiVersion}, newer than LazyBuilder's Paper 1.21.4 baseline.");
    }

    private static string ProblemIdFromPath(string path)
    {
        string name = Path.GetFileNameWithoutExtension(path).ToLowerInvariant();
        string safe = string.Concat(name.Select(ch => char.IsLetterOrDigit(ch) || ch is '-' or '_' ? ch : '-')).Trim('-');
        return string.IsNullOrWhiteSpace(safe) ? "invalid-plugin" : $"invalid-{safe}";
    }

    private static string SafeJarName(string name, string version)
    {
        string safeName = string.Concat(name.Select(ch => char.IsLetterOrDigit(ch) || ch is '-' or '_' or '.' ? ch : '-'));
        string safeVersion = string.Concat(version.Select(ch => char.IsLetterOrDigit(ch) || ch is '-' or '_' or '.' ? ch : '-'));
        return $"{safeName}-{safeVersion}.jar";
    }

    private static async Task CopyAtomicAsync(string source, string destination, CancellationToken cancellationToken)
    {
        string temp = destination + ".incoming";
        if (File.Exists(temp)) File.Delete(temp);
        await using (var input = File.OpenRead(source))
        await using (var output = new FileStream(temp, FileMode.CreateNew, FileAccess.Write, FileShare.None, 81920, useAsync: true))
            await input.CopyToAsync(output, cancellationToken);
        File.Move(temp, destination, overwrite: true);
    }

    private sealed record InstalledPlugin(string Path, PluginMetadata Metadata, PluginState State);

    private sealed record ScanResult(string Path, PluginMetadata? Metadata, PluginState State, string? Error)
    {
        public InstalledPlugin ToInstalled() => new(Path, Metadata!, State);
    }
}
