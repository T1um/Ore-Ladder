package com.oreladder;

/** Порядок констант = порядок прохождения. Чтобы поменять порядок руд — просто переставь строки. */
public enum Ore {
    COAL("Уголь", "Coal", false),
    COPPER("Медь", "Copper", false),
    IRON("Железо", "Iron", false),
    GOLD("Золото", "Gold", false),
    NETHER_GOLD("Золото Незера (можно пропустить)", "Nether Gold (optional)", true),
    REDSTONE("Редстоун", "Redstone", false),
    QUARTZ("Кварц", "Quartz", false),
    LAPIS("Лазурит", "Lapis", false),
    DIAMOND("Алмаз", "Diamond", false),
    EMERALD("Изумруд", "Emerald", false),
    NETHERITE("Незерит (древние обломки)", "Netherite (ancient debris)", false);

    public final String ru, en;
    public final boolean optional;

    Ore(String ru, String en, boolean optional) {
        this.ru = ru;
        this.en = en;
        this.optional = optional;
    }

    public String label(boolean english) {
        return english ? en : ru;
    }
}
