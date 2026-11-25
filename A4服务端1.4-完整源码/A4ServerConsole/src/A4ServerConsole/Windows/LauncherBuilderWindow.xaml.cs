using System;
using System.Diagnostics;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using A4ServerConsole.Models;
using A4ServerConsole.Services;

namespace A4ServerConsole.Windows
{
    public partial class LauncherBuilderWindow : Window
    {
        private readonly ConsoleConfig _config;
        private readonly Action<string> _log;
        private string OutputDir => LauncherBuilder.ResolveOutputDirectory(TxtOutputDir.Text);

        public LauncherBuilderWindow(ConsoleConfig config, Action<string> log)
        {
            InitializeComponent();
            _config = config;
            _log = log;

            TxtModeBanner.Text = $"当前模式：{(_config.IsPublicMode ? "公网模式" : "本机模式")} → 网关 {_config.EffectiveHost}:{_config.GatewayPort}  |  游戏入口 {_config.EffectiveHost}:{_config.EntrancePort}";
            TxtOutputDir.Text = LauncherBuilder.GetPreferredClientDirectory(_config);

            TxtRegion.Text = _config.LauncherRegion;
            TxtServerName.Text = _config.LauncherServerName;
            TxtTelecom.Text = _config.LauncherTelecomRegions;
            TxtNetcom.Text = _config.LauncherNetcomRegions;
            TxtServerList.Text = _config.LauncherServerList;
            TxtWebsite.Text = _config.LinkWebsite;
            TxtService.Text = _config.LinkService;
            CmbState.SelectedIndex = _config.LauncherServerState switch
            {
                "繁忙" => 1,
                "拥挤" => 2,
                "维护" => 3,
                _ => 0
            };
        }

        private void SyncToConfig()
        {
            _config.LauncherRegion = TxtRegion.Text.Trim();
            _config.LauncherServerName = TxtServerName.Text.Trim();
            _config.LauncherTelecomRegions = TxtTelecom.Text.Trim();
            _config.LauncherNetcomRegions = TxtNetcom.Text.Trim();
            _config.LauncherServerList = TxtServerList.Text.Trim();
            _config.LinkWebsite = TxtWebsite.Text.Trim();
            _config.LinkService = TxtService.Text.Trim();
            _config.LauncherServerState = (CmbState.SelectedItem as ComboBoxItem)?.Content?.ToString() ?? "空闲";
        }

        private void BtnBrowseOut_Click(object sender, RoutedEventArgs e)
        {
            using var dialog = new System.Windows.Forms.FolderBrowserDialog
            {
                Description = "选择配套登录器的生成位置",
                SelectedPath = Directory.Exists(TxtOutputDir.Text.Trim())
                    ? TxtOutputDir.Text.Trim() : AppDomain.CurrentDomain.BaseDirectory
            };
            if (dialog.ShowDialog() == System.Windows.Forms.DialogResult.OK)
                TxtOutputDir.Text = dialog.SelectedPath;
        }

        private void BtnBuild_Click(object sender, RoutedEventArgs e)
        {
            try
            {
                string outputDir = OutputDir;
                var existingFiles = LauncherBuilder.GetExistingOutputFiles(outputDir);
                bool overwriteConfirmed = existingFiles.Length > 0;
                if (overwriteConfirmed && MessageBox.Show(this,
                    "以下文件将被覆盖。现有文件会先保存到生成位置内的 LauncherBackups 文件夹：\n\n" +
                    string.Join("\n", existingFiles) + "\n\n确定继续吗？",
                    "确认覆盖登录器文件", MessageBoxButton.YesNo, MessageBoxImage.Warning, MessageBoxResult.No) != MessageBoxResult.Yes)
                    return;

                SyncToConfig();
                _config.Save();
                var res = LauncherBuilder.Build(_config, outputDir, overwriteConfirmed);
                _log("[登录器] " + res.Message.Replace("\r\n", " | ").Replace("\n", " | "));
                MessageBox.Show(this, res.Message, res.Success ? "生产完成" : "生产失败",
                    MessageBoxButton.OK, res.Success ? MessageBoxImage.Information : MessageBoxImage.Error);
            }
            catch (Exception ex)
            {
                MessageBox.Show(this, ex.Message, "生产失败", MessageBoxButton.OK, MessageBoxImage.Error);
            }
        }

        private void BtnOpenOut_Click(object sender, RoutedEventArgs e)
        {
            try
            {
                string outputDir = OutputDir;
                if (!Directory.Exists(outputDir))
                {
                    MessageBox.Show(this, "生成位置还不存在，请先制作配套登录器。", "打开生成位置",
                        MessageBoxButton.OK, MessageBoxImage.Information);
                    return;
                }
                Process.Start(new ProcessStartInfo { FileName = outputDir, UseShellExecute = true });
            }
            catch (Exception ex)
            {
                MessageBox.Show(this, ex.Message, "无法打开输出目录", MessageBoxButton.OK, MessageBoxImage.Error);
            }
        }

        private void BtnClose_Click(object sender, RoutedEventArgs e)
        {
            try
            {
                SyncToConfig();
                _config.Save();
                Close();
            }
            catch (Exception ex)
            {
                MessageBox.Show(this, ex.Message, "无法保存设置", MessageBoxButton.OK, MessageBoxImage.Error);
            }
        }
    }
}
