package com.dylan.game;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.dylan.net.ServerConfig;
import com.dylan.protocol.body.Cmd229ReplyBody;
import com.dylan.protocol.body.Cmd230ReplyBody;
import com.dylan.protocol.body.Cmd231ReplyBody;
import com.dylan.protocol.body.ItemListType;
import com.dylan.protocol.body.Noti207ItemLockListBody;
import com.dylan.protocol.body.Noti208ItemUnlockBody;
import com.dylan.pvf.script.ItemScript;
import com.dylan.store.GameStore;
import com.dylan.store.blob.AmplifyOption;
import com.dylan.store.blob.InvenItem;
import com.dylan.store.blob.ItemArray;
import com.dylan.store.blob.ItemLockTable;

public final class ItemLockService {

    public static final int UNLOCK_WAIT_SEC = 259_200;

    public static final long TIMER_PERIOD_MS = 60_000L;

    public static final int UNLOCK_COOLDOWN_SEC = 3;

    public static final int ERROR_ITEM_LOCKED = 205;

    private static final int[] SPACES = {
            ItemListType.LIST_MAIN,
            ItemListType.LIST_EQUIPPED,
            ItemListType.LIST_CARGO,
            ItemListType.LIST_CREATURE,
    };

    private final GameSession session;

    private int characNo;
    private ItemLockTable table = ItemLockTable.empty();
    private boolean timerArmed;
    private long lastUnlockRequestAtMs;

    public ItemLockService(GameSession session) {
        this.session = session;
    }

    public void load(int characNo) {
        this.characNo = characNo;
        this.timerArmed = false;
        this.lastUnlockRequestAtMs = 0;
        GameStore store = session == null ? null : session.store();
        if (store == null) {
            this.table = ItemLockTable.empty();
            return;
        }
        this.table = store.itemLock().loadItemLocks(characNo);
        verify();
        armTimerIfNeeded();
    }

    void attach(int characNo, ItemLockTable table) {
        this.characNo = characNo;
        this.table = table;
        this.timerArmed = false;
        this.lastUnlockRequestAtMs = 0;
    }

    ItemLockTable table() {
        return table;
    }

    public void clear() {
        characNo = 0;
        table = ItemLockTable.empty();
        timerArmed = false;
        lastUnlockRequestAtMs = 0;
    }

    public void save() {
        GameStore store = session == null ? null : session.store();
        if (store == null || characNo == 0) {
            return;
        }
        store.itemLock().saveItemLocks(characNo, table);
    }

    void verify() {
        boolean[] seen = new boolean[ItemLockTable.LAST_KEY + 1];
        boolean changed = expireOnLoad();
        for (int space : SPACES) {
            ItemArray array = containerOf(space);
            if (array == null) {
                continue;
            }
            for (int slot = 0; slot < array.slots(); slot++) {
                InvenItem item = array.get(slot);
                int key = item.amplify().lock();
                if (key == 0) {
                    continue;
                }
                if (key < ItemLockTable.FIRST_KEY || key > ItemLockTable.LAST_KEY) {
                    clearLockKey(space, slot);
                    continue;
                }
                if (table.free(key)) {
                    clearLockKey(space, slot);
                    continue;
                }
                if (seen[key]) {
                    clearLockKey(space, slot);
                    continue;
                }
                seen[key] = true;
            }
        }
        for (int key = ItemLockTable.FIRST_KEY; key <= ItemLockTable.LAST_KEY; key++) {
            if (!table.free(key) && !seen[key]) {
                table.release(key);
                changed = true;
            }
        }
        if (changed) {
            persist();
        }
    }

    private boolean expireOnLoad() {
        long now = now();
        boolean changed = false;
        for (int key = ItemLockTable.FIRST_KEY; key <= ItemLockTable.LAST_KEY; key++) {
            if (table.state(key) != ItemLockTable.STATE_UNLOCK_WAITING
                    || table.time(key) + UNLOCK_WAIT_SEC > now) {
                continue;
            }
            int[] at = findByKey(key);
            if (at != null) {
                clearLockKey(at[0], at[1]);
            }
            table.release(key);
            changed = true;
        }
        return changed;
    }

    public Cmd229ReplyBody lock(int space, int slot) {
        InvenItem item = itemAt(space, slot);
        if (item == null || item.isEmpty()) {
            return Cmd229ReplyBody.rejected(Cmd229ReplyBody.ERROR_NO_ITEM);
        }
        String why = whyCannotLock(space, item);
        if (why != null) {
            return Cmd229ReplyBody.rejected(Cmd229ReplyBody.ERROR_CANNOT);
        }
        int key = table.firstFreeKey();
        if (key == 0) {
            return Cmd229ReplyBody.rejected(Cmd229ReplyBody.ERROR_LOCK_FULL);
        }
        table.set(key, ItemLockTable.STATE_LOCKED, now());
        setLockKey(space, slot, key);
        persist();
        return Cmd229ReplyBody.locked(space, slot);
    }

