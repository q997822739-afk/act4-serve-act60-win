package com.dylan.game;

import com.dylan.protocol.body.ItemListType;
import com.dylan.store.blob.InvenItem;
import com.dylan.store.blob.ItemArray;

public final class EnchantMission {
    public record Material(GameSession owner, int itemSpace, int slot, int itemId) {
        public static final Material EMPTY = new Material(null, 0, 0, 0);

        public boolean isEmpty() {
            return owner == null || itemId == 0;
        }
    }

    private final GameSession enchanter;
    private final GameSession beneficiary;
    private final int enchanterUniqueId;
    private final int beneficiaryUniqueId;
    private final boolean useOtherUserMonsterCard;

    private long money;
    private Material equipment = Material.EMPTY;
    private Material card = Material.EMPTY;
    private long recipeItemId;

    EnchantMission(GameSession enchanter, GameSession beneficiary,
                   boolean useOtherUserMonsterCard) {
        this.enchanter = enchanter;
        this.beneficiary = beneficiary;
        this.enchanterUniqueId = enchanter == null ? 0 : enchanter.uniqueId();
        this.beneficiaryUniqueId = beneficiary == null ? 0 : beneficiary.uniqueId();
        this.useOtherUserMonsterCard = useOtherUserMonsterCard;
    }

    public GameSession enchanter() {
        return enchanter;
    }

    public GameSession beneficiary() {
        return beneficiary;
    }

    public boolean solo() {
        return enchanter != null && enchanter == beneficiary;
    }

    public boolean useOtherUserMonsterCard() {
        return useOtherUserMonsterCard;
    }

    public GameSession other(GameSession side) {
        if (solo()) {
            return null;
        }
        if (side == enchanter) {
            return beneficiary;
        }
        if (side == beneficiary) {
            return enchanter;
        }
        return null;
    }

    public long money() {
        return money;
    }

    void money(long value) {
        this.money = value;
    }

    public Material equipment() {
        return equipment;
    }

    public Material card() {
        return card;
    }

    public long recipeItemId() {
        return recipeItemId;
    }

    void recipeItemId(long value) {
        this.recipeItemId = value;
    }

    void equipment(Material value) {
        this.equipment = value == null ? Material.EMPTY : value;
    }

    void card(Material value) {
        this.card = value == null ? Material.EMPTY : value;
    }

    void resetCard() {
        this.card = Material.EMPTY;
        this.recipeItemId = 0;
    }

    static int checkStillRight(Material material) {
        if (material == null || material.isEmpty()) {
            return com.dylan.protocol.body.EnchantError.WRONG_MATERIAL;
        }
        GameSession owner = material.owner();
        if (owner == null || !owner.hasCharacter()) {
            return com.dylan.protocol.body.EnchantError.NOT_YOURS;
        }
        if (material.itemSpace() != ItemListType.LIST_MAIN) {
            return com.dylan.protocol.body.EnchantError.WRONG_MATERIAL;
        }
        InvenItem now = itemAt(owner, material.itemSpace(), material.slot());
        if (now == null || now.isEmpty() || now.itemId() != material.itemId()) {
            return com.dylan.protocol.body.EnchantError.WRONG_MATERIAL;
        }
        return com.dylan.protocol.body.EnchantError.NONE;
    }

    static InvenItem itemAt(GameSession owner, int itemSpace, int slot) {
        if (owner == null || owner.inventory() == null) {
            return null;
        }
        ItemArray array = owner.inventory().containerOf(itemSpace);
        if (array == null || slot < 0 || slot >= array.slots()) {
            return null;
        }
        return array.get(slot);
    }

    @Override
    public String toString() {
        return "EnchantMission[" + (solo() ? "给自己附魔 " + enchanterUniqueId
                : "附魔师 " + enchanterUniqueId + " → 受惠者 " + beneficiaryUniqueId)
                + (useOtherUserMonsterCard ? "，卡由受惠者出" : "，卡由附魔师出")
                + "，配方 " + recipeItemId + "，报酬 " + money + "]";
    }
}
