package com.dylan.game.handler;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** One short-lived authorization after exiting an authenticated A4 channel via CMD 3. */
final class ChannelHandoffs {
    private record Grant(String digest, String peer, long expiresAt) {}

    static final ChannelHandoffs LIVE = new ChannelHandoffs(Duration.ofSeconds(25), System::nanoTime);

    static {
        var cleanup = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "channel-handoff-cleanup");
            thread.setDaemon(true);
            return thread;
        });
        cleanup.scheduleWithFixedDelay(LIVE::purge, 30, 30, TimeUnit.SECONDS);
    }

    private final ConcurrentHashMap<String, Grant> grants = new ConcurrentHashMap<>();
    private final long ttlNanos;
    private final LongSupplier clock;

    ChannelHandoffs(Duration ttl, LongSupplier clock) {
        this.ttlNanos = ttl.toNanos();
        this.clock = clock;
    }

    void offer(String user, String digest, String peer) {
        if (digest != null && !peer.isBlank()) {
            grants.put(user, new Grant(digest, peer, clock.getAsLong() + ttlNanos));
        }
    }

    void revoke(String user) {
        grants.remove(user);
    }

    boolean consume(String user, String digest, String peer) {
        Grant grant = grants.get(user);
        return grant != null && grant.expiresAt() - clock.getAsLong() > 0
                && grant.peer().equals(peer)
                && MessageDigest.isEqual(grant.digest().getBytes(StandardCharsets.US_ASCII),
                        digest.getBytes(StandardCharsets.US_ASCII))
                && grants.remove(user, grant);
    }

    private void purge() {
        long now = clock.getAsLong();
        grants.values().removeIf(grant -> grant.expiresAt() - now <= 0);
    }

    static String digest(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
