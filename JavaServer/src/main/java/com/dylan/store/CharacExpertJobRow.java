package com.dylan.store;

public record CharacExpertJobRow(int characNo, int giveupCount, int info, int infoEx,
                                 RecipeBitmap recipes) {
    public static CharacExpertJobRow fresh(int characNo) {
        return new CharacExpertJobRow(characNo, 0, 0, 0, RecipeBitmap.empty());
    }

    public CharacExpertJobRow withRecipes(RecipeBitmap next) {
        return new CharacExpertJobRow(characNo, giveupCount, info, infoEx, next);
    }
}
