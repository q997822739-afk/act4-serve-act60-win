using System;
using System.ComponentModel;
using System.Diagnostics;
using System.IO;
using System.Net;
using System.Net.NetworkInformation;
using System.Windows;
using System.Windows.Media;
using A4ServerConsole.Models;
using A4ServerConsole.Services;
using A4ServerConsole.Services.Gateway;
using A4ServerConsole.Windows;
using Microsoft.Win32;

namespace A4ServerConsole
{
    public partial class MainWindow : Window
    {
        private readonly ConsoleConfig _config;
        private readonly GameServerManager _gameServer;
        private readonly GatewayServer _gatewayServer;
        private string GmUrl => _config.IsPublicMode
            ? (PublicIpDetector.IsValidIpv4(_config.PublicIp) ? $"https://{_config.PublicIp}/" : "")
            : $"http://127.0.0.1:{_config.GmPort}/";
        private bool IsGmRunning => _gmProcess is { HasExited: false };
        private bool _initializing = true;
        private bool _startingGm;
        private Process? _gmProcess;

        public MainWindow()
        {
            InitializeComponent();
            _config = ConsoleConfig.Load();

            _gameServer = new GameServerManager(AppendLog);
            _gameServer.StateChanged += pid => Dispatcher.BeginInvoke(new Action(() =>
            {
                if (!_gameServer.IsRunning) StopGm();
                UpdateStatusUi();
            }));
            _gatewayServer = new GatewayServer(AppendLog);

            LoadConfigToUi();
            _initializing = false;
            UpdatePreviewAndButtons();
            AppendLog("[控制台] A4 服务端控制台已就绪（兼容 Win7 SP1 ~ Win11 / Windows Server）");
        }

        private void LoadConfigToUi()
        {
            RbPublicMode.IsChecked = _config.IsPublicMode;
            RbLocalMode.IsChecked = !_config.IsPublicMode;
            TxtPublicIp.Text = _config.PublicIp;

            ChkEnableGateway.IsChecked = _config.EnableGateway;
            ChkEnableGm.IsChecked = _config.EnableGm;
            TxtGmPassword.Text = _config.ReadGmPassword();
        }

        private void SyncUiToConfig()
        {
            _config.IsPublicMode = RbPublicMode.IsChecked == true;
            _config.PublicIp = TxtPublicIp.Text.Trim();
            _config.EnableGateway = ChkEnableGateway.IsChecked == true;
            _config.EnableGm = ChkEnableGm.IsChecked == true;
            // 密码直连调试已从界面移除（登录器一律走一次性令牌），固定关闭，避免玩家拿密码直连绕过网关。
            _config.AllowPasswordFallback = false;
        }

        private bool IsAnyServiceRunning()
            => _gameServer.IsRunning || _gatewayServer.IsRunning || IsGmRunning;

        private bool SaveGmPassword()
        {
            try
            {
                string password = TxtGmPassword.Text;
                if (!string.Equals(_config.ReadGmPassword(), password, StringComparison.Ordinal))
                    _config.SetGmPassword(password);
                return true;
            }
            catch (System.Security.Cryptography.CryptographicException ex)
            {
                MessageBox.Show(this, "无法安全保存 GM 密码：" + ex.Message, "GM 密码", MessageBoxButton.OK, MessageBoxImage.Warning);
                return false;
            }
        }

        private bool ValidateGmSettings()
        {
            if (string.IsNullOrWhiteSpace(TxtGmPassword.Text))
            {
                MessageBox.Show(this, "请先设置网页 GM 密码。", "网页 GM", MessageBoxButton.OK, MessageBoxImage.Warning);
                TxtGmPassword.Focus();
                return false;
            }
            if (string.IsNullOrEmpty(GmUrl))
            {
                MessageBox.Show(this, "请先填写有效的公网 IPv4 地址。", "网页 GM", MessageBoxButton.OK, MessageBoxImage.Warning);
                return false;
            }
            return SaveGmPassword();
        }

        private void ModeRadio_Changed(object sender, RoutedEventArgs e)
        {
            if (_initializing) return;
            SyncUiToConfig();
            UpdatePreviewAndButtons();
            if (_config.IsPublicMode && string.IsNullOrWhiteSpace(_config.PublicIp))
            {
                _ = DetectPublicIpAsync();
            }
        }

        private void AnyConfig_Changed(object sender, RoutedEventArgs e)
        {
            if (_initializing) return;
            SyncUiToConfig();
            UpdatePreviewAndButtons();
        }

        private void UpdatePreviewAndButtons()
        {
            UpdateStatusUi();
        }

