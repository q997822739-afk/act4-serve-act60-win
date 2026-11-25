using System.Security.Cryptography;
using System.Text;

namespace A4Launcher.Services;

/// <summary>
/// 「记住密码」用 DPAPI 加密（当前 Windows 用户），换机器/换用户解不开。
/// 账号规则比 A12 宽（字母数字下划线），所以熵只用固定标签，不带账号。
/// .NET Framework 4.7.2 上 ProtectedData 在 System.Security.dll 里（csproj 已加引用），
/// CryptographicOperations.ZeroMemory 没有，用 Array.Clear 代替。
/// </summary>
public static class CredentialProtection
{
    private const string EntropyLabel = "A4Launcher:CredentialProtection:v1";

    public static string Protect(string password)
    {
        if (password is null) throw new ArgumentNullException(nameof(password));
        var plaintext = Encoding.UTF8.GetBytes(password);
        var entropy = Encoding.UTF8.GetBytes(EntropyLabel);
        try
        {
            return Convert.ToBase64String(
                ProtectedData.Protect(plaintext, entropy, DataProtectionScope.CurrentUser));
        }
        finally
        {
            Zero(plaintext);
            Zero(entropy);
        }
    }

    public static string? Unprotect(string protectedValue)
    {
        if (string.IsNullOrEmpty(protectedValue)) return null;
        try
        {
            var ciphertext = Convert.FromBase64String(protectedValue);
            var entropy = Encoding.UTF8.GetBytes(EntropyLabel);
            var plaintext = ProtectedData.Unprotect(ciphertext, entropy, DataProtectionScope.CurrentUser);
            try
            {
                return Encoding.UTF8.GetString(plaintext);
            }
            finally
            {
                Zero(plaintext);
                Zero(ciphertext);
                Zero(entropy);
            }
        }
        catch (CryptographicException)
        {
            // 别的用户/别的机器存下来的，当成没有就行
            return null;
        }
        catch (FormatException)
        {
            return null;
        }
    }

    private static void Zero(byte[] buffer) => Array.Clear(buffer, 0, buffer.Length);
}
