using System.Diagnostics;
using System.IO;
using System.Net.Sockets;
using System.Windows;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;
using A4Launcher.Dialogs;
using A4Launcher.Services;

namespace A4Launcher;

public partial class MainWindow : Window
{
    private readonly Settings _settings = Settings.Load();
    private CancellationTokenSource? _busy;

    // 原版那个「已阅读并同意协议」的勾，我们拿来当「记住密码」：默认勾上、默认记住
    private bool _remember = true;

    public MainWindow()
    {
        InitializeComponent();
        AccountBox.Text = _settings.SavedAccount;
        var saved = CredentialProtection.Unprotect(_settings.ProtectedPassword);
        if (saved is not null)
        {
            PasswordBox.Password = saved;
        }
        AgreeTick.Visibility = _remember ? Visibility.Visible : Visibility.Collapsed;
        FullscreenTick.Visibility = _settings.Fullscreen ? Visibility.Visible : Visibility.Collapsed;

        // 一打开就是「选择大区 / 服务器」这一页，确认选择之后才回登录页
        ShowCategory("电信");
        PageLogin.Visibility = Visibility.Collapsed;
        PageServers.Visibility = Visibility.Visible;

        Loaded += async (_, _) => await RefreshNoticesAsync();
    }

    // ---------- 窗口拖动 / 关闭 ----------

