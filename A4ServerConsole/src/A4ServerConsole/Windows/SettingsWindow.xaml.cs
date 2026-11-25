using System;
using System.IO;
using System.Linq;
using System.Windows;
using A4ServerConsole.Models;
using Microsoft.Win32;

namespace A4ServerConsole.Windows
{
    public partial class SettingsWindow : Window
    {
        private readonly ConsoleConfig _config;

        public SettingsWindow(ConsoleConfig config)
        {
            InitializeComponent();
            _config = config;
            TxtServerJar.Text = config.ServerJarPath;
            TxtClientDir.Text = config.ClientDir;
            TxtDbPath.Text = config.DbPath;
            TxtJavaExe.Text = config.JavaExePath;
            TxtEntrancePort.Text = config.EntrancePort.ToString();
            TxtGamePort.Text = config.GamePort.ToString();
            TxtGatewayPort.Text = config.GatewayPort.ToString();
            TxtGmPort.Text = config.GmPort.ToString();
            ChkTokenMode.IsChecked = config.UseTokenLogin;
        }

        private void BrowseJar_Click(object sender, RoutedEventArgs e) =>
            BrowseFile(TxtServerJar, "JAR 文件 (*.jar)|*.jar|所有文件 (*.*)|*.*");

        private void BrowseDb_Click(object sender, RoutedEventArgs e) =>
            BrowseFile(TxtDbPath, "SQLite 数据库 (*.db)|*.db|所有文件 (*.*)|*.*");

        private void BrowseJava_Click(object sender, RoutedEventArgs e) =>
            BrowseFile(TxtJavaExe, "java.exe|java.exe|所有可执行文件 (*.exe)|*.exe");

        private void BrowseFile(System.Windows.Controls.TextBox target, string filter)
        {
            var dialog = new OpenFileDialog { Filter = filter };
            if (File.Exists(target.Text)) dialog.FileName = target.Text;
            if (dialog.ShowDialog(this) == true) target.Text = dialog.FileName;
        }

        private void BrowseClient_Click(object sender, RoutedEventArgs e)
        {
            using var dialog = new System.Windows.Forms.FolderBrowserDialog
            {
                Description = "选择包含 Script.pvf 的 ACT4/ACT5 客户端目录",
                SelectedPath = TxtClientDir.Text
            };
            if (dialog.ShowDialog() == System.Windows.Forms.DialogResult.OK)
                TxtClientDir.Text = dialog.SelectedPath;
        }

        private void Save_Click(object sender, RoutedEventArgs e)
        {
            try
            {
                int entrance = ParsePort(TxtEntrancePort.Text, "入口");
                int game = ParsePort(TxtGamePort.Text, "游戏");
                int gateway = ParsePort(TxtGatewayPort.Text, "网关");
                int gm = ParsePort(TxtGmPort.Text, "网页 GM");
                if (new[] { entrance, game, gateway, gm }.Distinct().Count() != 4)
                    throw new ArgumentException("入口、游戏、网关和网页 GM 端口不能重复。");

                string jar = FullPath(TxtServerJar.Text, "服务端 JAR");
                string client = FullPath(TxtClientDir.Text, "客户端目录");
                string db = FullPath(TxtDbPath.Text, "游戏数据库");
                string javaInput = TxtJavaExe.Text.Trim();
                string java = javaInput.Length == 0 || javaInput == "java" ? "java" : Path.GetFullPath(javaInput);
                string root = _config.ServerRootDir;
                if (!string.Equals(jar, _config.ServerJarPath, StringComparison.OrdinalIgnoreCase))
                {
                    // 保留之前浏览 JAR 时更新工作目录的行为。
                    string? parent = Directory.GetParent(Path.GetDirectoryName(jar) ?? jar)?.FullName;
                    if (parent != null && Directory.Exists(parent)) root = parent;
                }

                _config.ServerJarPath = jar;
                _config.ServerRootDir = root;
                _config.ClientDir = client;
                _config.DbPath = db;
                _config.JavaExePath = java;
                _config.EntrancePort = entrance;
                _config.GamePort = game;
                _config.GatewayPort = gateway;
                _config.GmPort = gm;
                _config.UseTokenLogin = ChkTokenMode.IsChecked == true;
                _config.AllowPasswordFallback = false;
                _config.Save();
                DialogResult = true;
            }
            catch (Exception ex)
            {
                MessageBox.Show(this, ex.Message, "无法保存设置", MessageBoxButton.OK, MessageBoxImage.Warning);
            }
        }

        private static string FullPath(string value, string label)
        {
            if (string.IsNullOrWhiteSpace(value)) throw new ArgumentException(label + "不能为空。");
            return Path.GetFullPath(value.Trim());
        }

        private static int ParsePort(string value, string label)
        {
            if (int.TryParse(value, out int port) && port >= 1 && port <= 65535) return port;
            throw new ArgumentException(label + "端口必须为 1 至 65535 的整数。");
        }
    }
}
