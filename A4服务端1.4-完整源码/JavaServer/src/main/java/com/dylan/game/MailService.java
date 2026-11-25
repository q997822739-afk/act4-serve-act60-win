package com.dylan.game;

import com.dylan.protocol.body.Cmd97MailBoxSendRequest;
import com.dylan.protocol.body.ItemListType;
import com.dylan.protocol.body.LetterStat;
import com.dylan.protocol.body.MailError;
import com.dylan.protocol.body.Noti13ItemListBody;
import com.dylan.protocol.body.Noti97MailBoxListBody;
import com.dylan.pvf.script.ItemScript;
import com.dylan.store.AvatarItemRow;
import com.dylan.store.GameStore;
import com.dylan.store.LetterRow;
import com.dylan.store.MailRow;
import com.dylan.store.MailStore;
import com.dylan.store.blob.InvenItem;
import com.dylan.store.blob.ItemArray;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

public final class MailService {
    private final GameSession session;

    private final List<MailRow> mails = new ArrayList<>();

    private final List<LetterRow> letters = new ArrayList<>();

    private int notLoadedMailCount;

    private long lastLoadedMailIdx;

    private int characNo;

    public MailService(GameSession session) {
        this.session = session;
    }

    public void load(int characNo) {
        this.characNo = characNo;
        mails.clear();
        letters.clear();
        notLoadedMailCount = 0;
        lastLoadedMailIdx = 0;
        GameStore store = session == null ? null : session.store();
        if (store == null || characNo <= 0) {
            return;
        }
        store.mail().expireMails(characNo);
        store.mail().purgeLetters(characNo);
        take(store.mail().loadMails(characNo, 0, MailRow.PAGE_SIZE));
        // take 只能按"这一页拿回来几行"粗算剩余；满页（正好 20 封）时它会把"还有更多"报成 0。
        // 用库里的真实总数校正一次 —— 在线收信（syncFromStore）也是同一个口径。
        notLoadedMailCount = Math.max(0, store.mail().countMails(characNo) - mails.size());
        letters.addAll(store.mail().loadLetters(characNo));
    }

    public void clear() {
        characNo = 0;
        mails.clear();
        letters.clear();
        notLoadedMailCount = 0;
        lastLoadedMailIdx = 0;
    }

    private void take(List<MailRow> page) {
        int room = MailRow.PAGE_SIZE - mails.size();
        int taken = Math.min(room, page.size());
        for (int i = 0; i < taken; i++) {
            MailRow row = page.get(i);
            mails.add(row);
            lastLoadedMailIdx = Math.max(lastLoadedMailIdx, row.postalId());
        }
        notLoadedMailCount = page.size() - taken;
    }

    public int remainingRoom() {
        return MailRow.PAGE_SIZE - mails.size();
    }

    public void sendList(int mode, String why) {
        send(mode, mails, letters, why);
    }

    private void send(int mode, List<MailRow> mailRows, List<LetterRow> letterRows, String why) {
        if (session == null) {
            return;
        }
        Instant now = Instant.now();
        List<Noti97MailBoxListBody.Mail> wire = new ArrayList<>(mailRows.size());
        for (MailRow row : mailRows) {
            wire.add(toWire(row, now));
        }
        List<Noti97MailBoxListBody.Letter> wireLetters = new ArrayList<>(letterRows.size());
        for (LetterRow row : letterRows) {
            wireLetters.add(toWire(row));
        }
        var body = new Noti97MailBoxListBody(mode, wire, notLoadedMailCount, wireLetters);
        byte[] bytes = body.toBytes();
        session.send(Noti97MailBoxListBody.PACKET_ID, bytes);
    }

    private Noti97MailBoxListBody.Mail toWire(MailRow row, Instant now) {
        long quantity = row.avatar() ? avatarRemainingSeconds(row.addInfo(), now) : row.addInfo();
        return new Noti97MailBoxListBody.Mail(
                row.postalId(),
                row.senderName() == null ? "" : row.senderName(),
                row.gold(),
                row.itemId(),
                row.sealed() ? Noti13ItemListBody.SEAL_SEALED : Noti13ItemListBody.SEAL_UNSEALED,
                quantity,
                row.endurance(),
                row.upgrade(),
                row.enchant(),
                row.amplifyOption(),
                row.amplifyValue(),
                row.avatar(),
                row.remainingSeconds(now),
                row.letterId());
    }

