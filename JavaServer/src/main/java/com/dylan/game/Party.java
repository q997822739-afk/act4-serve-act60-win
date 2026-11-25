package com.dylan.game;

import com.dylan.protocol.body.Noti9PartyInfoBody;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Party {
    public static final int MAX_MEMBERS = Noti9PartyInfoBody.MAX_MEMBERS;

    public static final int JOIN_OK = 1;

    public static final int JOIN_FULL = -1;

    public static final int JOIN_IN_DUNGEON = -2;

    public static final int JOIN_ALREADY_IN = -3;

    private final int partyIndex;

    private final GameSession[] slots = new GameSession[MAX_MEMBERS];

    private GameSession manager;

    private int titleKind = Noti9PartyInfoBody.TITLE_ON_THE_WIRE;
    private String title = "";
    private int capacity = MAX_MEMBERS;

    private final boolean singlePlay;

    private int state = DungeonInstance.STATE_TOWN;

    public static final int EPLP_NONE = 0;

    public static final int EPLP_PLAYING = 1;

    public static final int EPLP_CLEARED = 2;

    public static final int EPLP_COMMAND_SELECTED = 3;

    private int eplpState = EPLP_NONE;

    public synchronized int eplpState() {
        return eplpState;
    }

    public synchronized void eplpState(int value) {
        this.eplpState = value;
    }

    public static final int EPLP_CMD_NONE = -1;

    public static final int EPLP_CMD_STAY = 3;

    private int selectedEplpCmd = EPLP_CMD_NONE;

    public synchronized int selectedEplpCmd() {
        return selectedEplpCmd;
    }

    public synchronized void selectedEplpCmd(int value) {
        this.selectedEplpCmd = value;
    }

    private final ClearState clearState = new ClearState();

    public ClearState clearState() {
        return clearState;
    }

    private final int[] timerKeys = new int[TimerMessage.COUNT];

    public synchronized int genTimerKey(int message) {
        return ++timerKeys[message];
    }

    public synchronized boolean checkTimerKey(int message, int key) {
        return timerKeys[message] == key;
    }

    public synchronized void resetTimerKeys() {
        for (int i = 0; i < timerKeys.length; i++) {
            timerKeys[i]++;
        }
    }

    private DungeonInstance dungeon;

    private int worldMapIndex = NO_WORLD_MAP;

    private boolean hellPassChecked;

    public static final int NO_WORLD_MAP = -1;

    private final boolean[] finishedLoading = new boolean[MAX_MEMBERS];

    private boolean gameStarted;

    private final boolean[] alive = new boolean[MAX_MEMBERS];

    private final boolean[] enteredMap = new boolean[MAX_MEMBERS];

    private int routingTurn;

    private final int[] memberUseCoin = new int[MAX_MEMBERS];

    private final int[] skillUse = new int[MAX_MEMBERS];

    private final int[] validSkillUse = new int[MAX_MEMBERS];

    private int usedCoinCount;

    private double hellPartyValue;

    Party(int partyIndex, GameSession founder, String title, int capacity) {
        this(partyIndex, founder, title, capacity, false);
    }

    Party(int partyIndex, GameSession founder, String title, int capacity, boolean singlePlay) {
        this.partyIndex = partyIndex;
        this.slots[0] = founder;
        this.manager = founder;
        this.title = title == null ? "" : title;
        this.capacity = clampCapacity(capacity);
        this.singlePlay = singlePlay;
    }

    public boolean singlePlay() {
        return singlePlay;
    }

    public synchronized int state() {
        return state;
    }

    public synchronized void state(int value) {
        this.state = value;
    }

    public synchronized DungeonInstance dungeon() {
        return dungeon;
    }

    public synchronized DungeonInstance startDungeon(DungeonRun run, BattleField field,
                                                     DungeonRoom startRoom) {
        this.dungeon = new DungeonInstance(run, field, startRoom);
        this.state = DungeonInstance.STATE_IN_DUNGEON;
        this.eplpState = EPLP_PLAYING;
        this.selectedEplpCmd = EPLP_CMD_NONE;
        resetTimerKeys();
        return this.dungeon;
    }

    public synchronized void endDungeon() {
        this.dungeon = null;
        this.state = DungeonInstance.STATE_TOWN;
        this.eplpState = EPLP_NONE;
        this.clearState.reset();
    }

    public synchronized void startDeathTower(DungeonRun run, DungeonRoom startRoom) {
        this.dungeon = new DungeonInstance(run, null, startRoom);
        this.state = DungeonInstance.STATE_IN_DUNGEON;
        this.eplpState = EPLP_PLAYING;
        this.selectedEplpCmd = EPLP_CMD_NONE;
        resetTimerKeys();
    }

    public synchronized void currentRoom(DungeonRoom room) {
        if (dungeon != null) {
            dungeon.room(room);
        }
    }

    public synchronized boolean finishLoading(GameSession s) {
        int slot = slotOf(s);
        if (slot < 0 || finishedLoading[slot]) {
            return false;
        }
        finishedLoading[slot] = true;
        return true;
    }

    public synchronized boolean checkAllMemberLoading(boolean forced) {
        if (gameStarted) {
            return false;
        }
        int memberCount = memberCount();
        if (memberCount == 0) {
            return false;
        }
        int loaded = 0;
        for (int i = 0; i < MAX_MEMBERS; i++) {
            if (slots[i] != null && finishedLoading[i]) {
                loaded++;
            }
        }
        if (loaded != memberCount && !(forced && loaded > 0)) {
            return false;
        }
        gameStarted = true;
        return true;
    }

    public synchronized boolean gameStarted() {
        return gameStarted;
    }

    public synchronized void resetFinishLoadingVar() {
        java.util.Arrays.fill(finishedLoading, false);
    }

    public synchronized int armLoadingTimer() {
        gameStarted = false;
        return genTimerKey(TimerMessage.MAP_LOADING);
    }

    public synchronized List<GameSession> membersNotLoaded() {
        List<GameSession> out = new ArrayList<>(MAX_MEMBERS);
        for (int i = 0; i < MAX_MEMBERS; i++) {
            if (slots[i] != null && !finishedLoading[i]) {
                out.add(slots[i]);
            }
        }
        return out;
    }

    public synchronized List<GameSession> membersLoaded() {
        List<GameSession> out = new ArrayList<>(MAX_MEMBERS);
        for (int i = 0; i < MAX_MEMBERS; i++) {
            if (slots[i] != null && finishedLoading[i]) {
                out.add(slots[i]);
            }
        }
        return out;
    }

    public synchronized void setAllAlive() {
        java.util.Arrays.fill(alive, true);
        java.util.Arrays.fill(enteredMap, true);
        int count = memberCount();
        routingTurn = count <= 1 ? 0 : java.util.concurrent.ThreadLocalRandom.current()
                .nextInt(count);
    }

    public synchronized int nextRoutingTurn(int memberCount) {
        int next = routingTurn + 1;
        return memberCount <= next ? 0 : next;
    }

    public synchronized void setRoutingTurn(int seq) {
        routingTurn = seq;
    }

    public synchronized boolean aliveAt(int slot) {
        return slot >= 0 && slot < MAX_MEMBERS && slots[slot] != null && alive[slot];
    }

    public synchronized boolean enteredMapAt(int slot) {
        return slot >= 0 && slot < MAX_MEMBERS && slots[slot] != null && enteredMap[slot];
    }

    public synchronized int liveCountEnterMap() {
        int count = 0;
        for (int i = 0; i < MAX_MEMBERS; i++) {
            if (slots[i] != null && enteredMap[i]) {
                count++;
            }
        }
        return count;
    }

    public synchronized int setCharacLive(GameSession s, int state) {
        int slot = slotOf(s);
        if (slot < 0) {
            return -1;
        }
        if (state == com.dylan.protocol.body.Noti32CharacLiveBody.STATE_DEAD) {
            alive[slot] = false;
        } else {
            alive[slot] = true;
            enteredMap[slot] = true;
            if (state == com.dylan.protocol.body.Noti32CharacLiveBody.STATE_ALIVE) {
                memberUseCoin[slot]++;
            }
        }
        return slot;
    }

    public synchronized boolean checkAllMemberDie() {
        for (int i = 0; i < MAX_MEMBERS; i++) {
            if (slots[i] != null && alive[i]) {
                return false;
            }
        }
        return true;
    }

    public synchronized int usedCoinCount() {
        return usedCoinCount;
    }

    public synchronized void increaseUsedCoinCount() {
        usedCoinCount++;
    }

    public synchronized void resetUsedCoin() {
        java.util.Arrays.fill(memberUseCoin, 0);
        usedCoinCount = 0;
    }

    public synchronized boolean recordSkillUse(GameSession s, int job, int skillId) {
        int slot = slotOf(s);
        if (slot < 0) {
            return false;
        }
        skillUse[slot]++;
        boolean effective = isCheckUseSkill(job, skillId);
        if (effective) {
            validSkillUse[slot]++;
        }
        return effective;
    }

    public static boolean isCheckUseSkill(int job, int skillId) {
        if (skillId > 168) {
            return false;
        }
        return switch (job) {
            case 0 -> skillId != 46;
            case 1 -> skillId != 5;
            case 2 -> skillId != 4;
            case 3 -> skillId != 12;
            case 4 -> skillId != 1;
            case 5 -> skillId != 4;
            case 6 -> skillId != 1;
            case 7 -> skillId != 5;
            default -> true;
        };
    }

    public synchronized int worldMapIndex() {
        return worldMapIndex;
    }

    public synchronized void worldMapIndex(int value) {
        this.worldMapIndex = value;
    }

    public synchronized double hellPartyValue() {
        return hellPartyValue;
    }

    public synchronized void hellPartyValue(double value) {
        this.hellPartyValue = value;
    }

    private boolean hellPartyEnabled;

    public synchronized boolean hellPartyEnabled() {
        return hellPartyEnabled;
    }

    public synchronized void hellPartyEnabled(boolean value) {
        this.hellPartyEnabled = value;
    }

    public synchronized boolean hellPassChecked() {
        return hellPassChecked;
    }

    public synchronized void hellPassChecked(boolean value) {
        this.hellPassChecked = value;
    }

    private int dungeonType;

    public synchronized int dungeonType() {
        return dungeonType;
    }

    public synchronized void dungeonType(int value) {
        this.dungeonType = value;
    }

    private boolean quickParty;

    public synchronized boolean quickParty() {
        return quickParty;
    }

    public synchronized void quickParty(boolean value) {
        this.quickParty = value;
    }

    public int index() {
        return partyIndex;
    }

    public synchronized int memberCount() {
        int n = 0;
        for (GameSession s : slots) {
            if (s != null) {
                n++;
            }
        }
        return n;
    }

    public synchronized boolean hasRoom() {
        return memberCount() < capacity;
    }

    public synchronized boolean isManager(GameSession s) {
        return manager != null && manager == s;
    }

    public synchronized boolean contains(GameSession s) {
        return slotOf(s) >= 0;
    }

    public synchronized GameSession memberAt(int slot) {
        return slot < 0 || slot >= MAX_MEMBERS ? null : slots[slot];
    }

    public synchronized int slotOf(GameSession s) {
        for (int i = 0; i < MAX_MEMBERS; i++) {
            if (slots[i] == s) {
                return i;
            }
        }
        return -1;
    }

    public synchronized int join(GameSession s) {
        if (state == DungeonInstance.STATE_IN_DUNGEON) {
            return JOIN_IN_DUNGEON;
        }
        if (slotOf(s) >= 0) {
            return JOIN_ALREADY_IN;
        }
        if (!hasRoom()) {
            return JOIN_FULL;
        }
        for (int i = 0; i < MAX_MEMBERS; i++) {
            if (slots[i] == null) {
                slots[i] = s;
                alive[i] = false;
                enteredMap[i] = false;
                return JOIN_OK;
            }
        }
        return JOIN_FULL;
    }

    public synchronized Departure leave(GameSession s) {
        int slot = slotOf(s);
        if (slot < 0) {
            return null;
        }
        boolean disbanded = memberCount() <= 1;
        boolean wasManager = manager == s;
        slots[slot] = null;
        if (wasManager) {
            manager = null;
        }
        boolean managerChanged = false;
        if (disbanded) {
            for (int i = 0; i < MAX_MEMBERS; i++) {
                slots[i] = null;
            }
            manager = null;
        } else if (wasManager) {
            for (int i = 0; i < MAX_MEMBERS; i++) {
                if (slots[i] != null) {
                    manager = slots[i];
                    managerChanged = true;
                    break;
                }
            }
        }
        return new Departure(slot, wasManager, managerChanged, disbanded, snapshot());
    }

    public synchronized View header(int titleKind, String title, int capacity) {
        this.titleKind = titleKind;
        this.title = title == null ? "" : title;
        this.capacity = clampCapacity(capacity);
        return snapshot();
    }

    public synchronized View view() {
        return snapshot();
    }

    private View snapshot() {
        List<GameSession> copy = new ArrayList<>(MAX_MEMBERS);
        Collections.addAll(copy, slots);
        return new View(partyIndex, titleKind, title, capacity,
                copy, manager == null ? -1 : indexOf(manager));
    }

    private int indexOf(GameSession s) {
        for (int i = 0; i < MAX_MEMBERS; i++) {
            if (slots[i] == s) {
                return i;
            }
        }
        return -1;
    }

    private static int clampCapacity(int value) {
        return Math.max(1, Math.min(MAX_MEMBERS, value));
    }

    public record Departure(int slot, boolean wasManager, boolean managerChanged,
                            boolean disbanded, View view) {
    }

    public record View(int partyIndex, int titleKind, String title, int capacity,
                       List<GameSession> slots, int managerSlot) {
        public View {
            slots = Collections.unmodifiableList(new ArrayList<>(slots));
        }

        public List<GameSession> members() {
            List<GameSession> out = new ArrayList<>(MAX_MEMBERS);
            for (GameSession s : slots) {
                if (s != null) {
                    out.add(s);
                }
            }
            return out;
        }

        public int size() {
            return members().size();
        }

        public boolean empty() {
            return size() == 0;
        }

        public int slotOfUserId(int userId) {
            for (int i = 0; i < slots.size(); i++) {
                GameSession s = slots.get(i);
                if (s != null && s.uniqueId() == userId) {
                    return i;
                }
            }
            return -1;
        }

        public GameSession at(int slot) {
            return slot < 0 || slot >= slots.size() ? null : slots.get(slot);
        }

        public GameSession manager() {
            return at(managerSlot);
        }

        public int hostSlot() {
            return Math.max(0, managerSlot);
        }
    }
}
