using System;
using System.Diagnostics;
using System.IO;
using System.Windows;
using A4ServerConsole.Models;
using A4ServerConsole.Services;

namespace A4ServerConsole.Windows
{
    public partial class BackupWindow : Window
    {
        private readonly ConsoleConfig _config;
        private readonly Func<bool> _isAnyServiceRunning;
        private readonly Action<string> _log;

        public BackupWindow(ConsoleConfig config, Func<bool> isAnyServiceRunning, Action<string> log)
        {
            InitializeComponent();
            _config = config;
            _isAnyServiceRunning = isAnyServiceRunning;
            _log = log;
            TxtDbPath.Text = $"数据库路径：{_config.DbPath}   |   备份目录：{_config.BackupDir}";
            RefreshList();
        }

        private void RefreshList()
        {
            ListBackups.ItemsSource = BackupManager.ListBackups(_config.BackupDir);
        }

        private void BtnBackupNow_Click(object sender, RoutedEventArgs e)
        {
            if (_isAnyServiceRunning())
            {
                var warn = MessageBox.Show(
                    "检测到服务正在运行中，建议先停止服务器再备份以确保 WAL 数据完全落盘。\n仍要强制热备份吗？",
                    "备份提示", MessageBoxButton.YesNo, MessageBoxImage.Warning);
                if (warn != MessageBoxResult.Yes) return;
            }

            if (BackupManager.CreateBackup(_config, "manual", out string zipPath, out string err))
            {
                _log("[备份] 手动备份成功：" + Path.GetFileName(zipPath));
                RefreshList();
                MessageBox.Show("备份完成：\n" + zipPath, "成功", MessageBoxButton.OK, MessageBoxImage.Information);
            }
            else
            {
                MessageBox.Show("备份失败：" + err, "错误", MessageBoxButton.OK, MessageBoxImage.Error);
            }
        }

        private void BtnRestore_Click(object sender, RoutedEventArgs e)
        {
            if (!(ListBackups.SelectedItem is BackupInfo selected))
            {
                MessageBox.Show("请先在列表中选择一份备份。", "提示", MessageBoxButton.OK, MessageBoxImage.Information);
                return;
            }

            if (_isAnyServiceRunning())
            {
                MessageBox.Show("请先点击主界面的「停止」按钮关闭游戏服和网关，再执行存档恢复！",
                    "禁止热恢复", MessageBoxButton.OK, MessageBoxImage.Warning);
                return;
            }

            var confirm = MessageBox.Show(
                $"确定要将当前存档恢复为：\n{selected.Name}\n\n（恢复前会自动备份当前数据库以防误操作）",
                "确认恢复存档", MessageBoxButton.YesNo, MessageBoxImage.Question);
            if (confirm != MessageBoxResult.Yes) return;

            if (BackupManager.RestoreBackup(_config, selected.FullPath, out string err))
            {
                _log("[备份] 已恢复存档：" + selected.Name);
                RefreshList();
                MessageBox.Show("存档恢复成功！现在可以重新启动服务器。", "完成", MessageBoxButton.OK, MessageBoxImage.Information);
            }
            else
            {
                MessageBox.Show("恢复失败：" + err, "错误", MessageBoxButton.OK, MessageBoxImage.Error);
            }
        }

        private void BtnDelete_Click(object sender, RoutedEventArgs e)
        {
            if (!(ListBackups.SelectedItem is BackupInfo selected)) return;
            var confirm = MessageBox.Show($"确定删除备份文件 {selected.Name} 吗？", "确认删除",
                MessageBoxButton.YesNo, MessageBoxImage.Warning);
            if (confirm != MessageBoxResult.Yes) return;

            try
            {
                File.Delete(selected.FullPath);
                _log("[备份] 已删除备份：" + selected.Name);
                RefreshList();
            }
            catch (Exception ex)
            {
                MessageBox.Show("删除失败：" + ex.Message, "错误", MessageBoxButton.OK, MessageBoxImage.Error);
            }
        }

        private void BtnOpenDir_Click(object sender, RoutedEventArgs e)
        {
            Directory.CreateDirectory(_config.BackupDir);
            Process.Start("explorer.exe", _config.BackupDir);
        }

        private void BtnRefresh_Click(object sender, RoutedEventArgs e) => RefreshList();
    }
}
