package com.dylan.store;

public record CharacterRow(
        int characNo,
        String characName,
        int job,
        int lev,
        int growType,
        int sex,
        long guildId) {

    public CharacterRow withLev(int lev) {
        return lev == this.lev ? this
                : new CharacterRow(characNo, characName, job, lev, growType, sex, guildId);
    }
}
