using System.Windows;

namespace HaloKaryaMedia.LazyBuilder.Desktop.Shell;

public partial class MainWindow : Window
{
    public MainWindow(DashboardViewModel dashboard)
    {
        InitializeComponent();
        DataContext = dashboard;
    }
}
