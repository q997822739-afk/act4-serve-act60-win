package com.dylan.pvf.script;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.dylan.pvf.PvfArchive;
import com.dylan.pvf.PvfFormatException;

public final class EmotionTable {
    public static final String PATH = "etc/emotionexpressiondata.etc";

    public static final String SECTION = "EMOTION INFO";

    public static final String END = "$END";

    public static final int TYPE_NORMAL = 1;

    private static final Charset CHARSET = Charset.forName("GBK");

    public record Emotion(int id, int type, String name, List<String> commands) {
        public Emotion {
            commands = List.copyOf(commands);
        }
    }

    private final Map<Integer, Emotion> byId;

    private EmotionTable(Map<Integer, Emotion> byId) {
        this.byId = byId;
    }

    public static EmotionTable load(PvfArchive archive) {
        return parse(new String(archive.read(PATH), CHARSET));
    }

    public static EmotionTable parse(String text) {
        List<ScriptReader.Token> tokens = ScriptReader.tokenize(text);
        Map<Integer, Emotion> out = new LinkedHashMap<>();

        int i = 0;
        while (i < tokens.size() && !tokens.get(i).isSection(SECTION)) {
            i++;
        }
        if (i >= tokens.size()) {
            throw new PvfFormatException(PATH + " 里没有 [" + SECTION + "] 段");
        }
        i++;

        while (i < tokens.size()) {
            ScriptReader.Token token = tokens.get(i);
            if (token.kind() == ScriptReader.Kind.SECTION
                    || token.kind() == ScriptReader.Kind.SECTION_END) {
                break;
            }
            i = readOne(tokens, i, out);
        }

        if (out.isEmpty()) {
            throw new PvfFormatException(PATH + " 的 [" + SECTION + "] 段里一条记录都没有");
        }
        return new EmotionTable(out);
    }

    private static int readOne(List<ScriptReader.Token> tokens, int i,
                               Map<Integer, Emotion> out) {
        int id = tokens.get(i).asInt();
        i = need(tokens, i + 1, "索引 " + id + " 后面的 type");
        int type = tokens.get(i).asInt();
        i++;

        i = needString(tokens, i, "索引 " + id + " 的第一个 ani 路径");
        i++;
        i = needString(tokens, i, "索引 " + id + " 的第二个 ani 路径");
        i++;

        while (i < tokens.size() && tokens.get(i).kind() == ScriptReader.Kind.WORD) {
            i++;
        }

        i = needString(tokens, i, "索引 " + id + " 的表情名");
        String name = tokens.get(i).text();
        i++;

        List<String> commands = new ArrayList<>();
        boolean closed = false;
        while (i < tokens.size()) {
            ScriptReader.Token t = tokens.get(i);
            if (t.kind() != ScriptReader.Kind.STRING) {
                break;
            }
            i++;
            if (END.equals(t.text())) {
                closed = true;
                break;
            }
            commands.add(t.text());
        }
        if (!closed) {
            throw new PvfFormatException(PATH + " 索引 " + id + " 那一条没有以 " + END + " 收尾");
        }

        Emotion previous = out.put(id, new Emotion(id, type, name, commands));
        if (previous != null) {
            throw new PvfFormatException(PATH + " 里索引 " + id + " 出现了两次");
        }
        return i;
    }

    private static int need(List<ScriptReader.Token> tokens, int i, String what) {
        if (i >= tokens.size()) {
            throw new PvfFormatException(PATH + " 在" + what + "处就到文件尾了");
        }
        return i;
    }

    private static int needString(List<ScriptReader.Token> tokens, int i, String what) {
        need(tokens, i, what);
        if (tokens.get(i).kind() != ScriptReader.Kind.STRING) {
            throw new PvfFormatException(PATH + " 的" + what + "不是一个反引号字符串，读到 \""
                    + tokens.get(i).text() + "\"");
        }
        return i;
    }

    public boolean isValid(int emotion) {
        return byId.containsKey(emotion);
    }

    public Emotion get(int emotion) {
        return byId.get(emotion);
    }

    public int size() {
        return byId.size();
    }

    public List<Emotion> all() {
        return List.copyOf(byId.values());
    }

    @Override
    public String toString() {
        return byId.size() + " 个表情：" + byId.values().stream()
                .map(e -> e.id() + "=" + e.name()).toList();
    }
}
