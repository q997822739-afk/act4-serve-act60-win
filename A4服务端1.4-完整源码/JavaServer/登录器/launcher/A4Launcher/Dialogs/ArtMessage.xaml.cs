using System.Windows;
using System.Windows.Input;

namespace A4Launcher.Dialogs;

/// <summary>原版素材拼的提示 / 询问框，代替系统 MessageBox（系统框和启动器外观对不上）。</summary>
public partial class ArtMessage : Window
{
    private ArtMessage(string title, string body, string okText, string? cancelText)
    {
        InitializeComponent();
        Title = title;
        TitleText.Text = title;
        BodyText.Text = body;
        OkButton.Content = okText;
        if (cancelText is null)
        {
            CancelButton.Visibility = Visibility.Collapsed;
        }
        else
        {
            CancelButton.Content = cancelText;
        }
    }

    /// <summary>只有一个「确定」的提示。</summary>
    public static void Show(Window? owner, string title, string body)
        => Open(owner, new ArtMessage(title, body, "确　定", null));

    /// <summary>两个按钮的询问；点左边那个返回 true，右边 / 关闭 / Esc 返回 false。</summary>
    public static bool Ask(Window? owner, string title, string body, string okText, string cancelText)
        => Open(owner, new ArtMessage(title, body, okText, cancelText));

    private static bool Open(Window? owner, ArtMessage dialog)
    {
        if (owner is not null && owner.IsVisible)
        {
            dialog.Owner = owner;
            // 主窗口缩在任务栏时 CenterOwner 会算到屏幕外，先把主窗口还原
            if (owner.WindowState == WindowState.Minimized)
            {
                owner.WindowState = WindowState.Normal;
            }
        }
        else
        {
            dialog.WindowStartupLocation = WindowStartupLocation.CenterScreen;
        }
        return dialog.ShowDialog() == true;
    }

    private void Header_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        if (e.ButtonState != MouseButtonState.Pressed)
        {
            return;
        }
        try
        {
            DragMove();
        }
        catch (InvalidOperationException)
        {
            // 鼠标已经松开
        }
    }

    private void Ok_Click(object sender, RoutedEventArgs e) => DialogResult = true;

    private void Cancel_Click(object sender, RoutedEventArgs e) => DialogResult = false;
}
