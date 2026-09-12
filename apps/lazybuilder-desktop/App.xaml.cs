using System.Windows;
using HaloKaryaMedia.LazyBuilder.Desktop.Modules.PluginManager;
using HaloKaryaMedia.LazyBuilder.Desktop.Modules.ServerManager;
using HaloKaryaMedia.LazyBuilder.Desktop.Modules.WorldManager;
using HaloKaryaMedia.LazyBuilder.Desktop.Shell;

namespace HaloKaryaMedia.LazyBuilder.Desktop;

public partial class App : Application
{
    private ShellViewModel? _shell;

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

        var worldControlOptions = await new WorldControlConfigStore(workspaceRoot).LoadOrCreateAsync();
        var paperEnvironment = new Dictionary<string, string>(StringComparer.Ordinal)
        {
            ["LAZYBUILDER_WORLD_CONTROL_TOKEN"] = worldControlOptions.Token,
            ["LAZYBUILDER_WORLD_CONTROL_PORT"] = worldControlOptions.Port.ToString()
        };

        var serverManager = new ServerProcessManager(workspaceRoot, options, paperEnvironment);
        var dashboard = new DashboardViewModel(serverManager);
        var worldControl = new HttpWorldManagerControlClient(worldControlOptions);
        var worlds = new WorldsViewModel(worldControl, serverManager);
        var pluginManager = new FileSystemPluginManager(workspaceRoot);
        var plugins = new PluginsViewModel(pluginManager);
        await plugins.InitializeAsync();

        _shell = new ShellViewModel(dashboard, worlds, plugins);
        var window = new MainWindow(_shell);
        MainWindow = window;
        window.Show();
    }

    protected override async void OnExit(ExitEventArgs e)
    {
        if (_shell is not null)
            await _shell.DisposeAsync();
        base.OnExit(e);
    }
}
