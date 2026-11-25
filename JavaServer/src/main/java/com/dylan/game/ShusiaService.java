package com.dylan.game;

import com.dylan.protocol.body.Cmd9ReplyBody;

public final class ShusiaService {

    private ShusiaService() {
    }

    public record Outcome(int error, String reason, long cost, long money) {

        public boolean ok() {
            return error == 0;
        }

        static Outcome rejected(int error, String reason, long money) {
            return new Outcome(error, reason, 0, money);
        }
    }

    public static Outcome recover(GameSession session) {
        InventoryService inventory = session.inventory();
        long money = inventory.money();

        StaminaService stamina = session.stamina();
        if (!stamina.weakened()) {
            return Outcome.rejected(Cmd9ReplyBody.ERROR_NOT_WEAKENED,
                    "体力已经是 " + stamina.current() + "，不虚弱", money);
        }

        int price = price(session, stamina.current());
        if (money < price) {
            return Outcome.rejected(Cmd9ReplyBody.ERROR_NOT_ENOUGH_MONEY,
                    "要 " + price + " 金币，身上只有 " + money, money);
        }

        inventory.setMoney(money - price);
        stamina.setFull("索西雅的解除虚弱服务，收 " + price + " 金币");
        return new Outcome(0, "", price, inventory.money());
    }

    public static int price(GameSession session, int stamina) {
        var scripts = session.scripts();
        if (scripts == null) {
            return 0;
        }
        return scripts.serverParameter().recoverStaminaPrice(session.level(), stamina);
    }
}