    public Cmd230ReplyBody unlock(int space, int slot) {
        int cooldown = cooldownRemainSec();
        if (cooldown > 0) {
            return Cmd230ReplyBody.rejected(Cmd230ReplyBody.ERROR_TOO_FREQUENT, cooldown);
        }
        int key = keyAt(space, slot);
        if (key == 0 || table.state(key) != ItemLockTable.STATE_LOCKED) {
            return Cmd230ReplyBody.rejected(Cmd229ReplyBody.ERROR_CANNOT);
        }
        table.set(key, ItemLockTable.STATE_UNLOCK_WAITING, now());
        markUnlockRequested();
        persist();
        armTimerIfNeeded();
        return Cmd230ReplyBody.accepted(space, slot, UNLOCK_WAIT_SEC);
    }

    public Cmd231ReplyBody unlockCancel(int space, int slot) {
        InvenItem item = itemAt(space, slot);
        if (item == null || item.isEmpty()) {
            return Cmd231ReplyBody.rejected(Cmd229ReplyBody.ERROR_NO_ITEM);
        }
        int key = item.amplify().lock();
        if (key == 0 || table.state(key) != ItemLockTable.STATE_UNLOCK_WAITING) {
            return Cmd231ReplyBody.rejected(Cmd229ReplyBody.ERROR_CANNOT);
        }
        table.set(key, ItemLockTable.STATE_LOCKED, now());
        persist();
        return Cmd231ReplyBody.cancelled(space, slot);
    }

    public boolean isLocked(int space, int slot) {
        int key = keyAt(space, slot);
        return key != 0 && !table.free(key);
    }

    public boolean blocksMove(int srcType, int srcSlot, int dstType) {
        return moveChecksLock(srcType, srcSlot, dstType) && isLocked(srcType, srcSlot);
    }

    private static boolean moveChecksLock(int srcType, int srcSlot, int dstType) {
        if (dstType == ItemListType.LIST_TRADE
                || dstType == MOVE_LOCK_DST_UNKNOWN_5
                || dstType == MOVE_LOCK_DST_UNKNOWN_14) {
            return true;
        }
        return dstType == ItemListType.LIST_EQUIPPED
                && srcType == ItemListType.LIST_CREATURE
                && srcSlot > CreatureBagLayout.PET.lastSlot();
    }

    private static final int MOVE_LOCK_DST_UNKNOWN_5 = 5;

    private static final int MOVE_LOCK_DST_UNKNOWN_14 = 14;

    public boolean isLocked(InvenItem item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        int key = item.amplify().lock();
        return key >= ItemLockTable.FIRST_KEY && key <= ItemLockTable.LAST_KEY && !table.free(key);
    }

    public Noti207ItemLockListBody lockList() {
        Noti207ItemLockListBody.Builder builder = new Noti207ItemLockListBody.Builder();
        long now = now();
        for (int space : SPACES) {
            ItemArray array = containerOf(space);
            if (array == null) {
                continue;
            }
            for (int slot = 0; slot < array.slots(); slot++) {
                int key = array.get(slot).amplify().lock();
                if (key < ItemLockTable.FIRST_KEY || key > ItemLockTable.LAST_KEY) {
                    continue;
                }
                int state = table.state(key);
                if (state == ItemLockTable.STATE_LOCKED) {
                    builder.add(Noti207ItemLockListBody.Entry.locked(space, slot));
                } else if (state == ItemLockTable.STATE_UNLOCK_WAITING) {
                    builder.add(Noti207ItemLockListBody.Entry.waiting(space, slot,
                            remainSec(key, now)));
                }
            }
        }
        return builder.build();
    }

    public void sendLockList(String why) {
        if (session == null || characNo == 0) {
            return;
        }
        Noti207ItemLockListBody body = lockList();
        byte[] bytes = body.toBytes();
        session.send(ServerConfig.A4 ? Noti207ItemLockListBody.A4_PACKET_ID : Noti207ItemLockListBody.PACKET_ID,
                bytes);
    }

    /** A4：NOTI 2/0 重发会重建身上装备，有锁时补一条 NOTI 192（锁列表重复发无副作用）。 */
    public void resendIfAnyLocked(String why) {
        if (ServerConfig.A4 && characNo != 0 && table.usedLocks() > 0) {
            sendLockList(why);
        }
    }

