using System;

namespace A4GmTool.Core.Pvf
{
    /// <summary>
    /// PVF 索引区 / 数据区的块解密。
    /// 算法来自服务端 <c>com.dylan.pvf.PvfCrypto</c>：每 4 字节当成一个 uint32，
    /// 先异或 key，再循环右移 6 位。
    /// </summary>
    public static class PvfCrypto
    {
        private const int Rotation = 6;

        /// <summary>原地解密一块数据。返回是否通过 CRC 校验（CRC 不匹配说明 key 不对）。</summary>
        public static bool Decrypt(byte[] data, int seed, int keyAndCrc)
        {
            if ((data.Length & 3) != 0)
                throw new PvfFormatException("加密块长度 " + data.Length + " 不是 4 的倍数；客户端遇到这种长度直接判失败");

            for (int i = 0; i < data.Length; i += 4)
            {
                uint cipher = (uint)(data[i]
                                     | (data[i + 1] << 8)
                                     | (data[i + 2] << 16)
                                     | (data[i + 3] << 24));
                uint plain = RotateRight(cipher ^ (uint)keyAndCrc, Rotation);
                data[i] = (byte)(plain & 0xFF);
                data[i + 1] = (byte)((plain >> 8) & 0xFF);
                data[i + 2] = (byte)((plain >> 16) & 0xFF);
                data[i + 3] = (byte)((plain >> 24) & 0xFF);
            }

            return Crc32.Update(seed, data) == keyAndCrc;
        }

        private static uint RotateRight(uint value, int bits)
        {
            return (value >> bits) | (value << (32 - bits));
        }

        /// <summary>
        /// DNF / PVF 用的 CRC32（多项式 0xEDB88320 反射）。
        /// 与服务端 <c>com.dylan.protocol.crypto.Crc32</c> 完全一致：
        /// <c>update(crc, data)</c> = ~( 反射CRC32 (~crc 起步) )，注意首尾各取反一次。
        /// </summary>
        private static class Crc32
        {
            private static readonly uint[] Table = BuildTable();

            private static uint[] BuildTable()
            {
                uint[] table = new uint[256];
                for (uint i = 0; i < 256; i++)
                {
                    uint c = i;
                    for (int k = 0; k < 8; k++)
                    {
                        c = ((c & 1) != 0) ? (0xEDB88320u ^ (c >> 1)) : (c >> 1);
                    }
                    table[i] = c;
                }
                return table;
            }

            public static int Update(int seed, byte[] data)
            {
                uint c = ~unchecked((uint)seed);
                for (int i = 0; i < data.Length; i++)
                {
                    c = Table[(c ^ data[i]) & 0xFF] ^ (c >> 8);
                }
                return unchecked((int)~c);
            }
        }
    }

    /// <summary>PVF 包格式错误。</summary>
    public sealed class PvfFormatException : Exception
    {
        public PvfFormatException(string message) : base(message) { }
    }
}