    private void Root_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
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
            // 鼠标已经松开，忽略
        }
    }

    private void Close_Click(object sender, RoutedEventArgs e) => Close();

    // 原版那行「已阅读并同意…」的勾 → 记住密码开关
    private void Agree_Click(object sender, MouseButtonEventArgs e)
    {
        _remember = !_remember;
        AgreeTick.Visibility = _remember ? Visibility.Visible : Visibility.Collapsed;
    }

    // ---------- 两页切换 ----------

    // 第二页的「第一步」选的是哪个分类：电信 / 网通
    private string _category = "电信";

    private void SelectServer_Click(object sender, RoutedEventArgs e)
    {
        ShowCategory("电信");
        PageLogin.Visibility = Visibility.Collapsed;
        PageServers.Visibility = Visibility.Visible;
    }

    private void Telecom_Click(object sender, MouseButtonEventArgs e) => ShowCategory("电信");

    private void Netcom_Click(object sender, MouseButtonEventArgs e) => ShowCategory("网通");

    /// <summary>
    /// 大区列表是照原版做的样子：分类下的大区名都来自 launcher.ini 的 [regions]，
    /// 私服一般只有一台游戏服，选哪个都连同一台。
    /// </summary>
    private void ShowCategory(string category)
    {
        _category = category;
        var text = category == "电信" ? _settings.TelecomRegions : _settings.NetcomRegions;
        RegionList.Items.Clear();
        foreach (var entry in GameEntry.Parse(text, _settings.ServerState, region: true))
        {
            RegionList.Items.Add(entry);
        }
        if (RegionList.Items.Count > 0)
        {
            RegionList.SelectedIndex = 0;
        }
        else
        {
            ServerList.Items.Clear();
            CurrentName.Text = "";
        }
    }

    private void ConfirmServer_Click(object sender, RoutedEventArgs e)
    {
        PageServers.Visibility = Visibility.Collapsed;
        PageLogin.Visibility = Visibility.Visible;
        if (AccountBox.Text.Length > 0)
        {
            PasswordBox.Focus();
        }
        else
        {
            AccountBox.Focus();
        }
    }

    private void RegionList_SelectionChanged(object sender, System.Windows.Controls.SelectionChangedEventArgs e)
    {
        ServerList.Items.Clear();
        if (RegionList.SelectedItem is not GameEntry)
        {
            CurrentName.Text = "";
            return;
        }
        foreach (var entry in GameEntry.Parse(_settings.ServerNames, _settings.ServerState, region: false))
        {
            ServerList.Items.Add(entry);
        }
        if (ServerList.Items.Count > 0)
        {
            ServerList.SelectedIndex = 0;
        }
    }

    /// <summary>底下的「当前：」只写服务器名，后面那句「（未连接）」是原版的样子，连上以后再改。</summary>
    private void ServerList_SelectionChanged(object sender, System.Windows.Controls.SelectionChangedEventArgs e)
    {
        CurrentName.Text = ServerList.SelectedItem is GameEntry entry ? entry.Name : "";
    }

    private void Fullscreen_Click(object sender, MouseButtonEventArgs e)
    {
        _settings.Fullscreen = !_settings.Fullscreen;
        _settings.Save();
        FullscreenTick.Visibility = _settings.Fullscreen ? Visibility.Visible : Visibility.Collapsed;
    }

    // ---------- 公告跑马灯 ----------

    private async Task RefreshNoticesAsync()
    {
        string text;
        try
        {
            var client = new GatewayClient(_settings.GatewayHost, _settings.GatewayPort);
            var reply = await client.CallAsync("news", CancellationToken.None);
            var parts = new List<string>();
            if (reply.Root.TryGet("list", out var list) && list.Items.Count > 0)
            {
                foreach (var item in list.Items)
                {
                    var title = item.TryGet("title", out var t) ? t.AsString() : "";
                    var content = item.TryGet("content", out var c) ? c.AsString() : "";
                    parts.Add(string.IsNullOrWhiteSpace(title) ? content ?? "" : $"【{title}】{content}");
                }
            }
            if (parts.Count == 0)
            {
                TickerBar.Visibility = Visibility.Collapsed;
                return;
            }
            text = string.Join("        ", parts);
        }
        catch (Exception e) when (e is SocketException or IOException or InvalidDataException
                                      or OperationCanceledException or FormatException)
        {
            TickerBar.Visibility = Visibility.Collapsed;
            SetStatus("暂时取不到公告，稍后自动重试", false);
            Log.Write("取公告失败", e);
            return;
        }

        TickerText.Text = text;
        TickerBar.Visibility = Visibility.Visible;
        StartTicker();
    }

    private void StartTicker()
    {
        TickerText.Measure(new Size(double.PositiveInfinity, double.PositiveInfinity));
        var width = TickerText.DesiredSize.Width;
        if (width <= 0)
        {
            return;
        }
        var travel = width + 618;   // 618 = 公告条宽度（原图深棕带子 11..628）
        var duration = TimeSpan.FromSeconds(travel / 55.0);
        var anim = new DoubleAnimation
        {
            From = 618,
            To = -width,
            Duration = duration,
            RepeatBehavior = RepeatBehavior.Forever,
        };
        TickerText.BeginAnimation(System.Windows.Controls.Canvas.LeftProperty, anim);
    }

    // ---------- 登录 ----------

    private void SetStatus(string text, bool ok)
    {
        StatusText.Text = text;
        StatusText.Foreground = (Brush)FindResource(ok ? "OkFg" : "BadFg");
        StatusPanel.Visibility = text.Length > 0 ? Visibility.Visible : Visibility.Collapsed;
    }

    private async void PlayButton_Click(object sender, RoutedEventArgs e)
    {
        var account = AccountBox.Text.Trim();
        var password = PasswordBox.Password;
        if (account.Length == 0 || password.Length == 0)
        {
            SetStatus("请填写账号和密码", false);
            return;
        }

        // 客户端只能单开：已经有一个在跑就问要不要强制关掉。放在登录之前，免得白拿一次令牌
        if (!EnsureNoRunningGame())
        {
            return;
        }

        _busy?.Cancel();
        _busy = new CancellationTokenSource();
        PlayButton.IsEnabled = false;
        SetStatus("正在登录…", true);
        try
        {
            var client = new GatewayClient(_settings.GatewayHost, _settings.GatewayPort);
            var reply = await client.CallAsync("login", _busy.Token,
                ("u", account), ("p", password));
            if (!reply.Ok)
            {
                SetStatus(reply.Message.Length > 0 ? reply.Message : "登录失败", false);
                return;
            }

            var token = reply.Text("token");
            var host = _settings.GameHost.Length > 0 ? _settings.GameHost : reply.Text("host");
            var portText = reply.Text("port");
            var port = int.TryParse(portText, out var p) ? p : _settings.GamePort;

            if (_remember)
            {
                _settings.SavedAccount = account;
                _settings.ProtectedPassword = CredentialProtection.Protect(password);
            }
            else
            {
                _settings.SavedAccount = "";
                _settings.ProtectedPassword = "";
            }
            _settings.Save();

            GameLauncher.Start(host, port, account, token, _settings.AllowMulti);
            // 原版启动游戏后不出任何提示，直接缩到任务栏
            SetStatus("", true);
            WindowState = WindowState.Minimized;
        }
        catch (OperationCanceledException ex)
        {
            SetStatus("登录超时，请检查网络后重试", false);
            Log.Write("登录超时", ex);
        }
        catch (SocketException ex)
        {
            SetStatus("连不上网关，请检查 launcher.ini 里的地址和端口", false);
            Log.Write("连不上网关", ex);
        }
        catch (Exception ex) when (ex is InvalidDataException or IOException
                                       or FormatException
                                       or InvalidOperationException or UnauthorizedAccessException)
        {
            SetStatus("启动失败：" + ex.Message, false);
            Log.Write("启动失败", ex);
        }
        finally
        {
            PlayButton.IsEnabled = true;
        }
    }

    /// <summary>没有在跑的游戏，或者玩家同意并且确实关掉了，返回 true。</summary>
    private bool EnsureNoRunningGame()
    {
        if (_settings.AllowMulti)
        {
            return true;
        }
        var running = GameLauncher.FindRunning();
        var count = running.Length;
        foreach (var process in running)
        {
            process.Dispose();
        }
        if (count == 0)
        {
            return true;
        }

        if (!ArtMessage.Ask(this, "地下城与勇士",
                "检测到游戏已经在运行。" + Environment.NewLine + "是否强制关闭游戏进程并重新启动？",
                "强制关闭", "取　消"))
        {
            return false;
        }

        Cursor = Cursors.Wait;
        int left;
        try
        {
            left = GameLauncher.KillRunning();
        }
        finally
        {
            Cursor = null;
        }
        if (left > 0)
        {
            ArtMessage.Show(this, "地下城与勇士", "游戏进程关不掉，请用任务管理器结束 DNF.exe 后再试。");
            return false;
        }
        return true;
    }

    // ---------- 导航按钮 ----------

    // 顶部导航「注册帐号」：注册 / 改密共用同一个对话框，进去以后点一下就能切换
    private void RegisterButton_Click(object sender, RoutedEventArgs e)
    {
        var dialog = new AccountDialog(_settings, AccountDialog.Mode.Register);
        if (AccountBox.Text.Length > 0)
        {
            dialog.PrefillAccount(AccountBox.Text);
        }
        if (dialog.ShowDialog() == true)
        {
            AccountBox.Text = dialog.ResultAccount;
            PasswordBox.Password = dialog.ResultPassword;
            SetStatus(dialog.ResultIsRegister ? "注册成功" : "密码已修改", true);
        }
    }

    // 顶部导航「DNF助手」照原版是个网页链接
    private void HelperButton_Click(object sender, RoutedEventArgs e) => OpenLink(_settings.HelperUrl);

    private void WebsiteButton_Click(object sender, RoutedEventArgs e) => OpenLink(_settings.WebsiteUrl);

    private void ShopButton_Click(object sender, RoutedEventArgs e) => OpenLink(_settings.ShopUrl);

    private void ForumButton_Click(object sender, RoutedEventArgs e) => OpenLink(_settings.ForumUrl);

    private void ServiceButton_Click(object sender, RoutedEventArgs e) => OpenLink(_settings.ServiceUrl);

    private void OpenLink(string url)
    {
        if (string.IsNullOrWhiteSpace(url))
        {
            SetStatus("后台还没配这个链接（launcher.ini 的 [links]）", false);
            return;
        }
        try
        {
            Process.Start(new ProcessStartInfo { FileName = url, UseShellExecute = true });
        }
        catch (Exception ex) when (ex is System.ComponentModel.Win32Exception or InvalidOperationException)
        {
            SetStatus("打不开浏览器：" + ex.Message, false);
        }
    }
}
