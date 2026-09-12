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
        var installed = ScanDirectory(_pluginsDirectory, PluginState.Enabled)
            .Concat(ScanDirectory(_disabledDirectory, PluginState.Disabled))
            .ToList();

        var byId = installed.GroupBy(item => item.Metadata.CanonicalId, StringComparer.OrdinalIgnoreCase).ToList();
        var presentIds = byId.Select(group => group.Key).ToHashSet(StringComparer.OrdinalIgnoreCase);
        var result = new List<PluginSummary>();

        foreach (var group in byId.OrderBy(group => group.Key, StringComparer.OrdinalIgnoreCase))
        {
            var candidates = group.ToList();
            var primary = candidates.OrderByDescending(item => item.State == PluginState.Enabled).First();
            PluginState state = candidates.Count > 1 ? PluginState.Problem : primary.State;

            if (state != PluginState.Problem && primary.Metadata.Dependencies.Any(dep => !presentIds.Contains(dep)))
                state = PluginState.Problem;

            result.Add(new PluginSummary(
                primary.Metadata.CanonicalId,
                primary.Metadata.Name,
                primary.Metadata.Version,
                PluginCategoryRegistry.Resolve(primary.Metadata.CanonicalId, primary.Metadata.Name),
                state
            ));
        }

        return Task.FromResult<IReadOnlyList<PluginSummary>>(result);
    }

    public async Task<PluginInstallResult> InstallAsync(string jarPath, CancellationToken cancellationToken = default)
    {
        await _mutationGate.WaitAsync(cancellationToken);
        try
        {
            var incoming = PluginMetadataReader.Read(jarPath);
            ValidateTargetCompatibility(incoming);

            var existing = FindAllById(incoming.CanonicalId).ToList();
            if (existing.Count > 0)
            {
                var current = existing[0].Metadata.Version;
                return new PluginInstallResult(false, incoming.CanonicalId,
                    $"Plugin is already installed (current {current}, selected {incoming.Version}). Use Update instead.", false);
            }

            Directory.CreateDirectory(_pluginsDirectory);
            string destination = Path.Combine(_pluginsDirectory, SafeJarName(incoming.Name, incoming.Version));
            await CopyAtomicAsync(jarPath, destination, cancellationToken);
            return new PluginInstallResult(true, incoming.CanonicalId, "Plugin installed.", true);
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
            var incoming = PluginMetadataReader.Read(jarPath);
            ValidateTargetCompatibility(incoming);
            if (!string.Equals(canonicalId, incoming.CanonicalId, StringComparison.OrdinalIgnoreCase))
                return new PluginInstallResult(false, canonicalId, "Selected JAR belongs to a different plugin.", false);

            var existing = FindAllById(canonicalId).ToList();
            if (existing.Count == 0)
                return new PluginInstallResult(false, canonicalId, "Plugin is not installed.", false);
            if (existing.Count > 1)
                return new PluginInstallResult(false, canonicalId, "Duplicate plugin versions must be resolved before updating.", false);

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
                $"Updated {current.Metadata.Version} → {incoming.Version}.", true);
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

    private IEnumerable<InstalledPlugin> ScanDirectory(string directory, PluginState state)
    {
        if (!Directory.Exists(directory)) yield break;
        foreach (string path in Directory.EnumerateFiles(directory, "*.jar", SearchOption.TopDirectoryOnly))
        {
            PluginMetadata metadata;
            try { metadata = PluginMetadataReader.Read(path); }
            catch { continue; }
            yield return new InstalledPlugin(path, metadata, state);
        }
    }

    private IEnumerable<InstalledPlugin> FindAllById(string canonicalId) =>
        ScanDirectory(_pluginsDirectory, PluginState.Enabled)
            .Concat(ScanDirectory(_disabledDirectory, PluginState.Disabled))
            .Where(item => string.Equals(item.Metadata.CanonicalId, canonicalId, StringComparison.OrdinalIgnoreCase));

    private static void ValidateTargetCompatibility(PluginMetadata metadata)
    {
        if (string.IsNullOrWhiteSpace(metadata.ApiVersion)) return;
        if (!Version.TryParse(metadata.ApiVersion, out var version)) return;
        if (version.Major > 1 || (version.Major == 1 && version.Minor > 21))
            throw new InvalidDataException($"Plugin targets API {metadata.ApiVersion}, newer than LazyBuilder's Paper 1.21.4 baseline.");
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
}
