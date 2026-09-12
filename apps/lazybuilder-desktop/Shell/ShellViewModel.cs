using System.ComponentModel;
using System.Runtime.CompilerServices;
using System.Windows.Input;
using HaloKaryaMedia.LazyBuilder.Desktop.Modules.PluginManager;

namespace HaloKaryaMedia.LazyBuilder.Desktop.Shell;

public sealed class ShellViewModel : INotifyPropertyChanged, IAsyncDisposable
{
    private object _currentPage;
    private string _currentSection = "Dashboard";

    public ShellViewModel(DashboardViewModel dashboard, PluginsViewModel plugins)
    {
        Dashboard = dashboard;
        Plugins = plugins;
        Worlds = new PlaceholderPageViewModel("Worlds", "World management will be provided by World-Manager without duplicating server authority in the desktop app.");
        Settings = new PlaceholderPageViewModel("Settings", "Only server settings that help normal operation will be exposed here. Technical controls stay under Advanced.");

        _currentPage = Dashboard;
        ShowDashboardCommand = new RelayCommand(() => Navigate("Dashboard", Dashboard));
        ShowWorldsCommand = new RelayCommand(() => Navigate("Worlds", Worlds));
        ShowPluginsCommand = new RelayCommand(() => Navigate("Plugins", Plugins));
        ShowSettingsCommand = new RelayCommand(() => Navigate("Settings", Settings));
    }

    public event PropertyChangedEventHandler? PropertyChanged;

    public DashboardViewModel Dashboard { get; }
    public PluginsViewModel Plugins { get; }
    public PlaceholderPageViewModel Worlds { get; }
    public PlaceholderPageViewModel Settings { get; }

    public object CurrentPage
    {
        get => _currentPage;
        private set
        {
            if (ReferenceEquals(_currentPage, value)) return;
            _currentPage = value;
            Raise();
        }
    }

    public string CurrentSection
    {
        get => _currentSection;
        private set
        {
            if (_currentSection == value) return;
            _currentSection = value;
            Raise();
        }
    }

    public ICommand ShowDashboardCommand { get; }
    public ICommand ShowWorldsCommand { get; }
    public ICommand ShowPluginsCommand { get; }
    public ICommand ShowSettingsCommand { get; }

    private void Navigate(string section, object page)
    {
        CurrentSection = section;
        CurrentPage = page;
    }

    private void Raise([CallerMemberName] string? propertyName = null) =>
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));

    public async ValueTask DisposeAsync() => await Dashboard.DisposeAsync();
}
