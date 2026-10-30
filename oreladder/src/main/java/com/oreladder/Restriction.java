package com.oreladder;

/** Постоянные ограничения режима «Очень Экстримальный». Выдаются СЛУЧАЙНО за каждую руду. */
public enum Restriction {
    HUNGER4("hunger4",
            "Урезанный голод", "Шкала голода урезана на 4 единицы: насытиться можно максимум на 16.",
            "Shrunken hunger", "Your hunger bar is cut by 4 points: max food is 16."),
    DARKNESS("darkness",
            "Тьма", "Каждые 5 минут на тебя накладывается эффект Тьмы.",
            "Darkness", "Every 5 minutes you are hit with the Darkness effect."),
    HOTBAR_HALF("hotbar_half",
            "Половина хотбара", "Слоты хотбара 6–9 отобраны: предметы оттуда выталкиваются.",
            "Half a hotbar", "Hotbar slots 6-9 are taken away: items get pushed out of them."),
    HEARTS5("hearts5",
            "Пять сердец", "Максимальное здоровье — 5 сердец. Навсегда (классика).",
            "Five hearts", "Your max health is 5 hearts. Forever (a classic)."),
    SLOW("slow",
            "Вечная медлительность", "Ты всегда двигаешься медленнее.",
            "Eternal slowness", "You always move slower."),
    OFFHAND("offhand",
            "Нет второй руки", "Вторая (левая) рука заблокирована, как в старых версиях: щит и предметы в ней использовать нельзя.",
            "No second hand", "Your off-hand is blocked like in old versions: no shield or off-hand items."),
    JITTER("jitter",
            "Скачущий FPS", "Раз в минуту в случайный момент лимит FPS меняется на случайный от 5 до 40.",
            "Jumpy FPS", "Once a minute, at a random moment, your FPS cap changes to a random value from 5 to 40."),
    LEATHER("leather",
            "Кожаный дресс-код", "В слотах брони разрешена только кожаная броня (комплект выдан).",
            "Leather dress code", "Only leather armor is allowed in armor slots (you get a set)."),
    NO_SLEEP("no_sleep",
            "Бессонница", "Спать нельзя никогда. (Остальные игроки со своими ограничениями спят как хотят.)",
            "Insomnia", "You can never sleep. (Other players have their own restrictions.)"),
    BOW_ONLY("bow_only",
            "Только стрелы", "Все мечи и топоры отбираются. Основное оружие — лук/арбалет (выдан лук и стрелы).",
            "Arrows only", "All swords and axes are taken. Your main weapon is a bow/crossbow (bow and arrows given)."),
    HARD("hard",
            "Сложно", "Сложность мира принудительно «Сложно» и заблокирована.",
            "Hard", "World difficulty is forced to Hard and locked."),
    NO_DIAMOND("no_diamond",
            "Без алмазной экипировки", "Алмазная и незеритовая броня, мечи, топоры, лопаты и мотыги исчезают (кирка разрешена, иначе незерит недостижим).",
            "No diamond gear", "Diamond/netherite armor, swords, axes, shovels and hoes vanish (pickaxes allowed, or netherite would be impossible)."),
    HOTBAR_ONLY("hotbar_only",
            "Только хотбар", "Все слоты инвентаря, кроме хотбара, запрещены.",
            "Hotbar only", "Every inventory slot except the hotbar is forbidden."),
    NIGHT_ONLY("night_only",
            "Дитя ночи", "Днём под открытым небом ты получаешь урон от солнца. Выходи только ночью.",
            "Child of the night", "In daylight under open sky the sun hurts you. Go outside only at night."),
    VEGETARIAN("vegetarian",
            "Вегетарианец", "Мясо и рыбу есть нельзя.",
            "Vegetarian", "You cannot eat meat or fish."),
    GOLD_ONLY("gold_only",
            "Только золото", "Разрешены только золотая броня и золотые мечи/топоры/лопаты/мотыги (кирки — любые).",
            "Gold only", "Only golden armor and golden swords/axes/shovels/hoes are allowed (any pickaxe is fine)."),
    PLANKS("planks",
            "Пол — это доски", "Стоять можно только на досках (и плитах/ступенях из дерева). На другой земле — урон.",
            "Planks only", "You may only stand on planks (and wooden slabs/stairs). Any other ground hurts."),
    CARNIVORE("carnivore",
            "Хищник", "Есть можно только мясо и рыбу.",
            "Carnivore", "You can only eat meat and fish.");

    public final String id, titleRu, descRu, titleEn, descEn;

    Restriction(String id, String titleRu, String descRu, String titleEn, String descEn) {
        this.id = id;
        this.titleRu = titleRu;
        this.descRu = descRu;
        this.titleEn = titleEn;
        this.descEn = descEn;
    }

    public String title(boolean en) { return en ? titleEn : titleRu; }
    public String desc(boolean en) { return en ? descEn : descRu; }

    public static Restriction byId(String id) {
        for (Restriction r : values()) if (r.id.equals(id)) return r;
        return null;
    }

    /** Взаимоисключающие ограничения. */
    public boolean excludes(Restriction o) {
        return (this == LEATHER && o == GOLD_ONLY) || (this == GOLD_ONLY && o == LEATHER)
                || (this == VEGETARIAN && o == CARNIVORE) || (this == CARNIVORE && o == VEGETARIAN);
    }

    /** Ограничения, при которых задание невыполнимо. */
    public boolean conflicts(Task t) {
        switch (this) {
            case LEATHER: return (t.rules & Rule.NO_ARMOR) != 0;
            case GOLD_ONLY:
            case BOW_ONLY: return t.goal == Goal.SKELETON_IRON;
            case PLANKS: return t.goal == Goal.WALK;
            default: return false;
        }
    }
}
