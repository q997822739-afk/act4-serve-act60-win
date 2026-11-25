using System;
using System.Collections.Generic;
using System.IO;

namespace A4GmTool.Core.Img
{
    /// <summary>
    /// 一个 NPK 包的最小读取器（只支持 GM 取图标所需的 IMG v2 及以上）。
    /// 结构：8 字节头 + 偏移表；每段是一个 IMG。帧数据多为 DXT1/DXT3/DXT5 或 ARGB8888。
    ///
    /// 拿不到具体版本就返回 null，由上层显示占位图 —— 图标不是关键功能，不能因为它挂掉。
    /// </summary>
    public sealed class NpkArchive : IDisposable
    {
        private readonly byte[] _data;
        private readonly Dictionary<string, int> _imgOffsets = new Dictionary<string, int>(StringComparer.OrdinalIgnoreCase);

        public NpkArchive(string path)
        {
            _data = File.ReadAllBytes(path);
            Parse();
        }

        private void Parse()
        {
            if (_data.Length < 16) return;

            // NPK：前 8 字节是 "NeoplePack_Bill" 或 0x00000010 头；之后是段数
            int count = BitConverter.ToInt32(_data, 0);
            if (count <= 0 || count > 100000) return;

            int pos = 4;
            for (int i = 0; i < count && pos + 8 <= _data.Length; i++)
            {
                int offset = BitConverter.ToInt32(_data, pos); pos += 4;
                int length = BitConverter.ToInt32(_data, pos); pos += 4;
                if (offset <= 0 || length <= 0 || offset + length > _data.Length) continue;

                try
                {
                    string name = ReadImgName(offset, length);
                    if (!string.IsNullOrEmpty(name) && !_imgOffsets.ContainsKey(name)) _imgOffsets[name] = offset;
                }
                catch
                {
                    // 单段坏了跳过
                }
            }
        }

        private string ReadImgName(int offset, int length)
        {
            if (offset + 16 > _data.Length) return null;
            // IMG v2 头：uint16 版本, uint16 未知, uint32 帧数, uint32 校验, 然后名字
            int p = offset + 12;
            int end = Math.Min(_data.Length, offset + length);
            while (p < end && _data[p] != 0) p++;
            if (p <= offset + 12) return null;
            string name = System.Text.Encoding.ASCII.GetString(_data, offset + 12, p - (offset + 12));
            return name.Length > 0 ? name : null;
        }

        public bool Contains(string imgName)
        {
            return _imgOffsets.ContainsKey(imgName);
        }

        /// <summary>渲染指定 IMG 的某一帧。失败返回 null。</summary>
        public byte[] RenderFrame(string imgName, int frame)
        {
            int offset;
            if (!_imgOffsets.TryGetValue(imgName, out offset)) return null;
            return ImgDecoder.RenderFrame(_data, offset, frame);
        }

        public void Dispose()
        {
        }
    }

    /// <summary>
    /// IMG 帧解码占位实现：DNF 的 IMG 帧可能是 DXT1/3/5、ARGB8888 或调色板索引，
    /// 具体版本还要看头里的 version。这里给出结构化的入口，拿不到可解码的帧就返回 null。
    /// </summary>
    internal static class ImgDecoder
    {
        public static byte[] RenderFrame(byte[] npk, int imgOffset, int frame)
        {
            try
            {
                int version = BitConverter.ToUInt16(npk, imgOffset);
                int w = BitConverter.ToInt32(npk, imgOffset + 16);
                int h = BitConverter.ToInt32(npk, imgOffset + 20);

                // 只有够得着的尺寸才继续，避免把乱七八糟的字节当图像解
                if (w <= 0 || h <= 0 || w > 512 || h > 512) return null;

                int dataOffset = BitConverter.ToInt32(npk, imgOffset + 24);
                if (dataOffset <= 0 || imgOffset + dataOffset >= npk.Length) return null;

                int baseOffset = imgOffset + dataOffset;
                int pix = baseOffset;
                while (pix + 4 <= npk.Length && pix < baseOffset + 65536)
                {
                    int frameW = BitConverter.ToInt32(npk, pix);
                    int frameH = BitConverter.ToInt32(npk, pix + 4);
                    pix += 8;
                    if (frameW <= 0 || frameH <= 0 || frameW > 512 || frameH > 512) return null;

                    for (int y = 0; y < frameH; y++)
                    {
                        byte type = npk[pix]; pix += 1;
                        int len = BitConverter.ToInt32(npk, pix); pix += 4;
                        if (len < 0 || pix + len > npk.Length) return null;
                        pix += len;
                    }

                    if (frame == 0)
                    {
                        // 真的调像素这一步依赖具体 IMG 版本；
                        // 没把握时宁可返回 null 让前端显示占位，也不要显示花屏。
                        return null;
                    }

                    frame--;
                    if (frame < 0) return null;
                }
            }
            catch
            {
                return null;
            }
            return null;
        }
    }
}
