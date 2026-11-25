package com.dylan.game.handler;

import com.dylan.game.CommandHandler;
import com.dylan.game.GameSession;
import com.dylan.game.Party;
import com.dylan.game.SkillService;
import com.dylan.game.StatService;
import com.dylan.protocol.body.Cmd30ChangeSkillSlotRequest;
import com.dylan.protocol.body.Cmd30ReplyBody;
import com.dylan.protocol.body.Cmd31BuySkillRequest;
import com.dylan.protocol.body.Cmd31ReplyBody;
import com.dylan.protocol.body.Cmd41UseSkillRequest;

import java.util.Set;

public final class SkillHandler implements CommandHandler {

    @Override
    public Set<Integer> handles() {
        return Set.of(Cmd30ChangeSkillSlotRequest.PACKET_ID,
                Cmd31BuySkillRequest.PACKET_ID,
                Cmd41UseSkillRequest.PACKET_ID);
    }

    @Override
    public void handle(GameSession session, int packetId, byte[] plain) {
        switch (packetId) {
            case Cmd30ChangeSkillSlotRequest.PACKET_ID -> changeSkillSlot(session, plain);
            case Cmd31BuySkillRequest.PACKET_ID -> buySkill(session, plain);
            case Cmd41UseSkillRequest.PACKET_ID -> useSkill(session, plain);
            default -> throw new IllegalStateException("不该路由到这里：CMD " + packetId);
        }
    }

    private void useSkill(GameSession session, byte[] plain) {
        Cmd41UseSkillRequest request = Cmd41UseSkillRequest.parse(plain);

        // A4 官方每次 CMD 41 都回 1 字节 01（scen_off / replay_off：tut CMD 41、副本里每次放技能）。
        // 客户端读包表里 41 没有专属分支（no-handler），但回执仍按结果字节派发；不回时技能 UI 等确认。
        session.reply(Cmd41UseSkillRequest.PACKET_ID, new byte[]{1});

        if (!session.inDungeon()) {
            return;
        }

        var row = session.character();
        int job = row == null ? 0 : row.job();
        int packed = row == null ? 0 : Math.max(0, row.growType());
        int firstGrow = StatService.firstGrow(packed);
        int secondGrow = StatService.secondGrow(packed);

        if (!session.skills().isValidUsingSkill(job, firstGrow, secondGrow, request.skillId())) {
            session.hack().count(com.dylan.game.HackType.SKILL_NOT_LEARNED,
                    request.skillId(), job,
                    "技能 " + request.skillId() + " 配不上职业 " + job
                            + " 的一转分支 " + firstGrow + " / 觉醒 " + secondGrow);
        }

        Party party = session.party().current();
        if (party == null) {
            return;
        }
        party.recordSkillUse(session, job, request.skillId());
        party.slotOf(session);
    }

    private void buySkill(GameSession session, byte[] plain) {
        Cmd31BuySkillRequest req;
        try {
            req = Cmd31BuySkillRequest.parse(plain);
        } catch (RuntimeException e) {
            session.fail("CMD " + Cmd31BuySkillRequest.PACKET_ID
                    + " BUY_SKILL 包体不合法（" + plain.length + " 字节）", e);
            return;
        }

        if (!session.hasCharacter()) {
            session.reply(Cmd31ReplyBody.PACKET_ID,
                    Cmd31ReplyBody.rejected(SkillService.wireError(
                            SkillService.BUY_ERROR_BAD_DATA)));
            return;
        }

        SkillService.BuyOutcome outcome = session.skills().masterNewSkill(req.skillId());
        if (!outcome.ok()) {
            int wire = SkillService.wireError(outcome.error());
            session.reply(Cmd31ReplyBody.PACKET_ID, Cmd31ReplyBody.rejected(wire));
            return;
        }
        session.reply(Cmd31ReplyBody.PACKET_ID, Cmd31ReplyBody.accepted(
                session.skills().remainSp(), outcome.slot(),
                outcome.skillId(), outcome.newLevel()));
    }

    private void changeSkillSlot(GameSession session, byte[] plain) {
        Cmd30ChangeSkillSlotRequest req;
        try {
            req = Cmd30ChangeSkillSlotRequest.parse(plain);
        } catch (RuntimeException e) {
            session.fail("CMD " + Cmd30ChangeSkillSlotRequest.PACKET_ID
                    + " CHANGE_SKILLSLOT 包体不合法（" + plain.length + " 字节）", e);
            return;
        }

        if (!session.hasCharacter()) {
            session.reply(Cmd30ReplyBody.PACKET_ID,
                    Cmd30ReplyBody.rejected(SkillService.ERROR_CANNOT_MOVE));
            return;
        }

        SkillService.MoveOutcome outcome = session.skills().moveSlot(req.slotA(), req.slotB());
        if (!outcome.ok()) {
            session.reply(Cmd30ReplyBody.PACKET_ID,
                    Cmd30ReplyBody.rejected(outcome.errorCode()));
            return;
        }
        session.reply(Cmd30ReplyBody.PACKET_ID,
                Cmd30ReplyBody.accepted(req.slotA(), req.slotB()));
    }
}
