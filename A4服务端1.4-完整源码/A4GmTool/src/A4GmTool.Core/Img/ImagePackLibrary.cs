using System;
using System.Collections.Generic;
using System.IO;
using System.Text;

namespace A4GmTool.Core.Img
{
    /// <summary>
    /// ImagePacks2 里的 NPCK/IMG 图标库。只做 GM 需要的一件事：按 <c>Item/IconArmor.img</c> + 帧号
    /// 找到像素并编码成 PNG。
    ///
    /// 注意：本地部署不一定有 ImagePacks2（客户端可能只放 Script.pvf），
    /// 所以这里所有失败都返回 null，由上层显示占位图，绝不让 GM 因此崩溃。
    /// </summary>
    public sealed class ImagePackLibrary : IDisposable
    {
        private readonly List<string> _dirs;
        private readonly Dictionary<string, NpkArchive> _packs = new Dictionary<string, NpkArchive>(StringComparer.OrdinalIgnoreCase);
        private readonly Dictionary<string, byte[]> _pngCache = new Dictionary<string, byte[]>(StringComparer.Ordinal);
        private readonly object _lock = new object();
        private readonly Action<string> _log;

        public bool Ready { get; private set; }
        public int PackCount { get { return _packs.Count; } }

        private ImagePackLibrary(List<string> dirs, Action<string> log)
        {
            _dirs = dirs;
            _log = log;
        }

        public static ImagePackLibrary Open(List<string> dirs, Action<string> log)
        {
            ImagePackLibrary lib = new ImagePackLibrary(dirs ?? new List<string>(), log);
            foreach (string dir in lib._dirs)
            {
                try
                {
                    foreach (string file in Directory.GetFiles(dir, "*.NPK"))
                    {
                        string key = Path.GetFileNameWithoutExtension(file);
                        try { lib._packs[key] = new NpkArchive(file); }
                        catch (Exception ex) { if (log != null) log("读 NPK 失败 " + Path.GetFileName(file) + "：" + ex.Message); }
                    }
                }
                catch (Exception ex)
                {
                    if (log != null) log("扫目录失败 " + dir + "：" + ex.Message);
                }
            }
            lib.Ready = lib._packs.Count > 0;
            return lib;
        }

        /// <summary>
        /// 渲染某一帧成 PNG。找不到就返回 null。
        /// imgPath 形如 <c>Item/IconArmor.img</c>。
        /// </summary>
        public byte[] TryRenderPng(string imgPath, int frame)
        {
            if (!Ready || string.IsNullOrEmpty(imgPath)) return null;

            string cacheKey = imgPath + "#" + frame;
            lock (_lock)
            {
                byte[] cached;
                if (_pngCache.TryGetValue(cacheKey, out cached)) return cached;
            }

            byte[] png = null;
            try
            {
                string naked = imgPath.Replace('\\', '/');
                int slash = naked.LastIndexOf('/');
                string dir = slash > 0 ? naked.Substring(0, slash) : "";
                string file = slash >= 0 ? naked.Substring(slash + 1) : naked;
                string packName = NpkNameCipher.PackKeyFor(dir, file);

                NpkArchive pack;
                if (packName != null && _packs.TryGetValue(packName, out pack))
                {
                    png = pack.RenderFrame(file, frame);
                }
            }
            catch (Exception ex)
            {
                if (_log != null) _log("解图标失败 " + imgPath + "：" + ex.Message);
                png = null;
            }

            lock (_lock)
            {
                _pngCache[cacheKey] = png;
            }
            return png;
        }

        public void Dispose()
        {
            lock (_lock)
            {
                foreach (NpkArchive p in _packs.Values)
                {
                    try { p.Dispose(); } catch { }
                }
                _packs.Clear();
                _pngCache.Clear();
            }
            Ready = false;
        }
    }

    /// <summary>NPK 文件名规则（ImagePacks2/sprite_...）；本地没有 NPK 时全部返回 null。</summary>
    internal static class NpkNameCipher
    {
        /// <summary>把 <c>Item</c> + <c>IconArmor.img</c> 映射成 NPK 包名，如 <c>sprite_item</c>。</summary>
        public static string PackKeyFor(string dir, string file)
        {
            if (string.IsNullOrEmpty(file)) return null;

            // 常见：Item/IconArmor.img → sprite_item；Character/... → sprite_character_...
            string top = string.IsNullOrEmpty(dir) ? "" : dir.Replace('\\', '/').Split('/')[0].ToLowerInvariant();
            if (top.Length == 0) return null;

            string first = top;
            if (first.StartsWith("item")) return "sprite_item";
            return "sprite_" + first;
        }
    }
}