        private void UpdateStatusUi()
        {
            bool running = IsAnyServiceRunning();
            BtnStart.IsEnabled = !_gameServer.IsRunning && !_gatewayServer.IsRunning && !_startingGm;
            BtnStop.IsEnabled = running;
            RbLocalMode.IsEnabled = !running;
            RbPublicMode.IsEnabled = !running;
            TxtPublicIp.IsEnabled = _config.IsPublicMode && !running;
            BtnDetectIp.IsEnabled = _config.IsPublicMode && !running;
            BtnSettings.IsEnabled = !running;
            ChkEnableGateway.IsEnabled = !_gameServer.IsRunning && !_gatewayServer.IsRunning;
            ChkEnableGm.IsEnabled = !running;
            TxtGmPassword.IsReadOnly = IsGmRunning || _startingGm;
            BtnOpenGm.ToolTip = IsGmRunning && !_startingGm
                ? "用默认浏览器打开网页 GM"
                : "勾选「同时启动网页 GM 工具」，启动服务器后即可打开";
            TxtGmUrl.Text = GmUrl;
            TxtGmHint.Text = _config.IsPublicMode
                ? $"公网访问需配置 HTTPS 反向代理 → 127.0.0.1:{_config.GmPort}，不要直接转发 GM 端口。"
                : "网页 GM 仅供本机访问。";

            if (running)
            {
                StatusDot.Fill = (Brush)FindResource("AccentGreen");
                TxtStatus.Foreground = (Brush)FindResource("AccentGreen");
                string game = _gameServer.ProcessId.HasValue ? $"游戏服 PID: {_gameServer.ProcessId}" : "游戏服未启动";
                string gateway = _gatewayServer.IsRunning ? $" | 网关:{_config.GatewayPort}" : "";
                string gm = IsGmRunning ? " | 网页 GM 已启动" : "";
                TxtStatus.Text = game + gateway + gm;
            }
            else
            {
                StatusDot.Fill = (Brush)FindResource("TextMuted");
                TxtStatus.Foreground = (Brush)FindResource("TextSecondary");
                TxtStatus.Text = "服务器已停止";
            }
        }

        private async void BtnDetectIp_Click(object sender, RoutedEventArgs e)
        {
            await DetectPublicIpAsync();
        }

        private async System.Threading.Tasks.Task DetectPublicIpAsync()
        {
            string originalIp = TxtPublicIp.Text;
            BtnDetectIp.IsEnabled = false;
            BtnDetectIp.Content = "识别中...";
            try
            {
                var (ip, source) = await PublicIpDetector.DetectAsync();
                if (IsAnyServiceRunning() || !_config.IsPublicMode || TxtPublicIp.Text != originalIp) return;
                TxtPublicIp.Text = ip;
                SyncUiToConfig();
                _config.Save();
                AppendLog($"[网络] 自动识别公网 IP：{ip}（来源：{source}）");
            }
            finally
            {
                BtnDetectIp.Content = "自动识别";
                UpdatePreviewAndButtons();
            }
        }

        private async void BtnStart_Click(object sender, RoutedEventArgs e)
        {
            SyncUiToConfig();

            if (_config.IsPublicMode && !PublicIpDetector.IsValidIpv4(_config.PublicIp))
            {
                MessageBox.Show("公网模式下请输入有效的 IPv4 地址，或点击「自动识别」。",
                    "公网 IP 无效", MessageBoxButton.OK, MessageBoxImage.Warning);
                return;
            }

            if (_config.EnableGm && !IsGmRunning && !ValidateGmSettings()) return;
            _config.Save();

            // 首次启动无备份时自动备份一次
            BackupManager.EnsureInitialBackup(_config, AppendLog);

            // 1. 启动游戏服
            if (!_gameServer.Start(_config, out string gameErr))
            {
                MessageBox.Show(gameErr, "启动游戏服失败", MessageBoxButton.OK, MessageBoxImage.Error);
                UpdateStatusUi();
                return;
            }

            UpdatePreviewAndButtons();
            if (_config.EnableGm) _ = StartGmAsync();

            // 2. 启动 Windows 移植版登录器网关（首次启动时等待游戏服完成 s1a5.db 建库）
            if (_config.EnableGateway)
            {
                if (!File.Exists(_config.DbPath))
                {
                    AppendLog("[网关] 等待游戏服首次初始化数据库 s1a5.db...");
                    for (int i = 0; i < 30 && _gameServer.IsRunning && !File.Exists(_config.DbPath); i++)
                    {
                        await System.Threading.Tasks.Task.Delay(300);
                    }
                }

                if (_gameServer.IsRunning)
                {
                    if (!_gatewayServer.Start(_config, out string gwErr))
                    {
                        AppendLog("[网关] 启动失败：" + gwErr);
                        MessageBox.Show("游戏服已启动，但登录器网关启动失败：\n" + gwErr,
                            "网关启动失败", MessageBoxButton.OK, MessageBoxImage.Warning);
                    }
                }
            }

            UpdatePreviewAndButtons();
        }

