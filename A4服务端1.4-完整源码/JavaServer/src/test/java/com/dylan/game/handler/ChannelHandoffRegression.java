package com.dylan.game.handler;

import com.dylan.account.AccountService;
import com.dylan.game.GameSession;
import com.dylan.net.OutboundCmdReply;
import com.dylan.protocol.body.Cmd1LoginRequest;
import com.dylan.store.GameStore;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/** Standalone regression check; run main on the shaded JAR classpath with token mode enabled. */
public final class ChannelHandoffRegression {
    private static final ConnectionHandler HANDLER = new ConnectionHandler();
    private static final String USER = "handoff1";
    private static final String TOKEN = "SyntheticTestToken";

    public static void main(String[] args) throws Exception {
        check(System.getProperty("s1a5.loginMode", "").equals("token"), "requires token mode");
        AtomicLong clock = new AtomicLong();
        var handoffs = new ChannelHandoffs(Duration.ofSeconds(25), clock::get);
        String digest = ChannelHandoffs.digest(TOKEN);
        handoffs.offer(USER, digest, "127.0.0.1");
        check(!handoffs.consume(USER, digest, "127.0.0.2"), "other peer");
        check(!handoffs.consume("other_user", digest, "127.0.0.1"), "other account");
        check(!handoffs.consume(USER, ChannelHandoffs.digest("bad"), "127.0.0.1"), "wrong token");
        check(handoffs.consume(USER, digest, "127.0.0.1"), "valid grant");
        check(!handoffs.consume(USER, digest, "127.0.0.1"), "grant is single-use");
        handoffs.offer(USER, digest, "127.0.0.1");
        clock.set(Duration.ofSeconds(25).toNanos());
        check(!handoffs.consume(USER, digest, "127.0.0.1"), "expired grant");

        var db = Files.createTempFile("a4-handoff-", ".db");
        String url = "jdbc:sqlite:" + db.toAbsolutePath();
        try (GameStore store = GameStore.open(url)) {
            long accountId = store.account().createAccount(USER, "NotTheToken");
            try (var c = DriverManager.getConnection(url); var s = c.createStatement()) {
                s.execute("CREATE TABLE gw_login_token (token TEXT PRIMARY KEY, user_id TEXT, expire_at INTEGER)");
                try (var ps = c.prepareStatement("INSERT INTO gw_login_token VALUES (?, ?, ?)")) {
                    ps.setString(1, TOKEN);
                    ps.setString(2, USER);
                    ps.setLong(3, System.currentTimeMillis() / 1000 + 60);
                    ps.executeUpdate();
                }
            }

            try (var normal = new TestConnection(store, 7002, "127.0.0.1")) {
                check(normal.login(TOKEN) && normal.session.account().mId() == accountId, "initial login");
                try (var arena = new TestConnection(store, 7004, "127.0.0.1")) {
                    check(!arena.login(TOKEN), "cannot replay without CMD 3");
                }
                normal.exit();
            }
            try (var wrongPeer = new TestConnection(store, 7004, "127.0.0.2")) {
                check(!wrongPeer.login(TOKEN), "wrong peer cannot use handoff");
            }
            try (var wrongToken = new TestConnection(store, 7004, "127.0.0.1")) {
                check(!wrongToken.login("WrongToken"), "invalid credential cannot use handoff");
            }
            try (var arena = new TestConnection(store, 7004, "127.0.0.1")) {
                check(arena.login(TOKEN) && arena.session.account().mId() == accountId
                        && arena.session.channelKind() == 4, "arena login keeps account and gets PvP kind");
                arena.exit();
            }
            try (var normal = new TestConnection(store, 7002, "127.0.0.1")) {
                check(normal.login(TOKEN), "arena to normal handoff");
            }
            try (var noExit = new TestConnection(store, 7004, "127.0.0.1")) {
                check(!noExit.login(TOKEN), "abrupt disconnect does not issue handoff");
            }
        } finally {
            Files.deleteIfExists(db);
        }
        System.out.println("PASS: initial login, 7002 -> 7004 -> 7002, invalid/expired/replayed grants");
    }

    private static void check(boolean condition, String what) {
        if (!condition) throw new AssertionError(what);
    }

    private static byte[] loginBody(String token) {
        byte[] user = USER.getBytes(StandardCharsets.UTF_8);
        byte[] password = token.getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(17 + user.length + password.length).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(user.length).put(user).putInt(password.length).put(password)
                .put((byte) 0).putInt(0).putInt(Cmd1LoginRequest.EXPECTED_VERSION).array();
    }

    private static final class TestConnection implements AutoCloseable {
        private final EmbeddedChannel channel;
        private final GameSession session;

        TestConnection(GameStore store, int port, String peer) {
            var adapter = new ChannelInboundHandlerAdapter();
            channel = new EmbeddedChannel(adapter) {
                @Override protected SocketAddress localAddress0() { return new InetSocketAddress("127.0.0.1", port); }
                @Override protected SocketAddress remoteAddress0() { return new InetSocketAddress(peer, 59000); }
            };
            session = new GameSession(channel.pipeline().context(adapter), store, null, new AccountService(store));
        }

        boolean login(String token) {
            HANDLER.handle(session, 1, loginBody(token));
            OutboundCmdReply reply = channel.readOutbound();
            check(reply != null && reply.packetId() == 1, "expected CMD 1 reply");
            return session.account() != null;
        }

        void exit() {
            session.characNo(42);
            HANDLER.handle(session, 3, new byte[0]);
        }

        @Override public void close() { channel.finishAndReleaseAll(); }
    }
}
