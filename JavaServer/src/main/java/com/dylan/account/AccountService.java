package com.dylan.account;

import com.dylan.store.AccountRow;
import com.dylan.store.AccountStore;
import com.dylan.store.GameStore;

import java.util.Optional;

public final class AccountService {

    private final GameStore store;

    public AccountService(GameStore store) {
        this.store = store;
    }

    public AuthOutcome authenticateFromGame(String userId, String password, boolean autoCreate) {
        String bad = AccountRules.checkUserId(userId);
        if (bad != null) {
            return AuthOutcome.fail(ApiCode.BAD_REQUEST, bad);
        }

        if (com.dylan.net.ServerConfig.LOGIN_TOKEN_MODE) {
            return authenticateByToken(userId, password);
        }

        return authenticateFromGameWithPassword(userId, password, autoCreate);
    }

    /** 原来那套：CMD 1 的密码位就是密码，直接比对 member_info.passwd。 */
    private AuthOutcome authenticateFromGameWithPassword(String userId, String password,
                                                         boolean autoCreate) {
        Optional<AccountStore.Credential> found = store.account().findCredential(userId);
        if (found.isEmpty()) {
            if (!autoCreate) {
                return AuthOutcome.fail(ApiCode.BAD_CREDENTIAL, "账号或密码错误");
            }
            store.account().createAccount(userId, password);
            AccountRow created = store.account().findAccount(userId).orElseThrow(
                    () -> new IllegalStateException("刚建的账号 " + userId + " 查不回来"));
            return AuthOutcome.ok("登录成功（新建账号）", created);
        }

        AccountStore.Credential credential = found.get();
        if (!matches(credential.password(), password)) {
            return AuthOutcome.fail(ApiCode.BAD_CREDENTIAL, "账号或密码错误");
        }
        if (credential.state() != AccountStore.ACCOUNT_STATE_NORMAL) {
            return AuthOutcome.fail(ApiCode.ACCOUNT_BANNED, "该账号已被封停");
        }

        store.account().touchLastPlayTime(userId);
        return AuthOutcome.ok("登录成功", credential.account());
    }

    /**
     * 令牌登录（商业登录器 2026-11-02）：CMD 1 的密码位装的是网关发的一次性令牌。
     * 令牌在 gw_login_token 里，命中即删除；账号本身仍然按 member_info 读，后面流程不变。
     */
    private AuthOutcome authenticateByToken(String userId, String token) {
        if (!store.account().consumeLoginToken(userId, token)) {
            if (com.dylan.net.ServerConfig.LOGIN_PASSWORD_FALLBACK) {
                // 调试期：令牌不对时还允许直接拿密码登录（老的 bat 还这么用）。
                // 正式服把 -Ds1a5.loginPasswordFallback=0 关掉，只认令牌。
                return authenticateFromGameWithPassword(userId, token, false);
            }
            return AuthOutcome.fail(ApiCode.BAD_CREDENTIAL, "登录令牌无效或已过期");
        }
        return completeTokenLogin(userId);
    }

    /** Called only after the game handler has verified and consumed a channel handoff. */
    public AuthOutcome completeTokenLogin(String userId) {
        Optional<AccountStore.Credential> found = store.account().findCredential(userId);
        if (found.isEmpty()) {
            return AuthOutcome.fail(ApiCode.BAD_CREDENTIAL, "账号不存在");
        }
        if (found.get().state() != AccountStore.ACCOUNT_STATE_NORMAL) {
            return AuthOutcome.fail(ApiCode.ACCOUNT_BANNED, "该账号已被封停");
        }
        store.account().touchLastPlayTime(userId);
        return AuthOutcome.tokenLogin(found.get().account());
    }

    private static boolean matches(String stored, String given) {
        if (stored == null || given == null) {
            return false;
        }
        if (stored.length() != given.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < stored.length(); i++) {
            diff |= stored.charAt(i) ^ given.charAt(i);
        }
        return diff == 0;
    }
}
