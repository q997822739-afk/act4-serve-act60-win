package com.dylan.store;

import com.dylan.net.ServerConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.Closeable;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class GameStore implements Closeable {
    private final HikariDataSource dataSource;

    private final MailStore mail;
    private final DeathTowerStore deathTower;
    private final EventStore event;
    private final QuestStore quest;
    private final PremiumStore premium;
    private final CreatureStore creature;
    private final AvatarStore avatar;
    private final GmStore gm;
    private final SecurityStore security;
    private final SkillStore skill;
    private final PvpStore pvp;
    private final DungeonClearStore dungeonClear;
    private final GameOptionStore gameOption;
    private final CeraStore cera;
    private final InventoryStore inventory;
    private final AccountStore account;
    private final CharacterStore character;
    private final GrowthTableStore growthTable;
    private final DailyGrantStore dailyGrant;
    private final GuildStore guild;
    private final MentorStore mentor;
    private final FriendStore friend;
    private final ElixirStore elixir;
    private final ContinuousItemStore continuousItem;
    private final ExpertJobStore expertJob;
    private final EmotionStore emotion;
    private final ItemLockStore itemLock;
    private final SlangStore slang;
    private final StaminaStore stamina;

    private GameStore(HikariDataSource dataSource) {
        this.dataSource = dataSource;
        this.mail = new MailStore(dataSource);
        this.deathTower = new DeathTowerStore(dataSource);
        this.event = new EventStore(dataSource);
        this.quest = new QuestStore(dataSource);
        this.premium = new PremiumStore(dataSource);
        this.creature = new CreatureStore(dataSource);
        this.avatar = new AvatarStore(dataSource);
        this.gm = new GmStore(dataSource);
        this.security = new SecurityStore(dataSource);
        this.skill = new SkillStore(dataSource);
        this.pvp = new PvpStore(dataSource);
        this.dungeonClear = new DungeonClearStore(dataSource);
        this.gameOption = new GameOptionStore(dataSource);
        this.cera = new CeraStore(dataSource);
        this.inventory = new InventoryStore(dataSource);
        this.account = new AccountStore(dataSource);
        this.character = new CharacterStore(dataSource);
        this.growthTable = new GrowthTableStore(dataSource);
        this.dailyGrant = new DailyGrantStore(dataSource);
        this.guild = new GuildStore(dataSource);
        this.mentor = new MentorStore(dataSource);
        this.friend = new FriendStore(dataSource);
        this.elixir = new ElixirStore(dataSource);
        this.continuousItem = new ContinuousItemStore(dataSource);
        this.expertJob = new ExpertJobStore(dataSource);
        this.emotion = new EmotionStore(dataSource);
        this.itemLock = new ItemLockStore(dataSource);
        this.slang = new SlangStore(dataSource);
        this.stamina = new StaminaStore(dataSource);
    }

    public EventStore event() {
        return event;
    }

    public MailStore mail() {
        return mail;
    }

    public DeathTowerStore deathTower() {
        return deathTower;
    }

    public QuestStore quest() {
        return quest;
    }

    public PremiumStore premium() {
        return premium;
    }

    public CreatureStore creature() {
        return creature;
    }

    public AvatarStore avatar() {
        return avatar;
    }

    public GmStore gm() {
        return gm;
    }

    public SecurityStore security() {
        return security;
    }

    public SkillStore skill() {
        return skill;
    }

    public PvpStore pvp() {
        return pvp;
    }

    public DungeonClearStore dungeonClear() {
        return dungeonClear;
    }

    public GameOptionStore gameOption() {
        return gameOption;
    }

    public CeraStore cera() {
        return cera;
    }

    public InventoryStore inventory() {
        return inventory;
    }

    public AccountStore account() {
        return account;
    }

    public CharacterStore character() {
        return character;
    }

    public GrowthTableStore growthTable() {
        return growthTable;
    }

    public DailyGrantStore dailyGrant() {
        return dailyGrant;
    }

    public GuildStore guild() {
        return guild;
    }

    public FriendStore friend() {
        return friend;
    }

    public MentorStore mentor() {
        return mentor;
    }

    public ElixirStore elixir() {
        return elixir;
    }

    public ContinuousItemStore continuousItem() {
        return continuousItem;
    }

    public ExpertJobStore expertJob() {
        return expertJob;
    }

    public EmotionStore emotion() {
        return emotion;
    }

    public StaminaStore stamina() {
        return stamina;
    }

    public ItemLockStore itemLock() {
        return itemLock;
    }

    public SlangStore slang() {
        return slang;
    }

    public static GameStore open(String url) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(normalizeSqliteUrl(url));
        config.setPoolName("S1A5");
        config.setMaximumPoolSize(ServerConfig.DB_POOL_SIZE);
        config.setConnectionTimeout(ServerConfig.DB_CONNECTION_TIMEOUT_MS);
        config.setLeakDetectionThreshold(ServerConfig.DB_LEAK_DETECTION_MS);
        config.setConnectionInitSql(
                "PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000; PRAGMA foreign_keys=ON");
        HikariDataSource ds = new HikariDataSource(config);
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            for (String ddl : Schema.STATEMENTS) {
                s.execute(ddl);
            }
            for (String ddl : Schema.INDEXES) {
                s.execute(ddl);
            }
            GrowthTableStore.seedMonsterReward(c);
            GrowthTableStore.seedSpReward(c);
            EventStore.seedEventInfo(c);
            SlangStore.seedSlangList(c);
            SlangStore.seedSlangListName(c);
        } catch (SQLException e) {
            ds.close();
            throw new StoreException("建表失败：" + e.getMessage(), e);
        }
        return new GameStore(ds);
    }

    private static String normalizeSqliteUrl(String url) {
        String prefix = "jdbc:sqlite:";
        if (!url.startsWith(prefix) || url.startsWith(prefix + ":memory:")) {
            return url;
        }
        String path = url.substring(prefix.length()).replace('\\', '/');
        java.nio.file.Path parent = java.nio.file.Path.of(path).getParent();
        if (parent != null) {
            try {
                java.nio.file.Files.createDirectories(parent);
            } catch (java.io.IOException e) {
                throw new StoreException("建不出库文件的目录 " + parent + "：" + e.getMessage(), e);
            }
        }
        return prefix + path;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