    private Noti97MailBoxListBody.Letter toWire(LetterRow row) {
        boolean unlimited = mails.stream()
                .anyMatch(m -> m.letterId() == row.letterId() && m.unlimited());
        return new Noti97MailBoxListBody.Letter(
                row.letterId(),
                row.senderCharacNo(),
                row.senderName() == null ? "" : row.senderName(),
                row.text() == null ? "" : row.text(),
                unlimited ? 0L : row.registeredAtEpochSecond(),
                row.stat());
    }

    private long avatarRemainingSeconds(long uiId, Instant now) {
        GameStore store = session == null ? null : session.store();
        if (store == null) {
            return 0L;
        }
        return store.avatar().findAvatarItem(uiId).map(r -> r.remainingSeconds(now)).orElse(0L);
    }

    public int open() {
        GameStore store = session == null ? null : session.store();
        if (store == null || characNo <= 0) {
            return notLoadedMailCount;
        }
        // 以前这里 if (notLoadedMailCount <= 0) return —— "页尾没剩下"就整段跳过。
        // 那条短路把**在线收到的信**挡在门外：邮件是外部进程（网页 GM / 别的玩家）直接插进
        // postal 的，内存这份快照不知道；notLoadedMailCount 在选角时是 0，于是点开邮箱什么也不补。
        // 现在整段交给 syncFromStore（它每次都重扫、只追加、并把计数校正到库里真实值）——
        // 既能补拉翻页剩下的，也能补进在线新到的。
        syncFromStore("点开邮箱补拉");
        return notLoadedMailCount;
    }

    /**
     * 在线收信：把外部进程（网页 GM 投递、别人寄件）**新插入** postal 的邮件补进内存快照，
     * 并把"还有多少封没加载"按库里的真实数量校正，然后推给客户端。
     *
     * 为什么必须这样：选角那一刻 {@link #load} 把邮箱读进内存后就不再碰库了，此后库里新增的行
     * 服务端看不见 —— 这就是"收 GM 发的邮件必须先小退"的根：重新选角才会再 load 一次。
     * 现在由 {@code MailPoller} 周期性调这里，不用小退也能收到。
     *
     * 只**追加**、绝不删改内存里已有的行（那些可能是刚领走/刚被服务端自己动过的）；
     * 去重按 postal_id：postal_id 是自增主键，只要 &gt; 已见到过的最大值就是新信。
     * 返回这次新补进来的行数（0 = 没新信，调用方就不用发包）。
     */
    public int syncFromStore(String why) {
        GameStore store = session == null ? null : session.store();
        if (store == null || characNo <= 0) {
            return 0;
        }
        int before = mails.size();
        // 从"已见最大值"往后拉；一次拉一页，页满就继续，直到拉空或快照满。
        while (remainingRoom() > 0) {
            List<MailRow> page = store.mail().loadMails(characNo, lastLoadedMailIdx, MailRow.PAGE_SIZE);
            int taken = 0;
            for (MailRow row : page) {
                if (remainingRoom() <= 0) {
                    break;
                }
                mails.add(row);
                lastLoadedMailIdx = Math.max(lastLoadedMailIdx, row.postalId());
                taken++;
            }
            if (taken < MailRow.PAGE_SIZE) {
                break;
            }
        }
        List<MailRow> added = List.copyOf(mails.subList(before, mails.size()));
        // 信件（邮件正文）也可能一起到：别人寄件时信和邮件是两行。这里补进新出现的那几封。
        List<LetterRow> newLetters = syncLetters(store);
        // 未加载数改成"库里现存总数 - 已加载数"，比原来的减一计数准（外部插入/领走都能跟上）。
        int total = store.mail().countMails(characNo);
        notLoadedMailCount = Math.max(0, total - mails.size());
        if (added.isEmpty() && newLetters.isEmpty()) {
            return 0;
        }
        if (!added.isEmpty()) {
            send(Noti97MailBoxListBody.MODE_APPEND, added, newLetters, why);
        } else {
            // 只有新信件、没有新邮件：也走 APPEND（空的邮件表 + 新信件），客户端照样能显示。
            send(Noti97MailBoxListBody.MODE_APPEND, List.of(), newLetters, why);
        }
        return added.size();
    }

    /** 把库里新出现的信件补进内存（按 letter_id 去重）；返回这次新加的。 */
    private List<LetterRow> syncLetters(GameStore store) {
        java.util.Set<Long> have = new java.util.HashSet<>();
        for (LetterRow row : letters) {
            have.add(row.letterId());
        }
        List<LetterRow> fresh = new ArrayList<>();
        for (LetterRow row : store.mail().loadLetters(characNo)) {
            if (have.add(row.letterId())) {
                fresh.add(row);
            }
        }
        letters.addAll(fresh);
        return fresh;
    }

