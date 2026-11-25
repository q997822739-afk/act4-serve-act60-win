using System;
using System.IO;
using System.Security.Cryptography;
using System.Text;

namespace A4Launcher.Services;

/// <summary>
/// launcher.ini 解密（与控制台 A4ServerConsole\Services\LauncherIniCipher.cs 必须完全一致）。
///
/// 文件内容 = "A4INI1\n" + Base64( nonce[16] + AES-256-CBC(明文UTF8, PKCS7) + HMAC-SHA256[32] )
/// 没有 "A4INI1" 头的就是普通明文 INI（兼容老配置、也方便自己手工改）。
/// </summary>
public static class IniCrypt
{
    public const string Magic = "A4INI1";

    private static readonly byte[] KeyMaterial = Encoding.ASCII.GetBytes(
        "A4.S1A5.LauncherIni.v1|9f2c7d41e8b04c6aa35d1e7f0b9c4d28|c6e1-4b7a-9d03-5f28-a4a4a4a4a4a4");

    public static bool IsEncrypted(string text)
        => !string.IsNullOrEmpty(text) && text.StartsWith(Magic, StringComparison.Ordinal);

    /// <summary>读 launcher.ini 全文：密文自动解密，明文原样返回。失败返回 null。</summary>
    public static string? ReadText(string path)
    {
        try
        {
            if (!File.Exists(path)) return null;
            var raw = File.ReadAllText(path, Encoding.UTF8);
            if (!IsEncrypted(raw)) return raw;
            return Decrypt(raw);
        }
        catch (Exception e) when (e is IOException or CryptographicException or FormatException
                                      or UnauthorizedAccessException or NotSupportedException)
        {
            return null;
        }
    }

    private static string Decrypt(string fileText)
    {
        var body = fileText.Substring(Magic.Length).TrimStart('\r', '\n');
        var all = Convert.FromBase64String(body.Replace("\r", "").Replace("\n", "").Trim());
        if (all.Length < 16 + 16 + 32) throw new FormatException("launcher.ini 内容不完整");

        var nonce = new byte[16];
        Buffer.BlockCopy(all, 0, nonce, 0, 16);
        var cipherLen = all.Length - 16 - 32;
        var cipher = new byte[cipherLen];
        Buffer.BlockCopy(all, 16, cipher, 0, cipherLen);
        var mac = new byte[32];
        Buffer.BlockCopy(all, 16 + cipherLen, mac, 0, 32);

        Derive(nonce, out var encKey, out var macKey, out var iv);

        byte[] expect;
        using (var hmac = new HMACSHA256(macKey))
        {
            var buf = new byte[16 + cipherLen];
            Buffer.BlockCopy(nonce, 0, buf, 0, 16);
            Buffer.BlockCopy(cipher, 0, buf, 16, cipherLen);
            expect = hmac.ComputeHash(buf);
        }
        if (!FixedTimeEquals(expect, mac)) throw new CryptographicException("launcher.ini 校验失败");

        using var aes = Aes.Create();
        aes.Mode = CipherMode.CBC;
        aes.Padding = PaddingMode.PKCS7;
        aes.Key = encKey;
        aes.IV = iv;
        using var dec = aes.CreateDecryptor();
        return Encoding.UTF8.GetString(dec.TransformFinalBlock(cipher, 0, cipher.Length));
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
        var d = hmac.ComputeHash(buf);
        Buffer.BlockCopy(d, 0, encKey, 0, 32);
        d[31] ^= 0x5A;
        var d2 = hmac.ComputeHash(d);
        Buffer.BlockCopy(d2, 0, macKey, 0, 32);
        Buffer.BlockCopy(d2, 16, iv, 0, 16);
    }

    private static bool FixedTimeEquals(byte[] a, byte[] b)
    {
        if (a.Length != b.Length) return false;
        var diff = 0;
        for (var i = 0; i < a.Length; i++) diff |= a[i] ^ b[i];
        return diff == 0;
    }
}
