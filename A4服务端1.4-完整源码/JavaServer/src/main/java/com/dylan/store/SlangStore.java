package com.dylan.store;

import javax.sql.DataSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class SlangStore extends DomainStore {

    private static final int SLANG_MAX = 153;

    static final String SEED_SLANG = "/slang/slang_list.txt";

    static final String SEED_SLANG_NAME = "/slang/slang_list_name.txt";

    SlangStore(DataSource dataSource) {
        super(dataSource);
    }

    public List<String> loadSlangList() {
        return loadAll("SELECT slang FROM slang_list", "读 slang_list");
    }

    public List<String> loadSlangListName() {
        return loadAll("SELECT slang FROM slang_list_name", "读 slang_list_name");
    }

    private List<String> loadAll(String sql, String what) {
        List<String> out = new ArrayList<>();
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String slang = rs.getString(1);
                if (slang != null && !slang.isEmpty()) {
                    out.add(slang);
                }
            }
        } catch (SQLException e) {
            throw new StoreException(what + " 失败：" + e.getMessage(), e);
        }
        return out;
    }

    static void seedSlangList(Connection c) throws SQLException {
        seed(c, "slang_list", SEED_SLANG);
    }

    static void seedSlangListName(Connection c) throws SQLException {
        seed(c, "slang_list_name", SEED_SLANG_NAME);
    }

    private static void seed(Connection c, String table, String resource) throws SQLException {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
            if (rs.next() && rs.getInt(1) > 0) {
                return;
            }
        }
        List<String> words = readSeed(resource);
        if (words.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT OR IGNORE INTO " + table + " (slang) VALUES (?)")) {
            for (String word : words) {
                ps.setString(1, fit(word, SLANG_MAX));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    static List<String> readSeed(String resource) {
        List<String> out = new ArrayList<>();
        try (InputStream in = SlangStore.class.getResourceAsStream(resource)) {
            if (in == null) {
                return out;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty() || line.startsWith("# ")) {
                        continue;
                    }
                    out.add(line);
                }
            }
        } catch (IOException e) {
        }
        return out;
    }
}
