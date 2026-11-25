using System.IO;
using System.Net.Sockets;
using System.Windows;
using System.Windows.Input;
using System.Windows.Media;
using A4Launcher.Services;

namespace A4Launcher.Dialogs;

/// <summary>注册 / 修改密码共用一个对话框：改密时多一个「新密码」输入框。</summary>
public partial class AccountDialog : Window
{
    public enum Mode
    {
        Register,
        ChangePassword,
    }

    private readonly Settings _settings;
    private Mode _mode;

    public string ResultAccount { get; private set; } = "";
    public string ResultPassword { get; private set; } = "";
    public bool ResultIsRegister { get; private set; }

    public AccountDialog(Settings settings, Mode mode)
    {
        InitializeComponent();
        _settings = settings;
        ApplyMode(mode);
        Loaded += (_, _) => AccountBox.Focus();
    }

    /// <summary>注册 / 改密封在同一个窗口里，只换标题、字段和左边那句链接。</summary>
    private void ApplyMode(Mode mode)
    {
        _mode = mode;
        var register = mode == Mode.Register;
        Title = register ? "注册帐号" : "修改密码";
        TitleText.Text = Title;
        OldLabel.Text = register ? "密　码" : "原密码";
        // 行高 0 + 隐藏：只隐藏的话 Grid 行还占着 23px
        NewRow.Height = new GridLength(register ? 0 : 23);
        NewLabel.Visibility = register ? Visibility.Collapsed : Visibility.Visible;
        NewBox.Visibility = register ? Visibility.Collapsed : Visibility.Visible;
        CdkeyRow.Height = new GridLength(register ? 23 : 0);
        CdkeyLabel.Visibility = register ? Visibility.Visible : Visibility.Collapsed;
        CdkeyBox.Visibility = register ? Visibility.Visible : Visibility.Collapsed;
        SwitchLink.Text = register ? "已有帐号？改密" : "没有帐号？注册";
        SetHint();
    }

    private void SwitchMode_Click(object sender, MouseButtonEventArgs e)
    {
        ApplyMode(_mode == Mode.Register ? Mode.ChangePassword : Mode.Register);
        (OldBox.Password.Length == 0 ? OldBox : NewBox).Focus();
    }

    // 没有系统标题栏，拖标题条移动窗口
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

    private void Close_Click(object sender, RoutedEventArgs e)
    {
        DialogResult = false;
    }

    public void PrefillAccount(string account)
    {
        AccountBox.Text = account;
        OldBox.Focus();
    }

    // 平时是一句米色说明（原版「游戏协议和玩家条例」那个颜色），出错换红字
    private void SetHint()
    {
        StatusText.Text = _mode == Mode.Register ? "帐号限 1~10 位字母、数字、下划线" : "";
        StatusText.Foreground = (Brush)FindResource("ArtNoteFg");
    }

    private void SetStatus(string text)
    {
        StatusText.Text = text;
        StatusText.Foreground = (Brush)FindResource("BadFg");
    }

    private async void OkButton_Click(object sender, RoutedEventArgs e)
    {
        var account = AccountBox.Text.Trim();
        var oldPass = OldBox.Password;
        if (account.Length == 0 || oldPass.Length == 0)
        {
            SetStatus("请填写账号和密码");
            return;
        }

        OkButton.IsEnabled = false;
        try
        {
            var client = new GatewayClient(_settings.GatewayHost, _settings.GatewayPort);
            if (_mode == Mode.Register)
            {
                var confirm = NewBox.Visibility == Visibility.Visible ? NewBox.Password : oldPass;
                if (confirm != oldPass)
                {
                    SetStatus("两次输入的密码不一致");
                    return;
                }
                var reply = await client.CallAsync("reg", CancellationToken.None,
                    ("u", account), ("p", oldPass), ("k", CdkeyBox.Text.Trim()));
                if (!reply.Ok)
                {
                    SetStatus(reply.Message);
                    return;
                }
                ResultAccount = account;
                ResultPassword = oldPass;
                ResultIsRegister = true;
                ArtMessage.Show(this, "注册帐号", "注册成功，已帮您填好帐号密码。");
                DialogResult = true;
            }
            else
            {
                var newPass = NewBox.Password;
                if (newPass.Length == 0)
                {
                    SetStatus("请填写新密码");
                    return;
                }
                var reply = await client.CallAsync("reSetPassword", CancellationToken.None,
                    ("u", account), ("p", oldPass), ("n", newPass));
                if (!reply.Ok)
                {
                    SetStatus(reply.Message);
                    return;
                }
                ResultAccount = account;
                ResultPassword = newPass;
                ArtMessage.Show(this, "修改密码", "密码已修改。");
                DialogResult = true;
            }
        }
        catch (SocketException)
        {
            SetStatus("连不上网关，请检查网络");
        }
        catch (Exception ex) when (ex is InvalidDataException or IOException
                                       or FormatException
                                       or OperationCanceledException)
        {
            SetStatus("失败：" + ex.Message);
        }
        finally
        {
            OkButton.IsEnabled = true;
        }
    }
}
