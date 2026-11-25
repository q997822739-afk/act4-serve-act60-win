package com.dylan.game.handler;

import java.util.Set;

import com.dylan.game.CommandHandler;
import com.dylan.game.GameSession;
import com.dylan.game.ItemLockService;
import com.dylan.net.ServerConfig;
import com.dylan.protocol.body.Cmd229ItemLockRequest;
import com.dylan.protocol.body.Cmd229ReplyBody;
import com.dylan.protocol.body.Cmd230ReplyBody;
import com.dylan.protocol.body.Cmd231ReplyBody;

public final class ItemLockHandler implements CommandHandler {

    @Override
    public Set<Integer> handles() {
        // A4 是 203/204/205。204 在 A4 上是解锁，剧情暂停让给 DungeonHandler 的非 A4 分支。
        if (!ServerConfig.A4) {
            return Set.of(Cmd229ItemLockRequest.PACKET_ID_LOCK,
                    Cmd229ItemLockRequest.PACKET_ID_UNLOCK,
                    Cmd229ItemLockRequest.PACKET_ID_UNLOCK_CANCEL);
        }
        return Set.of(Cmd229ItemLockRequest.PACKET_ID_LOCK,
                Cmd229ItemLockRequest.PACKET_ID_UNLOCK,
                Cmd229ItemLockRequest.PACKET_ID_UNLOCK_CANCEL,
                Cmd229ItemLockRequest.A4_LOCK,
                Cmd229ItemLockRequest.A4_UNLOCK,
                Cmd229ItemLockRequest.A4_CANCEL);
    }

    @Override
    public void handle(GameSession session, int packetId, byte[] plain) {
        Cmd229ItemLockRequest req;
        try {
            req = Cmd229ItemLockRequest.parse(plain);
        } catch (RuntimeException e) {
            session.fail("CMD " + packetId + " 物品锁定包体不合法（"
                    + (plain == null ? "null" : plain.length + " 字节") + "）", e);
            return;
        }
        if (!session.hasCharacter()) {
            return;
        }
        int op = packetId;
        if (ServerConfig.A4) {
            if (packetId == Cmd229ItemLockRequest.A4_LOCK) {
                op = Cmd229ItemLockRequest.PACKET_ID_LOCK;
            } else if (packetId == Cmd229ItemLockRequest.A4_UNLOCK) {
                op = Cmd229ItemLockRequest.PACKET_ID_UNLOCK;
            } else if (packetId == Cmd229ItemLockRequest.A4_CANCEL) {
                op = Cmd229ItemLockRequest.PACKET_ID_UNLOCK_CANCEL;
            }
        }
        ItemLockService locks = session.itemLock();
        switch (op) {
            case Cmd229ItemLockRequest.PACKET_ID_LOCK -> {
                Cmd229ReplyBody reply = locks.lock(req.itemSpace(), req.slot());
                byte[] body = reply.toBytes();
                session.reply(packetId, body);
            }
            case Cmd229ItemLockRequest.PACKET_ID_UNLOCK -> {
                Cmd230ReplyBody reply = locks.unlock(req.itemSpace(), req.slot());
                byte[] body = reply.toBytes();
                session.reply(packetId, body);
            }
            case Cmd229ItemLockRequest.PACKET_ID_UNLOCK_CANCEL -> {
                Cmd231ReplyBody reply = locks.unlockCancel(req.itemSpace(), req.slot());
                byte[] body = reply.toBytes();
                session.reply(packetId, body);
            }
            default -> throw new IllegalStateException("ItemLockHandler 收到没声明的号 " + packetId);
        }
        session.flush();
    }
}
