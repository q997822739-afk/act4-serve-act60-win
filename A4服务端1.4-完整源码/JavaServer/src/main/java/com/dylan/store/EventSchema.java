package com.dylan.store;

final class EventSchema {

    private EventSchema() {
    }

    static final String[] TABLES = {
            """
            CREATE TABLE IF NOT EXISTS dnf_event_log (
              log_id     INTEGER      NOT NULL PRIMARY KEY AUTOINCREMENT,
              occ_time   INT          NOT NULL DEFAULT 0,
              event_type TINYINT UNSIGNED NOT NULL DEFAULT 0,
              parameter1 INT UNSIGNED NOT NULL DEFAULT 0,
              parameter2 INT UNSIGNED NOT NULL DEFAULT 0,
              server_id  TINYINT UNSIGNED NOT NULL DEFAULT 0,
              event_flag TINYINT      DEFAULT 0,
              start_time INT          NOT NULL DEFAULT 0,
              end_time   INT          NOT NULL DEFAULT 0,
              m_id       INT          NOT NULL DEFAULT 0,
              expl       VARCHAR(200) NOT NULL DEFAULT '',
              etc        VARCHAR(100) NOT NULL DEFAULT ''
            )
            """,

            """
            CREATE TABLE IF NOT EXISTS dnf_event_info (
              event_id      INT          NOT NULL DEFAULT 0,
              event_name    VARCHAR(30)  NOT NULL DEFAULT '',
              event_explain VARCHAR(100) NOT NULL DEFAULT '',
              apply_type    TINYINT      NOT NULL DEFAULT 0,
              start_date    DATE         NOT NULL DEFAULT '1970-01-01',
              end_date      DATE         NOT NULL DEFAULT '1970-01-01',
              client_text   VARCHAR(60)  NOT NULL DEFAULT '',
              PRIMARY KEY (event_id)
            )
            """,
    };

    static final String[] INDEXES = {
            "CREATE INDEX IF NOT EXISTS dnf_event_log_idx_occ_time ON dnf_event_log (occ_time)",
            "CREATE INDEX IF NOT EXISTS dnf_event_log_idx_sever_id ON dnf_event_log (server_id)",
            "CREATE UNIQUE INDEX IF NOT EXISTS dnf_event_info_event_name ON dnf_event_info (event_name)",
    };

    record EventInfoRow(int eventId, String name, String explain, int applyType) {
    }

