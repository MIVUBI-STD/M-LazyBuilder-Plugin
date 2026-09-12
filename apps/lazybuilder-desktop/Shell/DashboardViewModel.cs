using System.ComponentModel;
using System.Runtime.CompilerServices;
using System.Windows;
using System.Windows.Input;
using HaloKaryaMedia.LazyBuilder.Desktop.Modules.ServerManager;

namespace HaloKaryaMedia.LazyBuilder.Desktop.Shell;

public sealed class DashboardViewModel : INotifyPropertyChanged, IAsyncDisposable
{
    private readonly IServerManager _serverManager;
    private bool _busy;
    private string? _lastError;

    public event PropertyChangedEventHandler? PropertyChanged;

    public DashboardViewModel(IServerManager serverManager)
    {
        _serverManager = serverManager;
        if (_serverManager is ServerProcessManager processManager)
            processManager.SnapshotChanged += OnServerSnapshotChanged;

        StartCommand = new AsyncCommand(StartAsync, () => !Busy && State is ServerState.Offline or ServerState.Crashed);
        StopCommand = new AsyncCommand(StopAsync, () => !Busy && State is ServerState.Starting or ServerState.Online);
        RestartCommand = new AsyncCommand(RestartAsync, () => !Busy && State == ServerState.Online);
    }

    public ServerState State => _serverManager.State;
    public string ServerStatus => State.ToString();
    public string HealthStatus => _serverManager.Health.Status;
    public string CpuText => $"{_serverManager.Health.CpuLoadPercent:0.#}%";
    public string RamText
    {
        get
        {
            var health = _serverManager.Health;
            if (health.MaxMemoryBytes <= 0) return "—";
            return $"{ToGiB(health.UsedMemoryBytes):0.0} / {ToGiB(health.MaxMemoryBytes):0.0} GB";
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

    public ICommand StartCommand { get; }
    public ICommand StopCommand { get; }
    public ICommand RestartCommand { get; }

    private Task StartAsync() => RunActionAsync(_serverManager.StartAsync);
    private Task StopAsync() => RunActionAsync(_serverManager.StopAsync);
    private Task RestartAsync() => RunActionAsync(_serverManager.RestartAsync);

    private async Task RunActionAsync(Func<CancellationToken, Task> action)
    {
        if (Busy) return;
        Busy = true;
        LastError = null;
        try
        {
            await action(CancellationToken.None);
        }
        catch (Exception exception)
        {
            LastError = exception.Message;
        }
        finally
        {
            Busy = false;
            RefreshSnapshot();
        }
    }

    private void OnServerSnapshotChanged(object? sender, EventArgs e)
    {
        var dispatcher = Application.Current?.Dispatcher;
        if (dispatcher is null || dispatcher.CheckAccess()) RefreshSnapshot();
        else dispatcher.BeginInvoke(RefreshSnapshot);
    }

    private void RefreshSnapshot()
    {
        Raise(nameof(State));
        Raise(nameof(ServerStatus));
        Raise(nameof(HealthStatus));
        Raise(nameof(CpuText));
        Raise(nameof(RamText));
        RefreshCommands();
    }

    private void RefreshCommands()
    {
        (StartCommand as AsyncCommand)?.RaiseCanExecuteChanged();
        (StopCommand as AsyncCommand)?.RaiseCanExecuteChanged();
        (RestartCommand as AsyncCommand)?.RaiseCanExecuteChanged();
    }

    private void Raise([CallerMemberName] string? propertyName = null) =>
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));

    private static double ToGiB(long bytes) => bytes / 1024d / 1024d / 1024d;

    public async ValueTask DisposeAsync()
    {
        if (_serverManager is ServerProcessManager processManager)
            processManager.SnapshotChanged -= OnServerSnapshotChanged;
        if (_serverManager is IAsyncDisposable disposable)
            await disposable.DisposeAsync();
    }
}