    private void tick() {
        if (characNo == 0) {
            timerArmed = false;
            return;
        }
        long now = now();
        boolean changed = false;
        List<Noti208ItemUnlockBody> unlocked = new ArrayList<>();
        for (int key = ItemLockTable.FIRST_KEY; key <= ItemLockTable.LAST_KEY; key++) {
            if (table.state(key) != ItemLockTable.STATE_UNLOCK_WAITING
                    || table.time(key) + UNLOCK_WAIT_SEC > now) {
                continue;
            }
            int[] at = findByKey(key);
            if (at != null) {
                clearLockKey(at[0], at[1]);
                unlocked.add(new Noti208ItemUnlockBody(at[0], at[1]));
            }
            table.release(key);
            changed = true;
        }
        if (changed) {
            persist();
        }
        if (ServerConfig.A4 && !unlocked.isEmpty()) {
            // A4 没有 NOTI 208 的读包（0042AD36 只弹固定提示），解锁到期用 NOTI 192 状态 0 清客户端的锁图标
            Noti207ItemLockListBody.Builder released = new Noti207ItemLockListBody.Builder();
            for (Noti208ItemUnlockBody body : unlocked) {
                released.add(Noti207ItemLockListBody.Entry.released(body.itemSpace(), body.slot()));
            }
            session.send(Noti207ItemLockListBody.A4_PACKET_ID, released.build().toBytes());
        }
        for (Noti208ItemUnlockBody body : unlocked) {
            session.send(Noti208ItemUnlockBody.PACKET_ID, body.toBytes());
        }
        if (!unlocked.isEmpty()) {
            session.flush();
        }
        timerArmed = false;
        armTimerIfNeeded();
    }

    private void armTimerIfNeeded() {
        if (timerArmed || session == null || characNo == 0 || !hasWaitingLock()) {
            return;
        }
        timerArmed = true;
        session.schedule(this::tick, TIMER_PERIOD_MS);
    }

    private boolean hasWaitingLock() {
        for (int key = ItemLockTable.FIRST_KEY; key <= ItemLockTable.LAST_KEY; key++) {
            if (table.state(key) == ItemLockTable.STATE_UNLOCK_WAITING) {
                return true;
            }
        }
        return false;
    }

    private static long now() {
        return Instant.now().getEpochSecond();
    }

    private int remainSec(int key, long now) {
        long remain = table.time(key) + UNLOCK_WAIT_SEC - now;
        return remain <= 0 ? 0 : (int) Math.min(remain, UNLOCK_WAIT_SEC);
    }

    private int cooldownRemainSec() {
        if (lastUnlockRequestAtMs == 0) {
            return 0;
        }
        long elapsed = (System.nanoTime() - lastUnlockRequestAtMs) / 1_000_000L;
        long remain = UNLOCK_COOLDOWN_SEC * 1000L - elapsed;
        return remain <= 0 ? 0 : (int) Math.max(1, remain / 1000);
    }

    private void markUnlockRequested() {
        lastUnlockRequestAtMs = System.nanoTime();
    }

    private ItemArray containerOf(int space) {
        return session == null ? null : session.inventory().containerOf(space);
    }

    private InvenItem itemAt(int space, int slot) {
        if (!supported(space)) {
            return null;
        }
        ItemArray array = containerOf(space);
        if (array == null || slot < 0 || slot >= array.slots()) {
            return null;
        }
        InvenItem item = array.get(slot);
        return item.isEmpty() ? null : item;
    }

    private int keyAt(int space, int slot) {
        InvenItem item = itemAt(space, slot);
        return item == null ? 0 : item.amplify().lock();
    }

    private static boolean supported(int space) {
        for (int s : SPACES) {
            if (s == space) {
                return true;
            }
        }
        return false;
    }

    private int[] findByKey(int key) {
        for (int space : SPACES) {
            ItemArray array = containerOf(space);
            if (array == null) {
                continue;
            }
            for (int slot = 0; slot < array.slots(); slot++) {
                if (array.get(slot).amplify().lock() == key) {
                    return new int[] {space, slot};
                }
            }
        }
        return null;
    }

    private void setLockKey(int space, int slot, int key) {
        ItemArray array = containerOf(space);
        if (array == null || slot < 0 || slot >= array.slots()) {
            return;
        }
        InvenItem item = array.get(slot);
        AmplifyOption amplify = item.amplify();
        array.set(slot, item.withAmplify(
                new AmplifyOption(amplify.rawTypeByte(), amplify.abilityValue(), key)));
        markDirty(space);
    }

    private void clearLockKey(int space, int slot) {
        setLockKey(space, slot, 0);
    }

    private void markDirty(int space) {
        if (session == null) {
            return;
        }
        if (space == ItemListType.LIST_CARGO) {
            session.inventory().markCargoDirty();
        } else {
            session.inventory().markItemsDirty();
        }
    }

    private void persist() {
        if (session == null || characNo == 0) {
            return;
        }
        session.inventory().save();
        save();
    }

    private String whyCannotLock(int space, InvenItem item) {
        if (!supported(space)) {
            return "容器 " + space + " 这一版不支持锁定";
        }
        if (item.avatar()) {
            return "时装的锁编号这一版没有地方存";
        }
        if (item.amplify().lock() != 0) {
            return "已经锁着了（第 " + item.amplify().lock() + " 把锁）";
        }
        ItemScript script = session == null ? null
                : session.scripts().item((int) item.itemId()).orElse(null);
        if (script == null) {
            return "查不到物品 " + item.itemId() + " 的脚本";
        }
        if (script.isStackableItem()) {
            return "可堆叠的东西不能锁";
        }
        return null;
    }
}
