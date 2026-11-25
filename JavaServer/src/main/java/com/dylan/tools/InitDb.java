package com.dylan.tools;

import com.dylan.net.ServerConfig;
import com.dylan.store.GameStore;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class InitDb {

    private InitDb() {
    }

    private static final String DEFAULT_ACCOUNT = "10038";

    private static final String DEFAULT_PASSWORD = "123456";

    static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        String userId = args.length > 0 ? args[0] : DEFAULT_ACCOUNT;
        String password = args.length > 1 ? args[1] : DEFAULT_PASSWORD;

        String url = ServerConfig.DB_URL;
        out.println("库文件：" + url);

        try (GameStore store = GameStore.open(url)) {
            var account = store.account().findOrCreateAccount(userId, password);
            out.println("账号：" + account.userId() + "（m_id=" + account.mId()
                    + "，角色槽 " + account.slot() + "）");

            store.gm().setGmLevel(account.mId(), 1);
            out.println("GM：已把 m_id=" + account.mId()
                    + " 写进 gm_manifest（level 1）。游戏里先敲 #ehdgusrlwn 开 GM 模式。");
        }

        Path file = fileOf(url);
        if (file != null && Files.exists(file)) {
            out.println("大小：" + Files.size(file) + " 字节");
            out.println();
            out.println("把它连同 .db-wal / .db-shm（如果有）一起放进仓库的 data/ 下。");
        }
    }

    private static Path fileOf(String url) {
        String prefix = "jdbc:sqlite:";
        if (!url.startsWith(prefix) || url.startsWith(prefix + ":memory:")) {
            return null;
        }
        return Path.of(url.substring(prefix.length()).replace('\\', '/'));
    }
}
