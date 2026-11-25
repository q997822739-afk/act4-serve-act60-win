using System.IO;
using System.Text;

namespace A4Launcher.Services;

/// <summary>
/// 出错日志：%LOCALAPPDATA%\A4Launcher\launcher.log。
/// 玩家报「打不开」时让客服要这个文件，比问半天管用。
/// 只保留最近的记录，超过 1 MB 就滚动成 launcher.log.1。
/// </summary>
public static class Log
{
    private const long MaxBytes = 1024 * 1024;
    private static readonly object Gate = new();

    public static void Write(string message)
    {
        try
        {
            var path = AppPaths.LauncherLog;
            lock (Gate)
            {
                var info = new FileInfo(path);
                if (info.Exists && info.Length > MaxBytes)
                {
                    var backup = path + ".1";
                    if (File.Exists(backup)) File.Delete(backup);
                    File.Move(path, backup);
                }
                File.AppendAllText(path,
                    DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss") + "  " + message + Environment.NewLine,
                    Encoding.UTF8);
            }
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException)
        {
            // 写日志本身失败就算了，不能因为日志再崩一次
        }
    }

    public static void Write(string message, Exception error)
        => Write(message + "：" + error.GetType().Name + ": " + error.Message + Environment.NewLine + error.StackTrace);
}
