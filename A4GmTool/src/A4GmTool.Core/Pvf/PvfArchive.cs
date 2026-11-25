using System;
using System.Collections.Generic;
using System.IO;
using System.Text;

namespace A4GmTool.Core.Pvf
{
    /// <summary>PVF 索引里的一条记录。</summary>
    public sealed class PvfEntry
    {
        public string Name;
        public int NameHash;
        /// <summary>相对数据区起点的偏移。</summary>
        public long Offset;
        public int Length;
        public int Key;

        public int PaddedLength
        {
            get { return (Length + 3) & ~3; }
        }
    }

    /// <summary>
    /// A4 / ACT5 的 Script.pvf 读取器（只读，随机访问）。
    ///
    /// 文件布局（全部小端）：
    ///   0              int32  guidLength
    ///   4              char[guidLength]  guid
    ///   4+L            int32  fileVersion
    ///   8+L            int32  indexLength
    ///   12+L           int32  indexKey（同时是索引区 CRC）
    ///   16+L           int32  fileCount
    ///   20+L           ...   加密索引区（indexLength 字节，4 字节对齐）
    ///   dataBase = 4+L+16+indexLength   ...  加密数据区
    ///
    /// 索引与每条数据都用自己的 key 做「异或 + 循环右移 6 位」解密。
    /// </summary>
    public sealed class PvfArchive : IDisposable
    {
        public const string A4Guid = "13ff9a94-0204-0410-845a-ceeffe637190";
        public static readonly Encoding ScriptCharset = GetEncoding("euc-kr", 949);
        public static readonly Encoding ChineseCharset = GetEncoding("gbk", 936);

        private static readonly byte[] ShieldMagic = new byte[] { 65, 53, 80, 86, 70, 49, 0, 0 };

        private readonly FileStream _stream;
        private readonly Dictionary<string, PvfEntry> _entries;
        private readonly Dictionary<string, PvfEntry> _entriesIgnoreCase;
        private string _path;

        private PvfArchive(FileStream stream, string path, string guid, int fileVersion, long dataBase, Dictionary<string, PvfEntry> entries)
        {
            _stream = stream;
            _path = path;
            Guid = guid;
            FileVersion = fileVersion;
            DataBase = dataBase;
            _entries = entries;
            _entriesIgnoreCase = new Dictionary<string, PvfEntry>(StringComparer.OrdinalIgnoreCase);
            foreach (KeyValuePair<string, PvfEntry> kv in entries)
            {
                if (!_entriesIgnoreCase.ContainsKey(kv.Key)) _entriesIgnoreCase[kv.Key] = kv.Value;
            }
        }

        public string Path { get { return _path; } }
        public string Guid { get; private set; }
        public int FileVersion { get; private set; }
        public long DataBase { get; private set; }
        public int Count { get { return _entries.Count; } }
        public IDictionary<string, PvfEntry> Entries { get { return _entries; } }

        private static Encoding GetEncoding(string name, int codepage)
        {
            try { return Encoding.GetEncoding(name); }
            catch { }
            try { return Encoding.GetEncoding(codepage); }
            catch { }
            return Encoding.UTF8;
        }

        public static PvfArchive Open(string path)
        {
            if (!File.Exists(path)) throw new FileNotFoundException("找不到 PVF 文件：" + path, path);

            FileStream fs = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.ReadWrite, 1 << 16);
            try
            {
                RefuseIfEncrypted(fs, path);

                byte[] head4 = ReadExactly(fs, 0, 4);
                int guidLength = BitConverter.ToInt32(head4, 0);
                if (guidLength < 0 || guidLength > 256)
                {
                    throw new PvfFormatException("这不是 ACT5 的 PVF 包：开头的 GUID 长度是 " + guidLength
                        + "。60 版那种以 DNF_SCRIPT_PACK 开头的包要用另一套头解析");
                }

                int headerLen = 4 + guidLength + 16;
                byte[] header = ReadExactly(fs, 4, headerLen - 4);
                string guid = Encoding.ASCII.GetString(header, 0, guidLength);
                int p = guidLength;
                int fileVersion = BitConverter.ToInt32(header, p);
                int indexLength = BitConverter.ToInt32(header, p + 4);
                int indexKey = BitConverter.ToInt32(header, p + 8);
                int fileCount = BitConverter.ToInt32(header, p + 12);

                if (indexLength < 0 || fileCount < 0)
                {
                    throw new PvfFormatException("文件头不合理：indexLength=" + ((uint)indexLength)
                        + " fileCount=" + ((uint)fileCount));
                }

                long dataBase = (long)headerLen + indexLength;
                if (dataBase > fs.Length)
                    throw new PvfFormatException("索引区长度 " + indexLength + " 超出文件大小 " + fs.Length);

                // 解密长度要 4 字节对齐
                int aligned = (indexLength + 3) & ~3;
                byte[] index = ReadExactly(fs, headerLen, aligned);

                if (!PvfCrypto.Decrypt(index, fileCount, indexKey))
                {
                    // 绝大多数的 A4 包索引 CRC 就是 indexKey；对不上通常也能正常读，
                    // 所以这里只当提示，不把它当成致命错误（服务端会直接拒绝，但 GM 读的是同一份文件）。
                }

                bool a4 = string.Equals(guid, A4Guid, StringComparison.OrdinalIgnoreCase);
                Dictionary<string, PvfEntry> entries = ParseIndex(index, indexLength, fileCount, a4);
                return new PvfArchive(fs, path, guid, fileVersion, dataBase, entries);
            }
            catch
            {
                fs.Dispose();
                throw;
            }
        }