    static final EventInfoRow[] EVENT_INFO_SEED = {
            new EventInfoRow(1, "CUnlimitFatigueEvent", "疲勞度無限大", 0),
            new EventInfoRow(2, "CMaxFatigueFactorEvent", "最大疲勞度|百分比", 1),
            new EventInfoRow(3, "CExpDoubleEvent", "雙倍經驗|百分比", 1),
            new EventInfoRow(4, "CCoinEventPerDay",
                    "每日分發復活幣|1~17級|18~26級|27級以上|未使用", 4),
            new EventInfoRow(5, "CCoinEventOnCharCreate", "創建角色時，分發復活幣|復活幣數|", 1),
            new EventInfoRow(6, "CLeadingChannelEvent", "頻道引導", 0),
            new EventInfoRow(7, "CItemDropRatioEvent", "道具掉落率加倍|倍數", 1),
            new EventInfoRow(8, "CPCRoomBurningEvent", "網咖燃燒時間|百分比", 1),
            new EventInfoRow(9, "CSchoolMatchEvent", "超級學校對戰", 3),
            new EventInfoRow(10, "CPCRoomFatigueEvent", "網咖玩家疲勞度無限大活動", 0),
            new EventInfoRow(12, "CReformingDanjinEvent", "土罐改版活動", 0),
            new EventInfoRow(13, "CCoinRefillEvent", "復活幣加值活動|第一時間|第二十間", 2),
            new EventInfoRow(15, "CBurningFatigueEvent", "燃燒疲勞度活動", 0),
            new EventInfoRow(16, "CClearRewardCardEvent", "完成地下城 獎賞限定道具活動", 0),
            new EventInfoRow(17, "CCeraShopBonusItemEvent",
                    "商城BONUS道具活動|給予BONUS道具的機率是（1000分比。如為100，將無條件獲得道具。"
                            + "如為10，將有10%機率可獲得）", 1),
            new EventInfoRow(18, "CTournamentPvPEvent", "撥放用決戰場", 0),
            new EventInfoRow(19, "CGoldCardBlankItemEvent", "金卡活動", 0),
            new EventInfoRow(21, "CCollectArchieveEventLog", "收集達成成就的Log", 0),
            new EventInfoRow(22, "CPCRoomWorldDropEvent", "網咖world掉落活動", 0),
            new EventInfoRow(24, "CPartyExpBonusEvent", "組隊經驗值獎賞|百分比", 1),
            new EventInfoRow(29, "CPcRoomCardBlankItemEvent", "網咖卡Blank活動", 0),
            new EventInfoRow(30, "CPowerWarEvent", "勢力戰活動", 0),
            new EventInfoRow(32, "CStabToDeathEvent", "刺殺週活動", 0),
            new EventInfoRow(33, "CGuildWarEvent", "", 0),
            new EventInfoRow(34, "CAutoMarketConditionsControlEv", "", 0),
            new EventInfoRow(35, "CVendingMachineBonusEvent", "自動售貨機BONUS活動", 0),
            new EventInfoRow(36, "CBurnigGoldMonsterEvent", "燃燒金怪物活動", 0),
            new EventInfoRow(38, "CNoNeedGoldOnGuildCreateEvent", "免費創設公會活動", 0),
            new EventInfoRow(39, "CDeathTowerWinPointEvent", "死亡之塔/迷妄之塔 apc活動|百分比", 1),
            new EventInfoRow(40, "CNotApplyBalkeunEvent", "未套用COF指數活動", 0),
            new EventInfoRow(41, "CCharacterDayEvent",
                    "角色日活動|職業號碼（0:鬼劍士,1:格鬥家,2:神槍手,3:魔法師,4:聖職者,"
                            + "5:女神槍手,6:盜賊,7:格鬥家(男),100:Game Script）", 1),
            new EventInfoRow(42, "CAssaultOnOffEvent", "防止街頭爭霸活動", 0),
            new EventInfoRow(43, "CFatigueBuffEvent", "疲勞度 Buff活動", 0),
            new EventInfoRow(45, "CBloodDungeonRewardFirstEvent", "無盡的祭壇中獎金 平時|中獎金", 1),
            new EventInfoRow(46, "CBloodDungeonRewardSecondEvent", "無盡的祭壇中獎金 活動時|中獎金", 1),
            new EventInfoRow(48, "CPCRoomWorldDropEvent2nd", "網咖world掉落活動 2nd", 0),
            new EventInfoRow(49, "CRestrictCharacCreationEvent", "角色伺服器生成限制", 1),
            new EventInfoRow(50, "CReduceUpgradeItemPay", "強化費用折扣活動", 1),
            new EventInfoRow(51, "COnTimeEvent", "On Time活動", 2),
            new EventInfoRow(52, "CBreakAwayPreventEvent", "防止脫離系統", 0),
            new EventInfoRow(53, "CPowerWarVictoriousEvent", "勢力戰勝利勢力耐久度活動", 0),
            new EventInfoRow(54, "CPvPExpPenaltyEvent", "決鬥場經驗值處罰 ", 0),
            new EventInfoRow(55, "CPvPLiveEvent", "Live 統合決鬥場活動", 0),
            new EventInfoRow(56, "CIntegratedPvPServerMatchEvent", "伺服器對抗戰活動", 0),
            new EventInfoRow(57, "COnlinePreliminaryEvent", "聯賽線上預選", 0),
            new EventInfoRow(58, "CSecretShopEvent", "神秘商店活動", 0),
            new EventInfoRow(61, "CDnFLeaguePromoteFirstEvent", "聯賽宣傳（星期四）", 0),
            new EventInfoRow(62, "CDnFLeaguePromoteSecondEvent", "聯賽宣傳（星期五）", 0),
            new EventInfoRow(64, "CDoubleGoldCardEvent", "金卡獎賞2倍活動", 0),
            new EventInfoRow(65, "CPremiumGoldCard", "優惠金卡活動", 0),
            new EventInfoRow(67, "CGmRegistEvent", "GM Web manager tool登入", 0),
            new EventInfoRow(86, "CFatigueAttendance", "出席活動", 0),
            new EventInfoRow(87, "CWeekendBonusEvent", "周末BONUS活動", 0),
            new EventInfoRow(91, "CUXGameLogEvent", "UX Game Log 系統", 0),
            new EventInfoRow(92, "CPCRoomPlayTimeEvent", "網咖遊戲時間活動", 0),
            new EventInfoRow(93, "LevelUpBefore70LvEvent", "升級活動", 0),
            new EventInfoRow(94, "CDimensionActivationEvent", "異界地下城活性化活動", 0),
            new EventInfoRow(95, "BlueMarbleDungeonEvent", "活動地下城", 0),
            new EventInfoRow(96, "AttendanceEvent", "2012年出席活動", 1),
            new EventInfoRow(100, "GrowthEquipEvent", "成長型裝備活動", 0),
            new EventInfoRow(101, "CFatigueGiveItemEvent", "消耗疲勞度分發道具活動", 0),
            new EventInfoRow(102, "CStopOverlabExpEvent", "成長之秘方禁止效果重複活動", 0),
            new EventInfoRow(103, "GiveGrowCreatureEvent", "分發成長型道具寵物活動", 0),
            new EventInfoRow(104, "NewAccountLevelUpEventToJob", "達成職業別等級 分發道具活動", 0),
            new EventInfoRow(105, "SeriaRoomAniDecoEvent", "布置賽莉亞房活動(動畫)", 0),
            new EventInfoRow(106, "BingoEvent", "賓果活動", 0),
            new EventInfoRow(109, "OneADayItemShopEvent", "OneADay商店活動", 0),
            new EventInfoRow(113, "CConditionEvent", "event_msg_67 == NULL, Etc/Etc.kor.str : ", 0),
            new EventInfoRow(116, "CAradRyosikaEvent",
                    "event_msg_190 == NULL, Etc/Etc.kor.str : ", 2),
            new EventInfoRow(119, "CEventAdvanceAltarOpen", "分發成長型道具寵物活動", 0),
            new EventInfoRow(155, "Arad_MomijiEvent",
                    "event_msg_196 == NULL, Etc/Etc.kor.str : ", 4),
            new EventInfoRow(158, "LevelupSupportEvent",
                    "event_msg_158 == NULL, Etc/Etc.kor.str : ", 0),
            new EventInfoRow(159, "CEventStayTime", "event_msg_159 == NULL, Etc/Etc.kor.str : ", 2),
            new EventInfoRow(160, "CEventCreateDnf", "event_msg_160 == NULL, Etc/Etc.kor.str : ", 4),
            new EventInfoRow(161, "CEventCeraShopRewardPoint", "Cera Point Event", 2),
            new EventInfoRow(162, "EventNewCharacterReward",
                    "event_msg_162 == NULL, Etc/Etc.kor.str : ", 2),
            new EventInfoRow(163, "LevelupSupport2ndEvent",
                    "event_msg_164 == NULL, Etc/Etc.kor.str : ", 0),
            new EventInfoRow(164, "HeroMissionEvent",
                    "event_msg_163 == NULL, Etc/Etc.kor.str : ", 0),
            new EventInfoRow(165, "EventGiveMeBox", "event_msg_165 == NULL, Etc/Etc.kor.str : ", 0),
    };
}
