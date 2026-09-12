using System.Windows;
using HaloKaryaMedia.LazyBuilder.Desktop.Modules.ServerManager;
using HaloKaryaMedia.LazyBuilder.Desktop.Shell;

namespace HaloKaryaMedia.LazyBuilder.Desktop;

public partial class App : Application
{
    private DashboardViewModel? _dashboard;

    protected override async void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        string workspaceRoot = WorkspaceRootLocator.Resolve();
        var configStore = new ServerManagerConfigStore(workspaceRoot);
        ServerManagerOptions options;

        try
        {
            options = await configStore.LoadAsync();
        }
        catch
        {
            options = new ServerManagerOptions();
        }

        var serverManager = new ServerProcessManager(workspaceRoot, options);
        _dashboard = new DashboardViewModel(serverManager);

        var window = new MainWindow(_dashboard);
        MainWindow = window;
        window.Show();
    }

    protected override async void OnExit(ExitEventArgs e)
    {
        if (_dashboard is not null)
            await _dashboard.DisposeAsync();
        base.OnExit(e);
    }
}
