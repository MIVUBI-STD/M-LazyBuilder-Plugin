using System.Windows;

namespace HaloKaryaMedia.LazyBuilder.Desktop.Shell;

public partial class MainWindow : Window
{
    public MainWindow(ShellViewModel shell)
    {
        InitializeComponent();
        DataContext = shell;
    }
}
