package com.dylan.protocol.body;

public enum MailError {

    UNKNOWN_ITEM(1),

    NO_SUCH_RECEIVER(3),

    INVENTORY_FULL(4),

    NOT_ENOUGH_GOLD(10),

    GOLD_OVER_LEVEL_LIMIT(14),

    CANNOT_SEND_ITEM(17),

    BAD_LETTER_STAT(19),

    NO_SUCH_MAIL(21),

    BUSY_OR_FULL(22),

    ITEM_NOT_PACKAGABLE(23),

    RECEIVER_DELETED(24),

    SENDER_BLACKLISTED(77),

    RECEIVER_IS_GM(90);

    private final int code;

    MailError(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