    /**
     * 有新信且角色在游戏里时，额外发一条 NOTI 99「邮箱提醒」—— 客户端据此点亮邮箱入口的角标，
     * 玩家不用手动开邮箱也知道有信。
     *
     * 值取**邮箱里还没领的邮件总数**（已加载 + 未加载），不是 notLoadedMailCount —— 后者是
     * "翻页还没翻到的那部分"，在线收到的信会被 syncFromStore 直接加载进内存、notLoaded 恒为 0，
     * 拿它当角标就永远是 0、等于没提醒。"还没领多少封"才是玩家眼里"有几封新邮件"。
     *
     * 语义上是**提示**不是**数据**：就算这份客户端忽略 NOTI 99，邮件本身也已经由 NOTI 97
     * 追加进列表了（玩家开邮箱照样看到）—— 所以这条包发失败无所谓，绝不能因为它影响收信。
     */
    public void alarmNewMail() {
        if (session == null) {
            return;
        }
        int unclaimed = mails.size() + notLoadedMailCount;
        session.sendNow(com.dylan.protocol.body.Noti99MailAlarmBody.PACKET_ID,
                new com.dylan.protocol.body.Noti99MailAlarmBody(
                        Math.max(0, Math.min(0xFFFF, unclaimed))).toBytes());
    }

    /** 邮箱里还没领的邮件总数（已加载进内存的 + 还在库里没翻到的）。 */
    public int unclaimedCount() {
        return mails.size() + notLoadedMailCount;
    }

    public record Outcome(MailError error) {
        static final Outcome OK = new Outcome(null);

        static Outcome fail(MailError error) {
            return new Outcome(error);
        }

        public boolean ok() {
            return error == null;
        }
    }

    public Outcome extract(long mailIdx) {
        if (session == null) {
            return Outcome.fail(MailError.NO_SUCH_MAIL);
        }
        MailRow mail = find(mailIdx);
        if (mail == null) {
            return Outcome.fail(MailError.NO_SUCH_MAIL);
        }
        InventoryService inv = session.inventory();
        ItemArray bag = inv.containerOf(ItemListType.LIST_MAIN);
        if (bag == null) {
            return Outcome.fail(MailError.NO_SUCH_MAIL);
        }

        InvenItem incoming = null;
        ItemScript incomingScript = null;
        boolean intoCreature = false;
        if (mail.hasItem() && !mail.avatar()) {
            incoming = toInvenItem(mail);
            if (incoming == null) {
                return Outcome.fail(MailError.UNKNOWN_ITEM);
            }
            incomingScript = session.scripts() == null ? null
                    : session.scripts().item((int) mail.itemId()).orElse(null);
            intoCreature = incomingScript != null
                    && InventoryService.isCreatureItemType(
                            InventoryService.itemTypeOf(incomingScript));
            boolean fits = intoCreature
                    ? InventoryService.tryCreatureSlot(
                            inv.containerOf(ItemListType.LIST_CREATURE), incomingScript,
                            (int) incoming.stackCount()) >= 0
                    : inv.tryInsertExisting(bag, incoming) >= 0;
            if (!fits) {
                return Outcome.fail(MailError.INVENTORY_FULL);
            }
        }
        AvatarItemRow avatarRow = null;
        if (mail.hasItem() && mail.avatar()) {
            GameStore store = session.store();
            avatarRow = store == null ? null : store.avatar().findAvatarItem(mail.addInfo()).orElse(null);
            if (avatarRow == null) {
                return Outcome.fail(MailError.UNKNOWN_ITEM);
            }
            if (inv.firstFreeAvatarSlot() < 0) {
                return Outcome.fail(MailError.INVENTORY_FULL);
            }
        }

        var touched = new LinkedHashMap<Integer, LinkedHashSet<Integer>>();
        if (mail.gold() > 0) {
            inv.setMoney(inv.money() + mail.gold());
            touched.computeIfAbsent(ItemListType.LIST_MAIN, k -> new LinkedHashSet<>())
                    .add(BagLayout.SLOT_GOLD);
        }
        if (incoming != null) {
            int space = ItemListType.LIST_MAIN;
            int slot;
            if (intoCreature) {
                InventoryService.Landing landing =
                        inv.addItem(incomingScript, (int) incoming.stackCount());
                space = landing.itemSpace();
                slot = landing.slot();
            } else {
                slot = inv.insertExisting(bag, incoming);
                inv.markItemsDirty();
            }
            if (slot < 0) {
                throw new IllegalStateException("邮件 " + mailIdx + " 的物品干跑放得下、真放却放不下");
            }
            touched.computeIfAbsent(space, k -> new LinkedHashSet<>()).add(slot);
        }
        if (avatarRow != null) {
            int slot = inv.receiveAvatarItem(avatarRow);
            if (slot < 0) {
                throw new IllegalStateException("邮件 " + mailIdx + " 的时装干跑放得下、真放却放不下");
            }
            touched.computeIfAbsent(ItemListType.LIST_AVATAR, k -> new LinkedHashSet<>()).add(slot);
        }

        GameStore store = session.store();
        if (store != null) {
            store.mail().removeMail(mailIdx, MailRow.DELETE_FLAG_TAKEN);
        }
        mails.remove(mail);

        for (var entry : touched.entrySet()) {
            inv.sendItemUpdate(entry.getKey(), "领取邮件 " + mailIdx,
                    entry.getValue().stream().mapToInt(Integer::intValue).toArray());
        }
        return Outcome.OK;
    }

