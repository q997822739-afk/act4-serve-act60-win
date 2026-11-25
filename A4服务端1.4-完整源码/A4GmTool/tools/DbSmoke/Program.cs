using System;
using A4GmTool.Core.Data;

namespace DbSmoke
{
    internal static class Program
    {
        private static int Main(string[] args)
        {
            string dbPath = args.Length > 0 ? args[0] : @"H:\60ACT4项目\A4ServerRelease\data\s1a5.db";
            try
            {
                using (SqliteConnection conn = SqliteConnection.OpenReadOnly(dbPath))
                {
                    using (SqliteCommand cmd = conn.CreateCommand("SELECT COUNT(*) FROM charac_info WHERE delete_flag = 0"))
                    {
                        cmd.Read();
                        Console.WriteLine("角色数 = " + cmd.GetInt64(0));
                    }

                    using (SqliteCommand cmd = conn.CreateCommand(
                        "SELECT c.charac_no, c.m_id, c.charac_name, c.lev, c.job, IFNULL(i.money,0) "
                        + "FROM charac_info c LEFT JOIN inventory i ON i.charac_no = c.charac_no "
                        + "WHERE c.delete_flag = 0 ORDER BY c.charac_no LIMIT 10"))
                    {
                        while (cmd.Read())
                        {
                            Console.WriteLine("  characNo=" + cmd.GetInt64(0) + " mId=" + cmd.GetInt64(1)
                                + " name=" + cmd.GetString(2) + " lev=" + cmd.GetInt32(3)
                                + " job=" + cmd.GetInt32(4) + " money=" + cmd.GetInt64(5));
                        }
                    }

                    using (SqliteCommand cmd = conn.CreateCommand("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name"))
                    {
                        int n = 0;
                        while (cmd.Read()) n++;
                        Console.WriteLine("表数 = " + n);
                    }

                    // 背包 blob 解码
                    using (SqliteCommand cmd = conn.CreateCommand("SELECT charac_no, inventory FROM inventory WHERE inventory IS NOT NULL LIMIT 1"))
                    {
                        if (cmd.Read())
                        {
                            long no = cmd.GetInt64(0);
                            byte[] blob = cmd.GetBlob(1);
                            Console.WriteLine("inventory blob charac=" + no + " bytes=" + blob.Length);
                            ItemContainer c = ItemContainer.Decode(blob, ItemContainer.InventorySlots);
                            int used = c.Count(it => !it.IsEmpty);
                            Console.WriteLine("  解码成功：槽位=" + c.Slots + " 已占用=" + used);
                            for (int i = 0; i < c.Slots; i++)
                            {
                                if (!c[i].IsEmpty) Console.WriteLine("   slot " + i + " = " + c[i]);
                            }
                        }
                    }
                }
                return 0;
            }
            catch (Exception ex)
            {
                Console.WriteLine("FAILED: " + ex.Message);
                Console.WriteLine(ex.StackTrace);
                return 1;
            }
        }
    }
}
