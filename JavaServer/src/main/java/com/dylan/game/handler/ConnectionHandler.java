package com.dylan.game.handler;

import com.dylan.game.CharacterService;
import com.dylan.game.CommandHandler;
import com.dylan.game.GameSession;
import com.dylan.net.ServerConfig;
import com.dylan.protocol.body.Cmd0CheckConnectionRequest;
import com.dylan.protocol.body.Cmd1LoginRequest;
import com.dylan.protocol.body.Cmd1ReplyBody;
import com.dylan.protocol.body.Cmd3ReplyBody;
import com.dylan.protocol.body.Cmd8GetUserInfoRequest;

import java.util.Set;

public final class ConnectionHandler implements CommandHandler {

    public static final int CMD_GET_USERINFO = Cmd8GetUserInfoRequest.PACKET_ID;

    @Override
    public Set<Integer> handles() {
        return Set.of(
                Cmd0CheckConnectionRequest.PACKET_ID,
                Cmd1ReplyBody.PACKET_ID,
                CMD_GET_USERINFO,
                Cmd3ReplyBody.PACKET_ID);
    }

    @Override
    public void handle(GameSession session, int packetId, byte[] plain) {
        switch (packetId) {
            case Cmd0CheckConnectionRequest.PACKET_ID -> checkConnection(session, plain);
            case Cmd1ReplyBody.PACKET_ID -> login(session, plain);
            case CMD_GET_USERINFO -> userInfo(session, plain);
            case Cmd3ReplyBody.PACKET_ID -> exit(session);
            default -> throw new IllegalStateException("不该路由到这里：" + packetId);
        }
    }

    private static void checkConnection(GameSession s, byte[] plain) {
        s.connectionCheck().onReply();

        Cmd0CheckConnectionRequest request = Cmd0CheckConnectionRequest.parse(plain);
        if (request == null) {
            s.noteHeartbeatReply(null);
            return;
        }

        s.silenceMs();
        int before = s.checksumChangesWithoutAction();
        s.noteHeartbeatReply(request);

        if (s.checksumChangesWithoutAction() > before) {
            return;
        }
    }

    private static void userInfo(GameSession session, byte[] plain) {
        Cmd8GetUserInfoRequest request = Cmd8GetUserInfoRequest.parse(plain);
        if (request.wantsCharacterList()) {
            CharacterService.sendCharacterList(session, "应 CMD 8 GET_USERINFO");
            return;
        }
        if (!request.wantsOtherUser()) {
            return;
        }
        CharacterService.sendOtherUserInfo(session, request);
    }

    private static void login(GameSession s, byte[] plain) {
        if (s.loginAnswered()) {
            return;
        }
        s.loginAnswered(true);

        Cmd1LoginRequest request = parseRequest(s, plain);
        if (request == null && ServerConfig.LOGIN_TOKEN_MODE && s.accounts() != null) {
            reject(s, ServerConfig.LOGIN_REJECT_CODE_AUTH);
            return;
        }
        String tokenDigest = null;
        if (request != null && s.accounts() != null) {
            var outcome = s.accounts().authenticateFromGame(
                    request.account(), request.password(), ServerConfig.DB_AUTO_CREATE_ACCOUNT);
            if (allowsHandoff(s)) {
                String digest = ChannelHandoffs.digest(request.password());
                if (outcome.gatewayToken()) {
                    ChannelHandoffs.LIVE.revoke(request.account());
                    tokenDigest = digest;
                } else if (!outcome.ok()
                        && ChannelHandoffs.LIVE.consume(request.account(), digest, s.clientIp())) {
                    outcome = s.accounts().completeTokenLogin(request.account());
                    if (outcome.ok()) {
                        tokenDigest = digest;
                    }
                }
            }
            if (!outcome.ok()) {
                System.out.println("[登录] 鉴权失败，频道端口 " + s.localPort()
                        + "，原因：" + outcome.message());
                reject(s, ServerConfig.LOGIN_REJECT_CODE_AUTH);
                return;
            }
        }
        if (request != null) {
            s.loginUserId(request.account());
        }

        CharacterService.resolveAccount(s);

        if (CharacterService.kickDuplicateLogin(s)) {
            return;
        }

        s.gatewayTokenDigest(tokenDigest);
        CharacterService.sendLoginReply(s);

        s.connectionCheck().start();
    }

    private static Cmd1LoginRequest parseRequest(GameSession s, byte[] plain) {
        if (!ServerConfig.LOGIN_AUTH) {
            return null;
        }
        if (plain == null || plain.length == 0) {
            return null;
        }
        try {
            return Cmd1LoginRequest.parse(plain);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean allowsHandoff(GameSession s) {
        return ServerConfig.A4 && ServerConfig.LOGIN_TOKEN_MODE
                && (s.localPort() == ServerConfig.PORT
                    || ServerConfig.A4_CHANNEL_PORTS.containsValue(s.localPort()));
    }

    private static void reject(GameSession s, int code) {
        byte[] body = Cmd1ReplyBody.rejected(code);
        s.replyNow(Cmd1ReplyBody.PACKET_ID, body);
    }

    private static void exit(GameSession s) {
        if (allowsHandoff(s) && s.account() != null && s.hasCharacter()) {
            ChannelHandoffs.LIVE.offer(s.loginUserId(), s.takeGatewayTokenDigest(), s.clientIp());
        }
        byte[] body = Cmd3ReplyBody.success();
        s.replyThenClose(Cmd3ReplyBody.PACKET_ID, body);
    }
}
