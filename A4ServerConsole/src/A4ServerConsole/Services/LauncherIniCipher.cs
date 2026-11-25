using System;
using System.IO;
using System.Security.Cryptography;
using System.Text;

namespace A4ServerConsole.Services
{
    /// <summary>
    /// launcher.ini 加密格式（与登录器 A4Launcher\Services\IniCrypt.cs 必须完全一致）。
    ///
    /// 文件内容 = "A4INI1\n" + Base64( nonce[16] + AES-256-CBC(明文UTF8, PKCS7) + HMAC-SHA256[32] )
    /// 密钥与 IV 都由固定密钥材料 + nonce 派生，保证每次生成的密文都不一样（相同配置也不同）；
    /// 登录器读到 "A4INI1" 头就自动解密，读不到就按普通明文 INI 解析（旧登录器/旧配置仍可用）。
    /// 注意：客户端工具必然要能解开它，所以这是“防普通玩家看明文”，不是密码学意义上的保密。
    /// </summary>
    public static class LauncherIniCipher
    {
        public const string Magic = "A4INI1";

        // 固定密钥材料（改了必须同步改登录器里的同一串）
        private static readonly byte[] KeyMaterial = Encoding.ASCII.GetBytes(
            "A4.S1A5.LauncherIni.v1|9f2c7d41e8b04c6aa35d1e7f0b9c4d28|c6e1-4b7a-9d03-5f28-a4a4a4a4a4a4");

        public static bool IsEncrypted(string text)
        {
            if (string.IsNullOrEmpty(text)) return false;
            return text.StartsWith(Magic, StringComparison.Ordinal);
        }

        public static bool IsEncryptedFile(string path)
        {
            try
            {
                if (!File.Exists(path)) return false;
                using var fs = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.ReadWrite);
                var buf = new byte[Magic.Length];
                int n = fs.Read(buf, 0, buf.Length);
                return n == buf.Length && IsEncrypted(Encoding.ASCII.GetString(buf));
            }
            catch
            {
                return false;
            }
        }

        public static string Encrypt(string plainText)
        {
            byte[] plain = Encoding.UTF8.GetBytes(plainText ?? "");
            var nonce = new byte[16];
            using (var rng = RandomNumberGenerator.Create()) rng.GetBytes(nonce);

            byte[] encKey, macKey, iv;
            Derive(nonce, out encKey, out macKey, out iv);

            byte[] cipher;
            using (var aes = Aes.Create())
            {
                aes.Mode = CipherMode.CBC;
                aes.Padding = PaddingMode.PKCS7;
                aes.Key = encKey;
                aes.IV = iv;
                using var enc = aes.CreateEncryptor();
                cipher = enc.TransformFinalBlock(plain, 0, plain.Length);
            }

            byte[] mac;
            using (var hmac = new HMACSHA256(macKey))
            {
                var buf = new byte[nonce.Length + cipher.Length];
                Buffer.BlockCopy(nonce, 0, buf, 0, nonce.Length);
                Buffer.BlockCopy(cipher, 0, buf, nonce.Length, cipher.Length);
                mac = hmac.ComputeHash(buf);
            }

            var all = new byte[nonce.Length + cipher.Length + mac.Length];
            Buffer.BlockCopy(nonce, 0, all, 0, nonce.Length);
            Buffer.BlockCopy(cipher, 0, all, nonce.Length, cipher.Length);
            Buffer.BlockCopy(mac, 0, all, nonce.Length + cipher.Length, mac.Length);

            return Magic + "\n" + Convert.ToBase64String(all);
        }

        public static string Decrypt(string fileText)
        {
            string body = fileText.StartsWith(Magic, StringComparison.Ordinal)
                ? fileText.Substring(Magic.Length).TrimStart('\r', '\n')
                : fileText;
            byte[] all = Convert.FromBase64String(body.Replace("\r", "").Replace("\n", "").Trim());
            if (all.Length < 16 + 16 + 32) throw new CryptographicException("launcher.ini 内容不完整");

            var nonce = new byte[16];
            Buffer.BlockCopy(all, 0, nonce, 0, 16);
            int cipherLen = all.Length - 16 - 32;
            var cipher = new byte[cipherLen];
            Buffer.BlockCopy(all, 16, cipher, 0, cipherLen);
            var mac = new byte[32];
            Buffer.BlockCopy(all, 16 + cipherLen, mac, 0, 32);

            byte[] encKey, macKey, iv;
            Derive(nonce, out encKey, out macKey, out iv);

            byte[] expect;
            using (var hmac = new HMACSHA256(macKey))
            {
                var buf = new byte[16 + cipherLen];
                Buffer.BlockCopy(nonce, 0, buf, 0, 16);
                Buffer.BlockCopy(cipher, 0, buf, 16, cipherLen);
                expect = hmac.ComputeHash(buf);
            }
            if (!FixedTimeEquals(expect, mac)) throw new CryptographicException("launcher.ini 校验失败（文件被改动过）");

            using var aes = Aes.Create();
            aes.Mode = CipherMode.CBC;
            aes.Padding = PaddingMode.PKCS7;
            aes.Key = encKey;
            aes.IV = iv;
            using var dec = aes.CreateDecryptor();
            byte[] plain = dec.TransformFinalBlock(cipher, 0, cipher.Length);
            return Encoding.UTF8.GetString(plain);
        }

        private static void Derive(byte[] nonce, out byte[] encKey, out byte[] macKey, out byte[] iv)
        {
            encKey = new byte[32];
            macKey = new byte[32];
            iv = new byte[16];
            using var hmac = new HMACSHA256(KeyMaterial);
            var buf = new byte[KeyMaterial.Length + nonce.Length];
            Buffer.BlockCopy(KeyMaterial, 0, buf, 0, KeyMaterial.Length);
            Buffer.BlockCopy(nonce, 0, buf, KeyMaterial.Length, nonce.Length);
            byte[] d = hmac.ComputeHash(buf);
            Buffer.BlockCopy(d, 0, encKey, 0, 32);
            d[31] ^= 0x5A;
            byte[] d2 = hmac.ComputeHash(d);
            Buffer.BlockCopy(d2, 0, macKey, 0, 32);
            Buffer.BlockCopy(d2, 16, iv, 0, 16);
        }

        private static bool FixedTimeEquals(byte[] a, byte[] b)
        {
            if (a.Length != b.Length) return false;
            int diff = 0;
            for (int i = 0; i < a.Length; i++) diff |= a[i] ^ b[i];
            return diff == 0;
        }
    }
}
