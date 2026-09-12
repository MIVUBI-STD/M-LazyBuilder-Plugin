using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Runtime.CompilerServices;
using System.Windows;
using System.Windows.Input;
using HaloKaryaMedia.LazyBuilder.Desktop.Modules.ServerManager;
using HaloKaryaMedia.LazyBuilder.Desktop.Modules.WorldManager;

namespace HaloKaryaMedia.LazyBuilder.Desktop.Shell;

public sealed class WorldsViewModel : INotifyPropertyChanged, IDisposable
{
    private readonly IWorldManagerControl _worldControl;
    private readonly IServerManager _serverManager;
    private bool _busy;
    private string _connectionStatus = "Server offline";
    private string? _lastError;

    public WorldsViewModel(IWorldManagerControl worldControl, IServerManager serverManager)
    {
        _worldControl = worldControl;
        _serverManager = serverManager;
        RefreshCommand = new AsyncCommand(RefreshAsync, () => !Busy);
        if (_serverManager is ServerProcessManager processManager)
            processManager.SnapshotChanged += OnServerSnapshotChanged;
    }

    public event PropertyChangedEventHandler? PropertyChanged;

    public ObservableCollection<ManagedWorldSummary> Worlds { get; } = new();
    public ICommand RefreshCommand { get; }

    public bool Busy
    {
        get => _busy;
        private set
        {
            if (_busy == value) return;
            _busy = value;
            Raise();
            (RefreshCommand as AsyncCommand)?.RaiseCanExecuteChanged();
        }
    }

    public string ConnectionStatus
    {
        get => _connectionStatus;
        private set
        {
            if (_connectionStatus == value) return;
            _connectionStatus = value;
            Raise();
        }
    }

    public string? LastError
    {
        get => _lastError;
        private set
        {
            if (_lastError == value) return;
            _lastError = value;
            Raise();
            Raise(nameof(HasError));
        }
    }

    public bool HasError => !string.IsNullOrWhiteSpace(LastError);
    public bool HasWorlds => Worlds.Count > 0;

    public async Task RefreshAsync()
    {
        if (Busy) return;
        if (_serverManager.State != ServerState.Online)
        {
            ConnectionStatus = "Start the server to manage worlds";
            LastError = null;
            Worlds.Clear();
            Raise(nameof(HasWorlds));
            return;
        }

        Busy = true;
        LastError = null;
        try
        {
            var status = await _worldControl.GetStatusAsync();
            if (!string.Equals(status.Status, "ready", StringComparison.OrdinalIgnoreCase))
                throw new InvalidOperationException("World-Manager control bridge is not ready.");

            var worlds = await _worldControl.ListWorldsAsync();
            Worlds.Clear();
            foreach (var world in worlds.OrderBy(item => item.Lifecycle).ThenBy(item => item.DisplayName, StringComparer.OrdinalIgnoreCase))
                Worlds.Add(world);
            ConnectionStatus = $"World-Manager ready · {Worlds.Count} world{(Worlds.Count == 1 ? string.Empty : "s")}";
            Raise(nameof(HasWorlds));
        }
        catch (Exception exception) when (exception is HttpRequestException or TaskCanceledException or InvalidOperationException or InvalidDataException)
        {
            Worlds.Clear();
            ConnectionStatus = "World-Manager unavailable";
            LastError = exception.Message;
            Raise(nameof(HasWorlds));
        }
        finally
        {
            Busy = false;
        }
    }

    private void OnServerSnapshotChanged(object? sender, EventArgs e)
    {
        var dispatcher = Application.Current?.Dispatcher;
        if (dispatcher is null) return;
        dispatcher.BeginInvoke(async () =>
        {
            if (_serverManager.State == ServerState.Online) await RefreshAsync();
            else if (_serverManager.State is ServerState.Offline or ServerState.Crashed)
            {
                Worlds.Clear();
                ConnectionStatus = "Start the server to manage worlds";
                LastError = null;
                Raise(nameof(HasWorlds));
            }
        });
    }

    private void Raise([CallerMemberName] string? propertyName = null) =>
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));

    public void Dispose()
    {
        if (_serverManager is ServerProcessManager processManager)
            processManager.SnapshotChanged -= OnServerSnapshotChanged;
        if (_worldControl is IDisposable disposable)
            disposable.Dispose();
    }
}
