using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Runtime.CompilerServices;
using System.Windows;
using System.Windows.Input;
using HaloKaryaMedia.LazyBuilder.Desktop.Shell;
using Microsoft.Win32;

namespace HaloKaryaMedia.LazyBuilder.Desktop.Modules.PluginManager;

public sealed class PluginsViewModel : INotifyPropertyChanged
{
    private readonly IPluginManager _pluginManager;
    private PluginSummary? _selectedPlugin;
    private string? _selectedCategory;
    private string? _selectedDuplicateFile;
    private string? _statusMessage;
    private bool _busy;

    public event PropertyChangedEventHandler? PropertyChanged;

    public PluginsViewModel(IPluginManager pluginManager)
    {
        _pluginManager = pluginManager;
        Categories = PluginCategoryRegistry.AllowedCategories;

        RefreshCommand = new AsyncCommand(RefreshAsync, () => !Busy);
        AddCommand = new AsyncCommand(AddAsync, () => !Busy);
        UpdateCommand = new AsyncCommand(UpdateAsync, () => !Busy && SelectedPlugin is not null && SelectedPlugin.State != PluginState.Problem);
        ToggleEnabledCommand = new AsyncCommand(ToggleEnabledAsync, () => !Busy && SelectedPlugin is { State: PluginState.Enabled or PluginState.Disabled });
        RemoveCommand = new AsyncCommand(RemoveAsync, () => !Busy && SelectedPlugin is not null && SelectedPlugin.State != PluginState.Problem);
        ApplyCategoryCommand = new AsyncCommand(ApplyCategoryAsync, () => !Busy && SelectedPlugin is not null && !string.IsNullOrWhiteSpace(SelectedCategory));
        ResolveDuplicatesCommand = new AsyncCommand(ResolveDuplicatesAsync,
            () => !Busy && SelectedPlugin?.CandidateFiles is { Count: > 1 } && !string.IsNullOrWhiteSpace(SelectedDuplicateFile));
    }

    public ObservableCollection<PluginSummary> Plugins { get; } = [];
    public IReadOnlyList<string> Categories { get; }

    public PluginSummary? SelectedPlugin
    {
        get => _selectedPlugin;
        set
        {
            if (Equals(_selectedPlugin, value)) return;
            _selectedPlugin = value;
            SelectedCategory = value?.Category;
            SelectedDuplicateFile = value?.CandidateFiles?.FirstOrDefault();
            Raise();
            Raise(nameof(HasSelection));
            Raise(nameof(ToggleEnabledLabel));
            RefreshCommands();
        }
    }

    public string? SelectedCategory
    {
        get => _selectedCategory;
        set
        {
            if (_selectedCategory == value) return;
            _selectedCategory = value;
            Raise();
            RefreshCommands();
        }
    }

    public string? SelectedDuplicateFile
    {
        get => _selectedDuplicateFile;
        set
        {
            if (_selectedDuplicateFile == value) return;
            _selectedDuplicateFile = value;
            Raise();
            RefreshCommands();
        }
    }

    public bool HasSelection => SelectedPlugin is not null;
    public string ToggleEnabledLabel => SelectedPlugin?.State == PluginState.Disabled ? "Enable" : "Disable";

    public string? StatusMessage
    {
        get => _statusMessage;
        private set
        {
            if (_statusMessage == value) return;
            _statusMessage = value;
            Raise();
        }
    }

    public bool Busy
    {
        get => _busy;
        private set
        {
            if (_busy == value) return;
            _busy = value;
            Raise();
            RefreshCommands();
        }
    }

    public ICommand RefreshCommand { get; }
    public ICommand AddCommand { get; }
    public ICommand UpdateCommand { get; }
    public ICommand ToggleEnabledCommand { get; }
    public ICommand RemoveCommand { get; }
    public ICommand ApplyCategoryCommand { get; }
    public ICommand ResolveDuplicatesCommand { get; }

    public async Task InitializeAsync() => await RefreshAsync();

    private async Task RefreshAsync()
    {
        await RunAsync(async () =>
        {
            var items = await _pluginManager.ListAsync();
            string? selectedId = SelectedPlugin?.Id;
            Plugins.Clear();
            foreach (var item in items) Plugins.Add(item);
            SelectedPlugin = selectedId is null ? null : Plugins.FirstOrDefault(item =>
                string.Equals(item.Id, selectedId, StringComparison.OrdinalIgnoreCase));
            StatusMessage = $"{Plugins.Count} plugin entries found.";
        });
    }