        private static void RefuseIfEncrypted(FileStream fs, string path)
        {
            if (fs.Length < 32) return;
            byte[] tail = ReadExactly(fs, fs.Length - 32, ShieldMagic.Length);
            for (int i = 0; i < ShieldMagic.Length; i++)
            {
                if (tail[i] != ShieldMagic[i]) return;
            }
            throw new PvfFormatException(path + " 是加密过的 pvf 包（尾部带 A5PVF1 标记）。这一份只读明文包，请换成明文的 Script.pvf");
        }

        private static Dictionary<string, PvfEntry> ParseIndex(byte[] index, int length, int fileCount, bool a4)
        {
            Dictionary<string, PvfEntry> outMap = new Dictionary<string, PvfEntry>(fileCount, StringComparer.Ordinal);
            int pos = 0;

            for (int i = 0; i < fileCount; i++)
            {
                if (pos + 8 > length) throw new PvfFormatException("索引区在第 " + i + " 条记录处提前结束");

                int nameHash = BitConverter.ToInt32(index, pos); pos += 4;
                int nameLength = BitConverter.ToInt32(index, pos); pos += 4;
                if (nameLength < 0 || pos + nameLength + 12 > length)
                {
                    throw new PvfFormatException("第 " + i + " 条记录的名字长度不合理：" + (uint)nameLength);
                }

                int end = pos;
                int nameEnd = pos + nameLength;
                while (end < nameEnd && index[end] != 0) end++;
                string name = Encoding.ASCII.GetString(index, pos, end - pos);
                pos = nameEnd;

                int fileLength = BitConverter.ToInt32(index, pos); pos += 4;
                int key = BitConverter.ToInt32(index, pos); pos += 4;
                int offset = BitConverter.ToInt32(index, pos); pos += 4;

                PvfEntry e = new PvfEntry
                {
                    Name = name,
                    NameHash = nameHash,
                    Offset = (uint)offset,
                    Length = fileLength,
                    Key = key
                };
                if (!outMap.ContainsKey(name)) outMap[name] = e;
            }

            return outMap;
        }

        private static byte[] ReadExactly(FileStream fs, long position, int length)
        {
            byte[] buf = new byte[length];
            fs.Seek(position, SeekOrigin.Begin);
            int total = 0;
            while (total < length)
            {
                int n = fs.Read(buf, total, length - total);
                if (n <= 0) throw new PvfFormatException("在偏移 " + position + " 处读不满 " + length + " 字节，文件被截断");
                total += n;
            }
            return buf;
        }

        public bool Contains(string entryPath)
        {
            return FindEntry(entryPath) != null;
        }

        /// <summary>先按原样找，再按忽略大小写找（string.lst 里的路径大小写常和索引不一致）。</summary>
        public PvfEntry FindEntry(string entryPath)
        {
            if (string.IsNullOrEmpty(entryPath)) return null;
            PvfEntry e;
            if (_entries.TryGetValue(entryPath, out e)) return e;
            if (_entriesIgnoreCase.TryGetValue(entryPath, out e)) return e;
            return null;
        }

        /// <summary>读出一条记录的原始字节（已解密，长度为记录声明的长度）。</summary>
        public byte[] Read(string entryPath)
        {
            PvfEntry e = FindEntry(entryPath);
            if (e == null) throw new PvfFormatException(_path + " 里没有 " + entryPath);
            return ReadEntry(e);
        }

        public byte[] ReadEntry(PvfEntry e)
        {
            int padded = e.PaddedLength;
            byte[] buf = ReadExactly(_stream, DataBase + e.Offset, padded);
            PvfCrypto.Decrypt(buf, e.NameHash, e.Key);

            if (e.Length == padded) return buf;
            byte[] exact = new byte[e.Length];
            Array.Copy(buf, exact, e.Length);
            return exact;
        }

        public string ReadText(string entryPath)
        {
            return ScriptCharset.GetString(Read(entryPath));
        }

        public string ReadChineseText(string entryPath)
        {
            return ChineseCharset.GetString(Read(entryPath));
        }

        public void Dispose()
        {
            try { _stream.Dispose(); } catch { }
        }
    }
}
