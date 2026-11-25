using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Linq;
using A4ServerConsole.Models;

namespace A4ServerConsole.Services
{
    /// <summary>
    /// 安全备份与恢复 data\s1a5.db（含 WAL 文件 -wal / -shm 以及可选 gateway.db）。
    /// </summary>
    public static class BackupManager
    {
        public static List<BackupInfo> ListBackups(string backupDir)
        {
            var list = new List<BackupInfo>();
            if (string.IsNullOrWhiteSpace(backupDir) || !Directory.Exists(backupDir))
                return list;

            foreach (var file in Directory.GetFiles(backupDir, "*.zip"))
            {
                var fi = new FileInfo(file);
                list.Add(new BackupInfo
                {
                    Name = fi.Name,
                    FullPath = fi.FullName,
                    CreatedAt = fi.LastWriteTime,
                    SizeBytes = fi.Length
                });
            }
            return list.OrderByDescending(b => b.CreatedAt).ToList();
        }

        public static void EnsureInitialBackup(ConsoleConfig cfg, Action<string> log)
        {
            if (string.IsNullOrWhiteSpace(cfg.DbPath) || !File.Exists(cfg.DbPath)) return;
            var existing = ListBackups(cfg.BackupDir);
            if (existing.Count > 0) return;

            if (CreateBackup(cfg, "auto_init", out string createdPath, out string err))
            {
                log("[备份] 检测到尚无存档备份，已自动创建初始备份：" + Path.GetFileName(createdPath));
            }
            else
            {
                log("[备份] 自动创建初始备份失败：" + err);
            }
        }

        public static bool CreateBackup(ConsoleConfig cfg, string tag, out string zipPath, out string error)
        {
            zipPath = "";
            error = "";
            if (string.IsNullOrWhiteSpace(cfg.DbPath) || !File.Exists(cfg.DbPath))
            {
                error = "找不到游戏数据库文件：" + cfg.DbPath;
                return false;
            }

            try
            {
                Directory.CreateDirectory(cfg.BackupDir);
                string stamp = DateTime.Now.ToString("yyyyMMdd_HHmmss");
                string safeTag = string.IsNullOrWhiteSpace(tag) ? "manual" : tag.Trim();
                zipPath = Path.Combine(cfg.BackupDir, $"s1a5_backup_{stamp}_{safeTag}.zip");

                using (var archive = ZipFile.Open(zipPath, ZipArchiveMode.Create))
                {
                    AddIfExists(archive, cfg.DbPath, "s1a5.db");
                    AddIfExists(archive, cfg.DbPath + "-wal", "s1a5.db-wal");
                    AddIfExists(archive, cfg.DbPath + "-shm", "s1a5.db-shm");
                    if (!string.IsNullOrWhiteSpace(cfg.GatewayDbPath))
                    {
                        AddIfExists(archive, cfg.GatewayDbPath, "gateway.db");
                    }
                }
                return true;
            }
            catch (Exception ex)
            {
                error = ex.Message;
                return false;
            }
        }

        public static bool RestoreBackup(ConsoleConfig cfg, string zipPath, out string error)
        {
            error = "";
            if (!File.Exists(zipPath))
            {
                error = "备份文件不存在：" + zipPath;
                return false;
            }

            try
            {
                // 恢复前先留一份防误操作快照
                if (File.Exists(cfg.DbPath))
                {
                    CreateBackup(cfg, "before_restore", out _, out _);
                }

                string? dbDir = Path.GetDirectoryName(cfg.DbPath);
                if (!string.IsNullOrEmpty(dbDir) && !Directory.Exists(dbDir))
                {
                    Directory.CreateDirectory(dbDir);
                }

                // 清理旧 WAL 残留，避免旧 WAL 覆写刚恢复的主库
                TryDelete(cfg.DbPath + "-wal");
                TryDelete(cfg.DbPath + "-shm");

                using (var archive = ZipFile.OpenRead(zipPath))
                {
                    var mainEntry = archive.GetEntry("s1a5.db");
                    if (mainEntry == null)
                    {
                        error = "该备份包中缺少 s1a5.db";
                        return false;
                    }
                    mainEntry.ExtractToFile(cfg.DbPath, overwrite: true);

                    archive.GetEntry("s1a5.db-wal")?.ExtractToFile(cfg.DbPath + "-wal", overwrite: true);
                    archive.GetEntry("s1a5.db-shm")?.ExtractToFile(cfg.DbPath + "-shm", overwrite: true);

                    if (!string.IsNullOrWhiteSpace(cfg.GatewayDbPath))
                    {
                        var gwEntry = archive.GetEntry("gateway.db");
                        if (gwEntry != null)
                        {
                            string? gwDir = Path.GetDirectoryName(cfg.GatewayDbPath);
                            if (!string.IsNullOrEmpty(gwDir) && !Directory.Exists(gwDir))
                                Directory.CreateDirectory(gwDir);
                            TryDelete(cfg.GatewayDbPath + "-wal");
                            TryDelete(cfg.GatewayDbPath + "-shm");
                            gwEntry.ExtractToFile(cfg.GatewayDbPath, overwrite: true);
                        }
                    }
                }
                return true;
            }
            catch (Exception ex)
            {
                error = ex.Message;
                return false;
            }
        }

        private static void AddIfExists(ZipArchive archive, string filePath, string entryName)
        {
            if (!File.Exists(filePath)) return;
            var entry = archive.CreateEntry(entryName, CompressionLevel.Optimal);
            using var entryStream = entry.Open();
            using var fs = new FileStream(filePath, FileMode.Open, FileAccess.Read, FileShare.ReadWrite);
            fs.CopyTo(entryStream);
        }

        private static void TryDelete(string path)
        {
            try
            {
                if (File.Exists(path)) File.Delete(path);
            }
            catch
            {
            }
        }
    }
}