    private async Task AddAsync()
    {
        var dialog = new OpenFileDialog { Filter = "Minecraft plugin (*.jar)|*.jar", CheckFileExists = true, Multiselect = false };
        if (dialog.ShowDialog() != true) return;

        await RunAsync(async () =>
        {
            var result = await _pluginManager.InstallAsync(dialog.FileName);
            StatusMessage = result.Message;
            await ReloadPreservingAsync(result.PluginId);
        });
    }

    private async Task UpdateAsync()
    {
        if (SelectedPlugin is null) return;
        var dialog = new OpenFileDialog { Filter = "Minecraft plugin (*.jar)|*.jar", CheckFileExists = true, Multiselect = false };
        if (dialog.ShowDialog() != true) return;

        string pluginId = SelectedPlugin.Id;
        await RunAsync(async () =>
        {
            var result = await _pluginManager.UpdateAsync(pluginId, dialog.FileName);
            StatusMessage = result.Message;
            await ReloadPreservingAsync(pluginId);
        });
    }

    private async Task ToggleEnabledAsync()
    {
        if (SelectedPlugin is null) return;
        string pluginId = SelectedPlugin.Id;
        bool enable = SelectedPlugin.State == PluginState.Disabled;
        await RunAsync(async () =>
        {
            await _pluginManager.SetEnabledAsync(pluginId, enable);
            StatusMessage = $"{SelectedPlugin.DisplayName} will be {(enable ? "enabled" : "disabled")} after restart.";
            await ReloadPreservingAsync(pluginId);
        });
    }

    private async Task RemoveAsync()
    {
        if (SelectedPlugin is null) return;
        var answer = MessageBox.Show(
            $"Remove {SelectedPlugin.DisplayName}? Plugin data will be kept.",
            "Remove Plugin",
            MessageBoxButton.YesNo,
            MessageBoxImage.Warning);
        if (answer != MessageBoxResult.Yes) return;

        string pluginId = SelectedPlugin.Id;
        await RunAsync(async () =>
        {
            await _pluginManager.RemoveAsync(pluginId, removeData: false);
            StatusMessage = "Plugin JAR removed. Data was preserved.";
            await ReloadPreservingAsync(null);
        });
    }

    private async Task ApplyCategoryAsync()
    {
        if (SelectedPlugin is null || string.IsNullOrWhiteSpace(SelectedCategory)) return;
        string pluginId = SelectedPlugin.Id;
        await RunAsync(async () =>
        {
            await _pluginManager.SetCategoryAsync(pluginId, SelectedCategory);
            StatusMessage = "Plugin category updated.";
            await ReloadPreservingAsync(pluginId);
        });
    }

    private async Task ResolveDuplicatesAsync()
    {
        if (SelectedPlugin is null || string.IsNullOrWhiteSpace(SelectedDuplicateFile)) return;
        string pluginId = SelectedPlugin.Id;
        string keepFile = SelectedDuplicateFile;
        var answer = MessageBox.Show(
            $"Keep {keepFile} and move the other duplicate JARs to backup?",
            "Resolve Duplicate Plugin",
            MessageBoxButton.YesNo,
            MessageBoxImage.Warning);
        if (answer != MessageBoxResult.Yes) return;

        await RunAsync(async () =>
        {
            var result = await _pluginManager.ResolveDuplicatesAsync(pluginId, keepFile);
            StatusMessage = result.Message;
            await ReloadPreservingAsync(pluginId);
        });
    }

    private async Task ReloadPreservingAsync(string? pluginId)
    {
        var items = await _pluginManager.ListAsync();
        Plugins.Clear();
        foreach (var item in items) Plugins.Add(item);
        SelectedPlugin = pluginId is null ? null : Plugins.FirstOrDefault(item =>
            string.Equals(item.Id, pluginId, StringComparison.OrdinalIgnoreCase));
    }

    private async Task RunAsync(Func<Task> action)
    {
        if (Busy) return;
        Busy = true;
        try
        {
            await action();
        }
        catch (Exception exception)
        {
            StatusMessage = exception.Message;
        }
        finally
        {
            Busy = false;
        }
    }

    private void RefreshCommands()
    {
        foreach (var command in new[]
                 {
                     RefreshCommand, AddCommand, UpdateCommand, ToggleEnabledCommand,
                     RemoveCommand, ApplyCategoryCommand, ResolveDuplicatesCommand
                 }.OfType<AsyncCommand>())
            command.RaiseCanExecuteChanged();
    }

    private void Raise([CallerMemberName] string? propertyName = null) =>
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
}
