package com.dylan.account;

public final class AccountRules {
    private AccountRules() {
    }

    /** A4 启动行和官方日志里账号是 "1"，官方成品服照收。ACT5 的 4 位下限会把它拒掉（CMD 1 回 00 02）。 */
    public static final int USER_ID_MIN = 1;

    public static final int USER_ID_MAX = 10;

    private static boolean isUserIdChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9') || c == '_';
    }

    public static String checkUserId(String userId) {
        if (userId == null || userId.isEmpty()) {
            return "请输入账号";
        }
        if (userId.length() < USER_ID_MIN || userId.length() > USER_ID_MAX) {
            return "账号长度需要在 " + USER_ID_MIN + " 到 " + USER_ID_MAX + " 位之间";
        }
        for (int i = 0; i < userId.length(); i++) {
            if (!isUserIdChar(userId.charAt(i))) {
                return "账号只能包含字母、数字和下划线";
            }
        }
        return null;
    }
}