    private InvenItem toInvenItem(MailRow mail) {
        var scripts = session.scripts();
        ItemScript script = scripts == null
                ? null : scripts.item((int) mail.itemId()).orElse(null);
        if (script == null) {
            return null;
        }
        return InventoryService.newItem(script, (int) Math.max(1L, mail.addInfo()))
                .withAttachType(mail.sealed() ? script.attachType() : 0)
                .withItemAttr(mail.upgrade())
                .withEndurance(mail.endurance())
                .withAmplify(com.dylan.store.blob.AmplifyOption.of(
                        mail.amplifyOption(), mail.amplifyValue(), true));
    }

    private MailRow find(long mailIdx) {
        for (MailRow row : mails) {
            if (row.postalId() == mailIdx) {
                return row;
            }
        }
        return null;
    }

    public static long postage(long gold, boolean withItem) {
        long fee = withItem ? FEE_WITH_ITEM : FEE_PLAIN;
        if (gold > 0) {
            fee += 5 * gold / 100;
            fee = Math.min(fee, withItem ? FEE_CAP_WITH_ITEM : FEE_CAP_PLAIN);
        }
        return fee;
    }

    public static final long FEE_WITH_ITEM = 1000;

    public static final long FEE_PLAIN = 100;

    public static final long FEE_CAP_WITH_ITEM = 11000;

    public static final long FEE_CAP_PLAIN = 10100;

