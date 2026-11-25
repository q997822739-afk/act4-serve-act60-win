package com.dylan.store;

import javax.sql.DataSource;

public final class EmotionStore extends DomainStore {

    EmotionStore(DataSource dataSource) {
        super(dataSource);
    }

    public int loadEmotion(int characNo) {
        return oneInt("SELECT emotion FROM charac_stat WHERE charac_no = ?", characNo,
                "读心情失败");
    }

    public void saveEmotion(int characNo, int emotion) {
        setOneInt("UPDATE charac_stat SET emotion = ? WHERE charac_no = ?", emotion, characNo,
                "写心情失败");
    }
}
