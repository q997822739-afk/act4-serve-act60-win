using System;
using System.IO;

namespace A4GmTool.Core.Utils
{
    /// <summary>小端二进制读取器（读 PVF / 存档 blob 用）。</summary>
    public sealed class ByteReader
    {
        private readonly byte[] _data;
        private int _pos;

        public ByteReader(byte[] data)
        {
            _data = data ?? new byte[0];
            _pos = 0;
        }

        public ByteReader(byte[] data, int offset)
        {
            _data = data ?? new byte[0];
            _pos = offset;
        }

        public byte[] Buffer { get { return _data; } }
        public int Position { get { return _pos; } set { _pos = value; } }
        public int Length { get { return _data.Length; } }
        public int Remaining { get { return _data.Length - _pos; } }

        public void Ensure(int count)
        {
            if (count < 0 || _pos + count > _data.Length)
            {
                throw new EndOfStreamException("数据不够了：想要 " + count + " 字节，位置 " + _pos + "，总长 " + _data.Length);
            }
        }

        public byte ReadByte()
        {
            Ensure(1);
            return _data[_pos++];
        }

        public sbyte ReadSByte()
        {
            return unchecked((sbyte)ReadByte());
        }

        public short ReadInt16()
        {
            Ensure(2);
            short v = (short)(_data[_pos] | (_data[_pos + 1] << 8));
            _pos += 2;
            return v;
        }

        public ushort ReadUInt16()
        {
            Ensure(2);
            ushort v = (ushort)(_data[_pos] | (_data[_pos + 1] << 8));
            _pos += 2;
            return v;
        }

        public int ReadInt32()
        {
            Ensure(4);
            int v = _data[_pos]
                    | (_data[_pos + 1] << 8)
                    | (_data[_pos + 2] << 16)
                    | (_data[_pos + 3] << 24);
            _pos += 4;
            return v;
        }

        public uint ReadUInt32()
        {
            return unchecked((uint)ReadInt32());
        }

        public long ReadInt64()
        {
            Ensure(8);
            long lo = (uint)(_data[_pos] | (_data[_pos + 1] << 8) | (_data[_pos + 2] << 16) | (_data[_pos + 3] << 24));
            long hi = (uint)(_data[_pos + 4] | (_data[_pos + 5] << 8) | (_data[_pos + 6] << 16) | (_data[_pos + 7] << 24));
            _pos += 8;
            return lo | (hi << 32);
        }

        public byte[] ReadBytes(int count)
        {
            if (count <= 0) return new byte[0];
            Ensure(count);
            byte[] buf = new byte[count];
            Array.Copy(_data, _pos, buf, 0, count);
            _pos += count;
            return buf;
        }

        public void Skip(int count)
        {
            Ensure(count);
            _pos += count;
        }

        /// <summary>读以 \0 结尾的 ASCII/单字节字符串。</summary>
        public string ReadCString(int maxLength)
        {
            int start = _pos;
            int end = start;
            int limit = Math.Min(_data.Length, start + maxLength);
            while (end < limit && _data[end] != 0) end++;
            string s = System.Text.Encoding.ASCII.GetString(_data, start, end - start);
            _pos = Math.Min(limit, end + 1);
            return s;
        }
    }

    /// <summary>小端二进制写入器。</summary>
    public sealed class ByteWriter
    {
        private readonly MemoryStream _ms = new MemoryStream();

        public int Length { get { return (int)_ms.Length; } }

        public ByteWriter WriteByte(byte v)
        {
            _ms.WriteByte(v);
            return this;
        }

        public ByteWriter WriteSByte(sbyte v)
        {
            _ms.WriteByte(unchecked((byte)v));
            return this;
        }

        public ByteWriter WriteInt16(short v)
        {
            _ms.WriteByte((byte)(v & 0xFF));
            _ms.WriteByte((byte)((v >> 8) & 0xFF));
            return this;
        }

        public ByteWriter WriteUInt16(ushort v)
        {
            return WriteInt16(unchecked((short)v));
        }

        public ByteWriter WriteInt32(int v)
        {
            _ms.WriteByte((byte)(v & 0xFF));
            _ms.WriteByte((byte)((v >> 8) & 0xFF));
            _ms.WriteByte((byte)((v >> 16) & 0xFF));
            _ms.WriteByte((byte)((v >> 24) & 0xFF));
            return this;
        }

        public ByteWriter WriteUInt32(uint v)
        {
            return WriteInt32(unchecked((int)v));
        }

        public ByteWriter WriteInt64(long v)
        {
            WriteInt32(unchecked((int)(v & 0xFFFFFFFF)));
            WriteInt32(unchecked((int)(v >> 32)));
            return this;
        }

        public ByteWriter WriteBytes(byte[] data)
        {
            if (data != null && data.Length > 0) _ms.Write(data, 0, data.Length);
            return this;
        }

        public byte[] ToArray()
        {
            return _ms.ToArray();
        }
    }
}