        private void BtnStop_Click(object sender, RoutedEventArgs e)
        {
            StopAllServices();
        }

        private void StopAllServices()
        {
            StopGm();
            _gatewayServer.Stop();
            _gameServer.Stop();
            UpdatePreviewAndButtons();
        }

        private void BtnBackupManager_Click(object sender, RoutedEventArgs e)
        {
            SyncUiToConfig();
            _config.Save();
            var win = new BackupWindow(_config, IsAnyServiceRunning, AppendLog) { Owner = this };
            win.ShowDialog();
        }

        private void BtnLauncherBuilder_Click(object sender, RoutedEventArgs e)
        {
            SyncUiToConfig();
            _config.Save();
            var win = new LauncherBuilderWindow(_config, AppendLog) { Owner = this };
            win.ShowDialog();
        }

        private void BtnSettings_Click(object sender, RoutedEventArgs e)
        {
            if (IsAnyServiceRunning()) return;
            SyncUiToConfig();
            var win = new SettingsWindow(_config) { Owner = this };
            if (win.ShowDialog() == true) UpdateStatusUi();
        }

        private void BtnOpenGm_Click(object sender, RoutedEventArgs e)
        {
            if (!IsGmRunning || _startingGm)
            {
                MessageBox.Show(this, _startingGm ? "网页 GM 正在启动，请稍候。" :
                    "网页 GM 未运行。请勾选「同时启动网页 GM 工具」、填写密码，再启动服务器。",
                    "打开 GM 网页", MessageBoxButton.OK, MessageBoxImage.Information);
                return;
            }
            try
            {
                Process.Start(new ProcessStartInfo(GmUrl) { UseShellExecute = true });
            }
            catch (Exception ex)
            {
                MessageBox.Show(this, "无法打开浏览器：" + ex.Message, "打开 GM 网页", MessageBoxButton.OK, MessageBoxImage.Warning);
            }
        }

        private void BtnCopyGmUrl_Click(object sender, RoutedEventArgs e)
        {
            if (string.IsNullOrEmpty(GmUrl)) return;
            try
            {
                Clipboard.SetText(GmUrl);
                AppendLog("[GM] 地址已复制：" + GmUrl);
            }
            catch (System.Runtime.InteropServices.COMException)
            {
                TxtGmUrl.Focus();
                TxtGmUrl.SelectAll();
                AppendLog("[GM] 剪贴板暂不可用，地址已选中，可按 Ctrl+C。");
            }
        }

