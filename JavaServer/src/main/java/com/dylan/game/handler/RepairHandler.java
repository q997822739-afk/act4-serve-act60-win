package com.dylan.game.handler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.dylan.game.CommandHandler;
import com.dylan.game.GameSession;
import com.dylan.game.RepairService;
import com.dylan.protocol.body.Cmd25RepairEquipRequest;
import com.dylan.protocol.body.Cmd25ReplyBody;
import com.dylan.protocol.body.ItemListType;

public final class RepairHandler implements CommandHandler {
    @Override
    public Set<Integer> handles() {
        return Set.of(Cmd25RepairEquipRequest.PACKET_ID);
    }

    @Override
    public void handle(GameSession session, int packetId, byte[] plain) {
        Cmd25RepairEquipRequest req;
        try {
            req = Cmd25RepairEquipRequest.parse(plain);
        } catch (RuntimeException e) {
            session.fail("CMD " + Cmd25RepairEquipRequest.PACKET_ID + " REPAIR_EQUIPMENT 包体不合法（"
                    + plain.length + " 字节）", e);
            return;
        }

        RepairService.Outcome result = RepairService.repair(session, req);
        if (!result.ok()) {
            session.reply(Cmd25ReplyBody.PACKET_ID, Cmd25ReplyBody.rejected(result.error()));
            return;
        }

        session.inventory().save();

        sendUpdates(session, req, result.targets());

        byte[] body = Cmd25ReplyBody.repaired(result.gold(), req.itemSpace(), req.slot());
        session.reply(Cmd25ReplyBody.PACKET_ID, body);
    }

    private static void sendUpdates(GameSession session, Cmd25RepairEquipRequest req,
                                    List<RepairService.Target> targets) {
        if (req.isRepairAll()) {
            int first = RepairService.REPAIR_ALL_EQUIP_FIRST;
            int last = RepairService.REPAIR_ALL_EQUIP_LAST;
            int[] slots = new int[last - first + 1];
            for (int i = 0; i < slots.length; i++) {
                slots[i] = first + i;
            }
            session.inventory().sendItemUpdate(ItemListType.LIST_EQUIPPED,
                    "全部修理：照官方无条件重发装备栏 " + first + ".." + last, slots);
            sendChanged(session, targets, ItemListType.LIST_MAIN);
            return;
        }
        sendChanged(session, targets, -1);
    }

    private static void sendChanged(GameSession session, List<RepairService.Target> targets,
                                    int onlySpace) {
        Map<Integer, List<Integer>> bySpace = new LinkedHashMap<>();
        for (var t : targets) {
            if (onlySpace >= 0 && t.itemSpace() != onlySpace) {
                continue;
            }
            bySpace.computeIfAbsent(t.itemSpace(), k -> new ArrayList<>()).add(t.slot());
        }
        for (var entry : bySpace.entrySet()) {
            int[] slots = entry.getValue().stream().mapToInt(Integer::intValue).toArray();
            session.inventory().sendItemUpdate(entry.getKey(), "修理", slots);
        }
    }
}
