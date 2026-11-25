package com.dylan.store;

import com.dylan.store.blob.SkillSlots;

public record CharacterSkills(
        int characNo,
        long remainSp,
        SkillSlots primary,
        SkillSlots secondary) {

    public CharacterSkills {
        if (primary == null || secondary == null) {
            throw new IllegalArgumentException("两套技能表都不能为 null");
        }
    }

    public static CharacterSkills empty(int characNo) {
        return new CharacterSkills(characNo, 0, SkillSlots.empty(), SkillSlots.empty());
    }
}