    public Outcome send(Cmd97MailBoxSendRequest req) {
        GameStore store = session == null ? null : session.store();
        if (store == null || characNo <= 0) {
            return Outcome.fail(MailError.NO_SUCH_MAIL);
        }
        if (req.guild()) {
            return Outcome.fail(MailError.ITEM_NOT_PACKAGABLE);
        }
        if (!req.hasItem() && req.gold() <= 0 && req.text().isEmpty()) {
            return Outcome.fail(MailError.BUSY_OR_FULL);
        }
        Optional<MailStore.Receiver> found = store.mail().findReceiver(req.receiverName());
        if (found.isEmpty()) {
            return Outcome.fail(MailError.NO_SUCH_RECEIVER);
        }
        MailStore.Receiver receiver = found.get();
        if (receiver.deleted()) {
            return Outcome.fail(MailError.RECEIVER_DELETED);
        }

        InventoryService inv = session.inventory();
        InvenItem attachment = null;
        if (req.hasItem()) {
            if (req.itemSpace() != ItemListType.LIST_MAIN) {
                return Outcome.fail(MailError.ITEM_NOT_PACKAGABLE);
            }
            ItemArray bag = inv.containerOf(ItemListType.LIST_MAIN);
            if (bag == null || req.slot() < 0 || req.slot() >= bag.slots()) {
                return Outcome.fail(MailError.CANNOT_SEND_ITEM);
            }
            if (session.itemLock().isLocked(ItemListType.LIST_MAIN, req.slot())) {
                return Outcome.fail(MailError.ITEM_NOT_PACKAGABLE);
            }
            InvenItem there = bag.get(req.slot());
            if (there.isEmpty() || there.itemId() != req.itemId()) {
                session.hack().count(com.dylan.game.HackType.MEMORY_MODIFIED,
                        HackAnalyzer.Site.MAIL_ITEM_MISMATCH, (int) req.itemId(),
                        "要寄主背包第 " + req.slot() + " 格的物品 " + req.itemId()
                                + "，服务端那一格是 " + there.itemId());
                return Outcome.fail(MailError.CANNOT_SEND_ITEM);
            }
            var scripts = session.scripts();
            ItemScript script = scripts == null
                    ? null : scripts.item((int) req.itemId()).orElse(null);
            if (script == null) {
                return Outcome.fail(MailError.UNKNOWN_ITEM);
            }
            long have = Math.max(1L, there.addInfo());
            long want = Math.max(1L, req.count());
            if (!script.isStackableItem()) {
                want = 1;
            } else if (want > have) {
                return Outcome.fail(MailError.CANNOT_SEND_ITEM);
            }
            attachment = there.withAddInfo(script.isStackableItem() ? want : there.addInfo());
        }

        long fee = postage(req.gold(), req.hasItem());
        long need = fee + req.gold();
        if (inv.money() < need) {
            return Outcome.fail(MailError.NOT_ENOUGH_GOLD);
        }

        inv.setMoney(inv.money() - need);
        int[] touchedSlots;
        if (attachment != null) {
            int taken = inv.deleteItem(ItemListType.LIST_MAIN, req.slot(), (int) req.itemId(),
                    (int) attachment.stackCount());
            if (taken < 0) {
                session.fail("寄件时从主背包槽 " + req.slot() + " 扣 " + req.itemId()
                        + " 失败（错误码 " + (-taken) + "），金币已经扣掉，状态不一致");
                return Outcome.fail(MailError.CANNOT_SEND_ITEM);
            }
            touchedSlots = new int[] {BagLayout.SLOT_GOLD, req.slot()};
        } else {
            touchedSlots = new int[] {BagLayout.SLOT_GOLD};
        }

        Instant now = Instant.now();
        String senderName = session.characName() == null ? "" : session.characName();
        long letterId = 0;
        if (!req.text().isEmpty()) {
            letterId = store.mail().insertLetter(receiver.characNo(), characNo, senderName, req.text(),
                    now, LetterStat.UNREAD);
        }
        long postalId = store.mail().insertMail(characNo, senderName, receiver.characNo(), now,
                attachment == null ? 0 : attachment.itemId(),
                attachment == null ? 0 : attachment.addInfo(),
                attachment == null ? 0 : attachment.endurance(),
                attachment == null ? 0 : attachment.itemAttr(),
                attachment == null ? 0 : attachment.amplify().abilityType(),
                attachment == null ? 0 : attachment.amplify().abilityValue(),
                attachment == null ? 0 : attachment.enchant(),
                req.gold(), attachment != null && attachment.attachType() != 0,
                false, false, letterId);

        inv.sendItemUpdate(ItemListType.LIST_MAIN, "寄出邮件 " + postalId, touchedSlots);
        return Outcome.OK;
    }

    public MailError changeLetterStat(long letterId, int stat) {
        if (!LetterStat.isValidRequest(stat)) {
            return MailError.BAD_LETTER_STAT;
        }
        if (stat == LetterStat.KEPT && keptCount() + 1 > LetterStat.MAX_KEPT) {
            return MailError.BUSY_OR_FULL;
        }
        GameStore store = session == null ? null : session.store();
        if (store == null) {
            return MailError.NO_SUCH_MAIL;
        }
        LetterRow row = findLetter(letterId);
        if (row == null) {
            return MailError.NO_SUCH_MAIL;
        }
        if (!store.mail().changeLetterStat(letterId, stat)) {
            return MailError.NO_SUCH_MAIL;
        }
        if (stat == LetterStat.DELETE) {
            letters.remove(row);
        } else {
            letters.set(letters.indexOf(row), row.withStat(stat));
        }
        return null;
    }

    public int keptCount() {
        int n = 0;
        for (LetterRow row : letters) {
            if (row.stat() == LetterStat.KEPT) {
                n++;
            }
        }
        return n;
    }

    private LetterRow findLetter(long letterId) {
        for (LetterRow row : letters) {
            if (row.letterId() == letterId) {
                return row;
            }
        }
        return null;
    }

    public static long deliver(GameStore store, int receiverCharacNo, String senderName,
                               long itemId, long count, long gold, boolean unlimited) {
        return deliver(store, receiverCharacNo, senderName, itemId, count, gold, null, unlimited);
    }

    public static long deliver(GameStore store, int receiverCharacNo, String senderName,
                               long itemId, long count, long gold, String text,
                               boolean unlimited) {
        Instant now = Instant.now();
        long letterId = 0;
        if (text != null && !text.isEmpty()) {
            letterId = store.mail().insertLetter(receiverCharacNo, 0, senderName, text, now,
                    LetterStat.UNREAD);
        }
        return store.mail().insertMail(0, senderName, receiverCharacNo, now,
                itemId, count, 0, 0, 0, 0, 0, gold, false, false, unlimited, letterId);
    }
}