        private async System.Threading.Tasks.Task StartGmAsync()
        {
            if (_startingGm || IsGmRunning) return;
            _startingGm = true;
            UpdateStatusUi();
            try
            {
                int port = _config.GmPort;
                string url = GmUrl;
                if (port < 1 || port > 65535) throw new InvalidOperationException("请在路径与端口设置中填写有效的网页 GM 端口。");
                foreach (var endpoint in IPGlobalProperties.GetIPGlobalProperties().GetActiveTcpListeners())
                {
                    if (endpoint.Port == port && (IPAddress.IsLoopback(endpoint.Address) ||
                        endpoint.Address.Equals(IPAddress.Any) || endpoint.Address.Equals(IPAddress.IPv6Any)))
                        throw new InvalidOperationException($"端口 {port} 已被占用，未启动网页 GM。");
                }

                string gmExe = FindGmExecutable() ?? throw new FileNotFoundException(
                    "找不到 A4GmTool.exe；请把完整的 A4GmTool 发布文件夹放到控制台旁边。");

                string password = TxtGmPassword.Text;
                if (string.IsNullOrWhiteSpace(password)) throw new InvalidOperationException("请先设置网页 GM 密码。");
                string args = $"--require-password --stop-on-stdin-eof --port {port} --game-port {_config.GamePort}";
                if (_config.IsPublicMode) args += " --secure-cookie";
                if (!string.IsNullOrWhiteSpace(_config.DbPath))
                    args += $" --db \"{Path.GetFullPath(_config.DbPath)}\"";
                if (!string.IsNullOrWhiteSpace(_config.ClientDir))
                {
                    string clientDir = Path.GetFullPath(_config.ClientDir);
                    string pvf = Path.Combine(clientDir, "Script.pvf");
                    string images = Path.Combine(clientDir, "ImagePacks2");
                    if (File.Exists(pvf)) args += $" --pvf \"{pvf}\"";
                    if (Directory.Exists(images)) args += $" --imagepacks \"{images}\"";
                }

                _gmProcess?.Dispose();
                var process = new Process
                {
                    StartInfo = new ProcessStartInfo(gmExe, args)
                    {
                        WorkingDirectory = Path.GetDirectoryName(gmExe),
                        UseShellExecute = false,
                        CreateNoWindow = true,
                        RedirectStandardInput = true,
                        RedirectStandardError = true,
                        StandardErrorEncoding = System.Text.Encoding.UTF8
                    }
                };
                process.StartInfo.EnvironmentVariables["A4_GM_PASSWORD"] = password;
                process.EnableRaisingEvents = true;
                process.Exited += (s, e) => Dispatcher.BeginInvoke(new Action(UpdateStatusUi));
                process.ErrorDataReceived += (s, e) => { if (!string.IsNullOrWhiteSpace(e.Data)) AppendLog("[GM] " + e.Data); };
                try
                {
                    if (!process.Start()) throw new InvalidOperationException("未能创建 GM 进程。");
                }
                catch
                {
                    process.Dispose();
                    throw;
                }
                finally
                {
                    process.StartInfo.EnvironmentVariables.Remove("A4_GM_PASSWORD");
                }
                _gmProcess = process;
                process.BeginErrorReadLine();

                for (int i = 0; i < 40; i++)
                {
                    if (_gmProcess != process) return;
                    if (process.HasExited)
                        throw new InvalidOperationException($"GM 已退出（代码 {process.ExitCode}）。");
                    foreach (var endpoint in IPGlobalProperties.GetIPGlobalProperties().GetActiveTcpListeners())
                    {
                        if (endpoint.Port == port && endpoint.Address.Equals(IPAddress.Loopback))
                        {
                            AppendLog("[GM] 服务已就绪：" + url);
                            return;
                        }
                    }
                    await System.Threading.Tasks.Task.Delay(500);
                }
                AppendLog("[GM] 尚未确认服务就绪，请检查上述日志。");
            }
            catch (Exception ex)
            {
                AppendLog("[GM] 启动失败：" + ex.Message);
            }
            finally
            {
                _startingGm = false;
                UpdateStatusUi();
            }
        }

        private static string? FindGmExecutable()
        {
            string baseDir = AppDomain.CurrentDomain.BaseDirectory;
            string bundled = Path.Combine(baseDir, "A4GmTool", "A4GmTool.exe");
            if (File.Exists(bundled)) return bundled;

            var directory = new DirectoryInfo(baseDir);
            for (int i = 0; i < 5 && directory != null; i++, directory = directory.Parent)
            {
                string devTool = Path.Combine(directory.FullName, "A4GmTool", "dist", "A4GmTool", "A4GmTool.exe");
                if (File.Exists(devTool)) return devTool;
            }
            return null;
        }

        private void StopGm()
        {
            var process = _gmProcess;
            _gmProcess = null;
            if (process == null) return;
            using (process)
            {
                try
                {
                    if (!process.HasExited) process.StandardInput.Close();
                }
                catch (IOException ex)
                {
                    AppendLog("[GM] 关闭后台输入管道失败：" + ex.Message);
                }
            }
        }

        private void BtnClearLog_Click(object sender, RoutedEventArgs e) => TxtLog.Clear();

        private void AppendLog(string line)
        {
            string stamped = $"[{DateTime.Now:HH:mm:ss}] {line}{Environment.NewLine}";
            if (!Dispatcher.CheckAccess())
            {
                Dispatcher.BeginInvoke(new Action(() => AppendLogOnUi(stamped)));
            }
            else
            {
                AppendLogOnUi(stamped);
            }
        }

        private void AppendLogOnUi(string text)
        {
            if (TxtLog.Text.Length > 120_000)
            {
                TxtLog.Text = TxtLog.Text.Substring(TxtLog.Text.Length - 60_000);
            }
            TxtLog.AppendText(text);
            TxtLog.ScrollToEnd();
        }

        private void Window_Closing(object sender, CancelEventArgs e)
        {
            SyncUiToConfig();
            if (!SaveGmPassword()) { e.Cancel = true; return; }
            _config.Save();
            if (IsAnyServiceRunning())
            {
                var confirm = MessageBox.Show("服务器正在运行中，关闭控制台会停止游戏服、登录器网关及由控制台启动的 GM，确定退出吗？",
                    "确认退出", MessageBoxButton.YesNo, MessageBoxImage.Question);
                if (confirm != MessageBoxResult.Yes)
                {
                    e.Cancel = true;
                    return;
                }
                StopAllServices();
            }
            else
            {
                StopGm();
            }
        }
    }
}
