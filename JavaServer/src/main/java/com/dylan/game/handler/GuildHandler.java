package com.dylan.game.handler;

import com.dylan.game.CommandHandler;
import com.dylan.game.GameSession;
import com.dylan.game.GuildService;
import com.dylan.net.ServerConfig;

import java.util.Set;

/**
 * A4 公会命令（官方枚举 opcodes_cmd.txt：70 / 71 / 76 / 150 / 164–168 / 194；邀请 162 / 163 来自客户端发包点）。
 * 169（GuildCreateRight）空包不在这里注册：A5 的 169 是死亡之塔，重复注册会让路由起不来。
 * 空包由 DungeonHandler 直接收下不回。见 07_协议检修/社交/公会.md。
 */
public final class GuildHandler implements CommandHandler {

    @Override
    public Set<Integer> handles() {
        return ServerConfig.A4 ? Set.of(70, 71, 76, 150, 162, 163, 164, 165, 166, 167, 168, 194) : Set.of();
    }

    @Override
    public void handle(GameSession s, int packetId, byte[] plain) {
        if (!s.hasCharacter()) {
            return;
        }
        switch (packetId) {
            case 70, 150 -> GuildService.info(s, packetId);
            case 71 -> GuildService.create(s);
            case 76 -> GuildService.levelUp(s);
            case 162 -> GuildService.invite(s, plain);
            case 163 -> GuildService.answer(s, plain);
            case 164 -> GuildService.secede(s, plain);
            case 165 -> GuildService.setNotice(s, plain);
            case 166 -> GuildService.appoint(s, plain);
            case 167 -> GuildService.checkName(s, plain);
            case 168 -> GuildService.checkDomain(s, plain);
            case 194 -> GuildService.dismiss(s);
            default -> throw new IllegalStateException("不该路由到这里：CMD " + packetId);
        }
    }
}
