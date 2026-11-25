package com.dylan.game;

import com.dylan.game.handler.A4TownInitHandler;
import com.dylan.game.handler.AntiBotHandler;
import com.dylan.game.handler.CashShopHandler;
import com.dylan.game.handler.CharacterHandler;
import com.dylan.game.handler.ChatHandler;
import com.dylan.game.handler.ClearRewardHandler;
import com.dylan.game.handler.CombatHandler;
import com.dylan.game.handler.CompoundAvatarHandler;
import com.dylan.game.handler.CompoundItemHandler;
import com.dylan.game.handler.DisjointHandler;
import com.dylan.game.handler.DropItemHandler;
import com.dylan.game.handler.ConnectionHandler;
import com.dylan.game.handler.CreatureHandler;
import com.dylan.game.handler.DeathHandler;
import com.dylan.game.handler.DungeonHandler;
import com.dylan.game.handler.EmotionHandler;
import com.dylan.game.handler.EnchantHandler;
import com.dylan.game.handler.ExpertJobHandler;
import com.dylan.game.handler.GameOptionHandler;
import com.dylan.game.handler.GmHandler;
import com.dylan.game.handler.IncreaseStatusHandler;
import com.dylan.game.handler.UseStackableHandler;
import com.dylan.game.handler.InventoryHandler;
import com.dylan.game.handler.ItemLockHandler;
import com.dylan.game.handler.LotteryHandler;
import com.dylan.game.handler.BoosterHandler;
import com.dylan.game.handler.MultiBoxHandler;
import com.dylan.game.handler.MailHandler;
import com.dylan.game.handler.QuestHandler;
import com.dylan.game.handler.RepairHandler;
import com.dylan.game.handler.ResetItemAttrHandler;
import com.dylan.game.handler.ShopHandler;
import com.dylan.game.handler.SkillHandler;
import com.dylan.game.handler.StaminaHandler;
import com.dylan.game.handler.TownHandler;
import com.dylan.game.handler.TutorialHandler;
import com.dylan.game.handler.UpgradeHandler;

import java.util.List;

public final class GameHandlers {

    private GameHandlers() {
    }

    public static List<CommandHandler> all() {
        return List.of(
                new ConnectionHandler(),
                new CharacterHandler(),
                new TownHandler(),
                new ChatHandler(),
                new DungeonHandler(),
                new CombatHandler(),
                new DropItemHandler(),
                new DeathHandler(),
                new ClearRewardHandler(),
                new QuestHandler(),
                new InventoryHandler(),
                new ItemLockHandler(),
                new SkillHandler(),
                new UpgradeHandler(),
                new ResetItemAttrHandler(),
                new IncreaseStatusHandler(),
                new UseStackableHandler(),
                new LotteryHandler(),
                new MultiBoxHandler(),
                new BoosterHandler(),
                new TutorialHandler(),
                new EmotionHandler(),
                new StaminaHandler(),
                new ExpertJobHandler(),
                new EnchantHandler(),
                new GameOptionHandler(),
                new CashShopHandler(),
                new MailHandler(),
                new CreatureHandler(),
                new ShopHandler(),
                new RepairHandler(),
                new CompoundAvatarHandler(),
                new CompoundItemHandler(),
                new DisjointHandler(),
                new GmHandler(),
                new AntiBotHandler(),
                new A4TownInitHandler(),
                new com.dylan.game.handler.StallHandler(),
                new com.dylan.game.handler.PeerHandler(),
                new com.dylan.game.handler.GuildHandler(),
                new com.dylan.game.handler.MentorHandler(),
                new com.dylan.game.handler.FriendHandler(),
                new com.dylan.game.handler.PvpHandler());
    }

    public static CommandRouter router() {
        List<CommandHandler> handlers = all();
        return new CommandRouter(handlers);
    }
}
