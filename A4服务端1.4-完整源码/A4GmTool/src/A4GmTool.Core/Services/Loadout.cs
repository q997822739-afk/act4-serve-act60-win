using System;
using System.Collections.Generic;

namespace A4GmTool.Core.Services
{
    /// <summary>一键紫装：按职业挑一套可用的装备。</summary>
    public sealed class Loadout
    {
        public sealed class Pick
        {
            public string Part = "";
            public string PartName = "";
            public bool Found;
            public bool InSet;
            public int Id;
            public string Name = "";
            public string Subtype = "";
            public string SubtypeName = "";
            public string JobName = "";
            public int Rarity;
            public int MinLevel;
            public string Icon = "";
        }

        public int CharacNo;
        public int Job;
        public int GrowType;
        public int Branch;
        public int Level;
        public string Title = "";
        public string SetName = "";
        public List<string> SetSlots = new List<string>();
        public int FoundCount;
        public List<Pick> Picks = new List<Pick>();

        /// <summary>按职业 + 等级挑装备。远程版挑的是同职业最优品级，这里挑等级不超上限的最高品级。</summary>
        public static Loadout Build(GmEnv env, int characNo, int job, int growType, int level)
        {
            Loadout lo = new Loadout();
            lo.CharacNo = characNo;
            lo.Job = job;
            lo.GrowType = growType;
            lo.Level = level;
            lo.Branch = level >= 50 ? 1 : 0;
            lo.Title = JobDisplayName(JobKey(job)) + " · " + level + " 级一键配装";

            string[] parts = { "weapon", "jacket", "shoulder", "pants", "shoes", "belt", "amulet", "wrist", "ring" };
            string jobKey = JobKey(job);

            foreach (string part in parts)
            {
                Pick pick = new Pick { Part = part, PartName = PartName(part) };

                CatalogEntry best = null;
                foreach (CatalogEntry e in env.Catalog.Items())
                {
                    if (e.Kind != Catalog.KindWeapon && e.Kind != Catalog.KindArmor) continue;
                    if (!string.Equals(e.Part, part, StringComparison.OrdinalIgnoreCase)) continue;
                    if (e.MinLevel > level) continue;

                    if (e.Job != "common" && !string.Equals(e.Job, jobKey, StringComparison.OrdinalIgnoreCase)) continue;

                    if (best == null || e.Rarity > best.Rarity || (e.Rarity == best.Rarity && e.MinLevel > best.MinLevel))
                    {
                        best = e;
                    }
                }

                if (best != null)
                {
                    pick.Found = true;
                    pick.Id = best.Id;
                    pick.Name = best.Name;
                    pick.Subtype = best.Subtype;
                    pick.SubtypeName = best.SubtypeName;
                    pick.JobName = best.JobName;
                    pick.Rarity = best.Rarity;
                    pick.MinLevel = best.MinLevel;
                    pick.Icon = best.IconPath;
                    lo.FoundCount++;
                }

                lo.Picks.Add(pick);
            }

            return lo;
        }

        public static string JobDisplayName(string key)
        {
            switch ((key ?? "").ToLowerInvariant())
            {
                case "swordman": return "鬼剑士";
                case "fighter": return "格斗家";
                case "gunner": return "神枪手";
                case "mage": return "魔法师";
                case "priest": return "圣职者";
                default: return "通用";
            }
        }

        public static string JobKey(int job)
        {
            switch (job)
            {
                case 0: return "swordman";
                case 1: return "fighter";
                case 2: return "gunner";
                case 3: return "mage";
                case 4: return "priest";
                case 5: return "swordman";
                case 6: return "fighter";
                case 7: return "gunner";
                default: return "common";
            }
        }

        public static string PartName(string part)
        {
            switch (part)
            {
                case "weapon": return "武器";
                case "jacket": return "上衣";
                case "shoulder": return "头肩";
                case "pants": return "下装";
                case "shoes": return "鞋";
                case "belt": return "腰带";
                case "amulet": return "项链";
                case "wrist": return "手镯";
                case "ring": return "戒指";
                default: return part;
            }
        }
    }
}
