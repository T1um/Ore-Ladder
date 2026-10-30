package com.oreladder;

/** Битовые маски ограничений и «цен» заданий. */
public final class Rule {
    private Rule() {}
    // ограничения (нарушил = провал)
    public static final int NO_BED = 1, NO_JUMP = 2, NO_FOOD = 4, NO_ARMOR = 8, NO_SHIELD = 16,
            NO_ARROW = 32, NO_DAMAGE = 64, NO_SPRINT = 128, NO_DEATH = 256;
    // цена
    public static final int HALF_HEARTS = 1; // пока идёт задание — только 5 сердец
    public static final int HALF_ITEMS = 2;  // после выполнения — теряешь половину вещей навсегда
}
