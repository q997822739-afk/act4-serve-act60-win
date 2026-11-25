package com.dylan.store;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class ExpertJobStore extends DomainStore {
    ExpertJobStore(DataSource dataSource) {
        super(dataSource);
    }

    public CharacExpertJobRow loadExpertJob(int characNo) {
        String sql = "SELECT expert_job_giveup_cnt, expert_job_info, expert_job_info_ex, recipe"
                + " FROM charac_expert_job WHERE charac_no = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, characNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return CharacExpertJobRow.fresh(characNo);
                }
                return new CharacExpertJobRow(characNo, rs.getInt(1), rs.getInt(2), rs.getInt(3),
                        RecipeBitmap.decode(rs.getBytes(4)));
            }
        } catch (SQLException e) {
            throw new StoreException("读副职业存档失败：" + e.getMessage(), e);
        }
    }

    public void saveExpertJob(CharacExpertJobRow row) {
        String sql = "INSERT INTO charac_expert_job(charac_no, expert_job_giveup_cnt,"
                + " expert_job_info, expert_job_info_ex, recipe) VALUES(?, ?, ?, ?, ?)"
                + " ON CONFLICT(charac_no) DO UPDATE SET"
                + " expert_job_giveup_cnt = excluded.expert_job_giveup_cnt,"
                + " expert_job_info = excluded.expert_job_info,"
                + " expert_job_info_ex = excluded.expert_job_info_ex,"
                + " recipe = excluded.recipe";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, row.characNo());
            ps.setInt(2, row.giveupCount());
            ps.setInt(3, row.info());
            ps.setInt(4, row.infoEx());
            ps.setBytes(5, row.recipes().encode());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("写副职业存档失败：" + e.getMessage(), e);
        }
    }

    public int loadExpertJobType(int characNo) {
        return oneInt("SELECT expert_job FROM charac_info WHERE charac_no = ?", characNo,
                "读副职业类型失败");
    }

    public void saveExpertJobType(int characNo, int type) {
        setOneInt("UPDATE charac_info SET expert_job = ? WHERE charac_no = ?", type, characNo,
                "写副职业类型失败");
    }

    public int loadExpertJobExp(int characNo) {
        return oneInt("SELECT expert_job_exp FROM charac_stat WHERE charac_no = ?", characNo,
                "读副职业熟练度失败");
    }

    public void saveExpertJobExp(int characNo, int exp) {
        setOneInt("UPDATE charac_stat SET expert_job_exp = ? WHERE charac_no = ?", exp, characNo,
                "写副职业熟练度失败");
    }
}
