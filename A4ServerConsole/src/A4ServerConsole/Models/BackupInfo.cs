using System;

namespace A4ServerConsole.Models
{
    public sealed class BackupInfo
    {
        public string Name { get; set; } = "";
        public string FullPath { get; set; } = "";
        public DateTime CreatedAt { get; set; }
        public long SizeBytes { get; set; }

        public string CreatedAtText => CreatedAt.ToString("yyyy-MM-dd HH:mm:ss");

        public string SizeText
        {
            get
            {
                if (SizeBytes >= 1024 * 1024)
                    return $"{SizeBytes / (1024.0 * 1024.0):F2} MB";
                if (SizeBytes >= 1024)
                    return $"{SizeBytes / 1024.0:F1} KB";
                return $"{SizeBytes} B";
            }
        }
    }
}
