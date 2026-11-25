package com.dylan.game.handler;

import com.dylan.game.CommandHandler;
import com.dylan.game.GameSession;
import com.dylan.game.TradeService;
import com.dylan.net.ServerConfig;
import com.dylan.protocol.body.TradePackets;

import java.util.Set;

/**
 * CMD 10 REQUEST_PEER / 11 RESPONSE_PEER（交易和组队共用，第 3 字节 kind：1 交易、0 组队，
 * 发包点 DNF.exe 00476A12 / 0053B663）和 CMD 26 SetItemTradeState。见 07_协议检修/社交/交易.md。
 */
public final class PeerHandler implements CommandHandler {

    static final int REQUEST_PEER = 10;
    static final int RESPONSE_PEER = 11;
    static final int SET_ITEMTRADE_STATE = 26;

    @Override
    public Set<Integer> handles() {
        return ServerConfig.A4
                ? Set.of(REQUEST_PEER, RESPONSE_PEER, SET_PARTY_INFO, LEAVE_PARTY, WALKOUT, SET_ITEMTRADE_STATE,
                        PARTY_REALTIME, SET_UDP_IP_PORT)
                : Set.of();
    }

    /** CMD 2（0085F59F）。回包 0041AD38 成功时只有结果字节 1。 */
    static final int SET_UDP_IP_PORT = 2;

    /** 组队 CMD 12（008B3FC5，u8+u8）/ 13（空）/ 14（009431E3，u8 队员槽）。 */
    static final int SET_PARTY_INFO = 12;
    static final int LEAVE_PARTY = 13;
    static final int WALKOUT = 14;
    /** 组队 CMD 177（009CEA4E，空包）：要队员实时信息，见 PartyInviteService.realtime。 */
    static final int PARTY_REALTIME = 177;

    @Override
    public void handle(GameSession session, int packetId, byte[] plain) {
        switch (packetId) {
            case SET_UDP_IP_PORT -> {
                com.dylan.game.PartyInviteService.noteUdp(session, plain);
                return;
            }
            case SET_PARTY_INFO -> {
                com.dylan.game.PartyInviteService.setInfo(session);
                return;
            }
            case LEAVE_PARTY -> {
                com.dylan.game.PartyInviteService.leave(session);
                return;
            }
            case WALKOUT -> {
                com.dylan.game.PartyInviteService.kick(session, plain.length == 0 ? 0 : plain[0] & 0xFF);
                return;
            }
            case PARTY_REALTIME -> {
                com.dylan.game.PartyInviteService.realtime(session);
                return;
            }
            default -> {
            }
        }
        if (packetId == SET_ITEMTRADE_STATE) {
            TradeService.state(session, plain.length == 0 ? 0 : plain[0] & 0xFF);
            return;
        }
        TradePackets.PeerRequest req = TradePackets.PeerRequest.parse(plain);
        if (req.kind() == TradePackets.KIND_TRADE) {
            if (packetId == REQUEST_PEER) {
                TradeService.request(session, req);
            } else {
                TradeService.respond(session, req);
            }
            return;
        }
        if (req.kind() == TradePackets.KIND_PARTY || req.kind() == 2 || req.kind() == 3) {
            // 决斗频道类型 4 的邀请按钮是 kind 2（00917920：u16 目标、u8 2、u32 0）。
            // 人已经坐在房间里才由 PvpLobby 接。没入座的 kind 2/3 仍回「对方已退出」，不建城镇队伍。
            // kind 0（0053B663）坐在房间里同样算房间邀请，否则仍是城镇组队。
            if (com.dylan.game.PvpLobby.onPeer(session, packetId, req)) {
                return;
            }
            if (req.kind() == TradePackets.KIND_PARTY) {
                com.dylan.game.PartyInviteService.onPeer(session, packetId, req);
                return;
            }
        }
        session.reply(packetId, TradePackets.fail(TradePackets.E_GONE, req.kind()));
    }
}
