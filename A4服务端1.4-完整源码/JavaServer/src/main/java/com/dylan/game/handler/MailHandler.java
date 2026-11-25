package com.dylan.game.handler;

import com.dylan.game.CommandHandler;
import com.dylan.game.GameSession;
import com.dylan.game.MailService;
import com.dylan.protocol.body.Cmd144ChangeLetterStatRequest;
import com.dylan.protocol.body.Cmd144ReplyBody;
import com.dylan.protocol.body.Cmd97MailBoxSendRequest;
import com.dylan.protocol.body.Cmd97ReplyBody;
import com.dylan.protocol.body.Cmd98ExtractItemRequest;
import com.dylan.protocol.body.Cmd98ReplyBody;
import com.dylan.protocol.body.Cmd99ReplyBody;
import com.dylan.protocol.body.MailError;

import java.util.Set;

public final class MailHandler implements CommandHandler {

    @Override
    public Set<Integer> handles() {
        return Set.of(Cmd97MailBoxSendRequest.PACKET_ID,
                Cmd98ExtractItemRequest.PACKET_ID,
                Cmd99ReplyBody.PACKET_ID,
                Cmd144ChangeLetterStatRequest.PACKET_ID);
    }

    @Override
    public void handle(GameSession session, int packetId, byte[] plain) {
        switch (packetId) {
            case Cmd99ReplyBody.PACKET_ID -> open(session);
            case Cmd98ExtractItemRequest.PACKET_ID -> extract(session, plain);
            case Cmd97MailBoxSendRequest.PACKET_ID -> send(session, plain);
            case Cmd144ChangeLetterStatRequest.PACKET_ID -> changeLetterStat(session, plain);
            default -> throw new IllegalStateException("不该路由到这里：CMD " + packetId);
        }
    }

    private void open(GameSession session) {
        MailService mail = session.mail();
        int notLoaded = mail.open();
        session.reply(Cmd99ReplyBody.PACKET_ID, Cmd99ReplyBody.success(notLoaded));
        if (com.dylan.net.ServerConfig.A4) {
            // A4 官方打开邮箱：回 CMD 99 后紧跟一份 NOTI 97 全量邮件表（回放 8/8 次都有）。
            mail.sendList(com.dylan.protocol.body.Noti97MailBoxListBody.MODE_REPLACE, "打开邮箱");
        }
    }

    private void extract(GameSession session, byte[] plain) {
        Cmd98ExtractItemRequest request;
        try {
            request = Cmd98ExtractItemRequest.parse(plain);
        } catch (RuntimeException e) {
            session.reply(Cmd98ExtractItemRequest.PACKET_ID,
                    Cmd98ReplyBody.failure(MailError.NO_SUCH_MAIL));
            return;
        }

        var outcome = session.mail().extract(request.mailIdx());
        if (outcome.ok()) {
            session.reply(Cmd98ReplyBody.PACKET_ID, Cmd98ReplyBody.success(request.mailIdx()));
            return;
        }
        session.reply(Cmd98ReplyBody.PACKET_ID, Cmd98ReplyBody.failure(outcome.error()));
    }

    private void send(GameSession session, byte[] plain) {
        Cmd97MailBoxSendRequest request;
        try {
            request = Cmd97MailBoxSendRequest.parse(plain);
        } catch (RuntimeException e) {
            session.reply(Cmd97ReplyBody.PACKET_ID,
                    Cmd97ReplyBody.failure(MailError.NO_SUCH_RECEIVER));
            return;
        }

        var outcome = session.mail().send(request);
        if (outcome.ok()) {
            session.reply(Cmd97ReplyBody.PACKET_ID, Cmd97ReplyBody.success());
            return;
        }
        session.reply(Cmd97ReplyBody.PACKET_ID, Cmd97ReplyBody.failure(outcome.error()));
    }

    private void changeLetterStat(GameSession session, byte[] plain) {
        Cmd144ChangeLetterStatRequest request;
        try {
            request = Cmd144ChangeLetterStatRequest.parse(plain);
        } catch (RuntimeException e) {
            session.reply(Cmd144ReplyBody.PACKET_ID, Cmd144ReplyBody.failure());
            return;
        }

        MailError error = session.mail().changeLetterStat(request.letterId(), request.stat());
        if (error == null) {
            session.reply(Cmd144ReplyBody.PACKET_ID,
                    Cmd144ReplyBody.success(request.letterId(), request.stat()));
            return;
        }
        session.reply(Cmd144ReplyBody.PACKET_ID,
                error == MailError.NO_SUCH_MAIL
                        ? Cmd144ReplyBody.failure()
                        : Cmd144ReplyBody.failure(error));
    }
}
