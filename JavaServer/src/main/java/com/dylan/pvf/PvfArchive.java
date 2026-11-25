package com.dylan.pvf;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class PvfArchive implements Closeable {

    public static final Charset SCRIPT_CHARSET = Charset.forName("EUC-KR");

    private static final int MAX_GUID_LENGTH = 256;

    /** DNF.exe 1.0.44.1（A4）客户端 Script.pvf 的 GUID。 */
    static final String A4_GUID = "13ff9a94-0204-0410-845a-ceeffe637190";

    private static final byte[] SHIELD_MAGIC = {'A', '5', 'P', 'V', 'F', '1', 0, 0};

    private static final int SHIELD_TAIL_SIZE = 32;

    private final Path path;
    private final FileChannel channel;
    private final String guid;
    private final int fileVersion;
    private final long dataBase;
    private final Map<String, PvfEntry> entries;

    private PvfArchive(Path path, FileChannel channel, String guid,
                       int fileVersion, long dataBase, Map<String, PvfEntry> entries) {
        this.path = path;
        this.channel = channel;
        this.guid = guid;
        this.fileVersion = fileVersion;
        this.dataBase = dataBase;
        this.entries = entries;
    }

    public static PvfArchive open(Path path) throws IOException {
        FileChannel channel = FileChannel.open(path, StandardOpenOption.READ);
        try {
            refuseIfEncrypted(channel, path);

            int guidLength = read(channel, 0, 4).getInt();
            if (guidLength < 0 || guidLength > MAX_GUID_LENGTH) {
                throw new PvfFormatException(path + " 不是 ACT5 的 PVF 包：开头的 GUID 长度是 "
                        + Integer.toUnsignedLong(guidLength)
                        + "。60 版那种以 \"DNF_SCRIPT_PACK\" 开头的包要用另一套头解析");
            }

            int headerLength = 4 + guidLength + 16;
            ByteBuffer header = read(channel, 4, headerLength - 4);
            byte[] guidBytes = new byte[guidLength];
            header.get(guidBytes);
            String guid = new String(guidBytes, StandardCharsets.ISO_8859_1);
            int fileVersion = header.getInt();
            int indexLength = header.getInt();
            int indexKey = header.getInt();
            int fileCount = header.getInt();

            if (indexLength < 0 || fileCount < 0) {
                throw new PvfFormatException(path + " 的文件头不合理：indexLength="
                        + Integer.toUnsignedLong(indexLength)
                        + " fileCount=" + Integer.toUnsignedLong(fileCount));
            }

            byte[] index = new byte[(indexLength + 3) & ~3];
            read(channel, headerLength, indexLength).get(index, 0, indexLength);
            if (!PvfCrypto.decrypt(index, fileCount, indexKey)) {
                throw new PvfFormatException(path + " 的索引区 CRC 校验失败");
            }

            Map<String, PvfEntry> parsed = parseIndex(path, index, indexLength, fileCount,
                    A4_GUID.equals(guid));
            return new PvfArchive(path, channel, guid, fileVersion,
                    headerLength + indexLength, parsed);
        } catch (IOException | RuntimeException e) {
            channel.close();
            throw e;
        }
    }

    /**
     * @param a4 A4 包的索引记录第一个 u32 是文件编号，不是名字哈希。其余字段、正文加密和
     *           CRC（种子仍是名字哈希）与 ACT5 完全相同，所以哈希改由名字算出来。
     */
    private static Map<String, PvfEntry> parseIndex(Path path, byte[] index, int length,
                                                    int fileCount, boolean a4) {
        ByteBuffer b = ByteBuffer.wrap(index, 0, length).order(ByteOrder.LITTLE_ENDIAN);
        Map<String, PvfEntry> out = LinkedHashMap.newLinkedHashMap(fileCount);
        for (int i = 0; i < fileCount; i++) {
            if (b.remaining() < 8) {
                throw new PvfFormatException("索引区在第 " + i + " 条记录处提前结束");
            }
            int nameHash = b.getInt();
            int nameLength = b.getInt();
            if (nameLength < 0 || nameLength > b.remaining() - 12) {
                throw new PvfFormatException("第 " + i + " 条记录的名字长度不合理："
                        + Integer.toUnsignedLong(nameLength));
            }
            byte[] nameBytes = new byte[nameLength];
            b.get(nameBytes);
            int fileLength = b.getInt();
            int key = b.getInt();
            int offset = b.getInt();

            int end = 0;
            while (end < nameBytes.length && nameBytes[end] != 0) {
                end++;
            }
            String name = PvfPath.normalize(
                    new String(nameBytes, 0, end, StandardCharsets.ISO_8859_1));
            // A4 包与 ACT5 同一容器，唯一差别：索引记录首个 u32 是文件编号不是名字哈希 → 由名字算（71019 条 CRC 全过）
            if (a4) {
                nameHash = PvfPath.hash(name);
            } else if (PvfPath.hash(name) != nameHash) {
                throw new PvfFormatException(path + " 的第 " + i + " 条索引记录（" + name
                        + "）哈希对不上：存的是 " + nameHash + "，算出来是 " + PvfPath.hash(name)
                        + "。索引区多半从更早的某条起就已经错位了");
            }
            out.put(name, new PvfEntry(name, nameHash, offset, fileLength, key));
        }
        return Map.copyOf(out);
    }

    public int size() {
        return entries.size();
    }

    public String guid() {
        return guid;
    }

    public int fileVersion() {
        return fileVersion;
    }

    public Collection<PvfEntry> entries() {
        return entries.values();
    }

    public boolean contains(String path) {
        return entries.containsKey(PvfPath.normalize(path));
    }

    public Optional<PvfEntry> entry(String path) {
        return Optional.ofNullable(entries.get(PvfPath.normalize(path)));
    }

    public byte[] read(String path) {
        PvfEntry e = entry(path).orElseThrow(() ->
                new PvfFormatException(this.path + " 里没有 " + PvfPath.normalize(path)));
        byte[] buf = new byte[e.paddedLength()];
        try {
            ByteBuffer b = read(channel, dataBase + Integer.toUnsignedLong(e.offset()),
                    buf.length);
            b.get(buf);
        } catch (IOException io) {
            throw new UncheckedIOException("读取 " + e.name() + " 失败", io);
        }
        if (!PvfCrypto.decrypt(buf, e.nameHash(), e.key())) {
            throw new PvfFormatException(e.name() + " 的 CRC 校验失败");
        }
        byte[] out = new byte[e.length()];
        System.arraycopy(buf, 0, out, 0, e.length());
        return out;
    }

    public String readText(String path) {
        return new String(read(path), SCRIPT_CHARSET);
    }

    private static ByteBuffer read(FileChannel channel, long position, int length)
            throws IOException {
        ByteBuffer b = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
        while (b.hasRemaining()) {
            int n = channel.read(b, position + b.position());
            if (n < 0) {
                throw new PvfFormatException("在偏移 " + position + " 处读不满 "
                        + length + " 字节，文件被截断");
            }
        }
        b.flip();
        return b;
    }

    /**
     * 这一份只读明文 pvf。加密包（尾部带 A5PVF1 标记）照 fail-fast 当场拒绝 ——
     * 不拒绝的话解出来的是乱码，错误会推迟到某个脚本解析失败的地方，看起来像数据坏了。
     */
    private static void refuseIfEncrypted(FileChannel channel, Path path) throws IOException {
        long size = channel.size();
        if (size < SHIELD_TAIL_SIZE) {
            return;
        }
        ByteBuffer tail = ByteBuffer.allocate(SHIELD_MAGIC.length);
        while (tail.hasRemaining()) {
            if (channel.read(tail, size - SHIELD_TAIL_SIZE + tail.position()) < 0) {
                return;
            }
        }
        for (int i = 0; i < SHIELD_MAGIC.length; i++) {
            if (tail.get(i) != SHIELD_MAGIC[i]) {
                return;
            }
        }
        throw new PvfFormatException(path + " 是加密过的 pvf 包（尾部带 A5PVF1 标记）。"
                + "这一份服务端只读明文包 —— 客户端补丁里也没有解密那一层，"
                + "所以加密包两边都读不了。请换成明文的 Script.pvf");
    }

    public Path path() {
        return path;
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
