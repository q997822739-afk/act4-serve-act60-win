using System;
using System.Collections.Generic;
using A4GmTool.Core.Pvf;

namespace PvfSmoke
{
    internal static class Program
    {
        private static int Main(string[] args)
        {
            string pvfPath = args.Length > 0 ? args[0] : @"H:\60ACT4项目\A4ServerRelease\client\Script.pvf";
            try
            {
                using (PvfArchive archive = PvfArchive.Open(pvfPath))
                {
                    Console.WriteLine("guid      = " + archive.Guid);
                    Console.WriteLine("version   = " + archive.FileVersion);
                    Console.WriteLine("entries   = " + archive.Count);
                    Console.WriteLine("dataBase  = " + archive.DataBase);

                    GameScripts gs = GameScripts.Load(archive, m => Console.WriteLine("  [warn] " + m));
                    Console.WriteLine("item paths = " + gs.ItemCount);
                    Console.WriteLine("string tables chn = " + (gs.StringTables == null ? -1 : gs.StringTables.Count));

                    int[] probe = { 10000, 10001, 1, 2, 8, 100, 1000 };
                    foreach (int id in probe)
                    {
                        string path = gs.PathOf(id);
                        string name = gs.NameOf(id);
                        Console.WriteLine("  item " + id + " -> [" + (path ?? "<无>") + "] name=[" + name + "]");
                    }

                    int named = 0, total = 0;
                    List<string> sample = new List<string>();
                    foreach (int id in gs.ItemIds)
                    {
                        total++;
                        if (total > 400) break;
                        string n = gs.NameOf(id);
                        if (!string.IsNullOrEmpty(n)) named++;
                        if (sample.Count < 8 && !string.IsNullOrEmpty(n)) sample.Add(id + "=" + n);
                    }
                    Console.WriteLine("tested " + total + " items, named " + named);
                    Console.WriteLine("sample: " + string.Join(" | ", sample.ToArray()));
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
