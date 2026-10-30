package com.oreladder;

import java.util.ArrayList;
import java.util.List;

import static com.oreladder.Rule.*;

public enum Task {
    // ===== Уголь / Coal =====
    COAL_NIGHT("coal_night", Ore.COAL, 2.5, Goal.NIGHT, 0, "", NO_BED | NO_DEATH, HALF_HEARTS,
            "Первая ночь без кровати", "Доживи до рассвета: не крафти, не ставь кровать и не спи. Пока идёт задание — у тебя только 5 сердец.",
            "First night without a bed", "Survive until dawn: don't craft, place or use a bed. While the task is active you only have 5 hearts."),
    COAL_NOJUMP("coal_nojump", Ore.COAL, 5, Goal.NIGHT, 0, "", NO_JUMP | NO_DEATH, 0,
            "Ночь без прыжков", "Доживи до рассвета, ни разу не прыгнув. Ступеньки и плиты — твои друзья.",
            "Night without jumping", "Survive until dawn without jumping once. Stairs and slabs are your friends."),
    COAL_HUNGER("coal_hunger", Ore.COAL, 15, Goal.TIMER_ANIMAL, 24000, "", NO_FOOD | NO_DEATH, 0,
            "Сутки на голодном пайке", "Найди любое существо (подойди ближе 4 блоков) и проживи целые игровые сутки (20 минут) без еды.",
            "A day on hunger rations", "Find any mob (get within 4 blocks) and survive a whole in-game day (20 minutes) without eating."),

    // ===== Медь / Copper =====
    COPPER_BEDS("copper_beds", Ore.COPPER, 7.5, Goal.BEDS, 5, "", 0, 0,
            "Королевская спальня", "Скрафти 5 кроватей РАЗНЫХ цветов.",
            "Royal bedroom", "Craft 5 beds of DIFFERENT colors."),
    COPPER_BIOME("copper_biome", Ore.COPPER, 5, Goal.BIOME, 0, "", 0, HALF_ITEMS,
            "Дальняя дорога", "Дойди до любого другого биома. Цена: после выполнения половина твоих вещей исчезнет навсегда.",
            "Long road", "Reach any other biome. Price: once done, half of your items vanish forever."),
    COPPER_WALK("copper_walk", Ore.COPPER, 7.5, Goal.WALK, 1000, "", NO_SPRINT | NO_DEATH, 0,
            "Пешком, не бегом", "Пройди 1000 блоков пешком, ни разу не спринтуя.",
            "Walk, don't run", "Walk 1000 blocks on foot without ever sprinting."),

    // ===== Железо / Iron =====
    IRON_SKELETON("iron_skeleton", Ore.IRON, 10, Goal.SKELETON_IRON, 0, "", NO_SHIELD | NO_ARROW | NO_ARMOR, 0,
            "Дуэлянт", "Убей скелета в ближнем бою ЖЕЛЕЗНЫМ мечом. Без щита, без урона от стрел и без брони любого типа (10 сек, чтобы её снять).",
            "Duelist", "Kill a skeleton in melee with an IRON sword. No shield, no arrow damage, no armor of any kind (10 seconds to take it off)."),
    IRON_SCRATCH("iron_scratch", Ore.IRON, 7.5, Goal.TIMER, 12000, "", NO_DAMAGE | NO_DEATH, 0,
            "Ни царапины", "Проживи 10 минут, не получив ни единой единицы урона.",
            "Not a scratch", "Survive 10 minutes without taking any damage."),
    IRON_FISTS("iron_fists", Ore.IRON, 7.5, Goal.KILL_FISTS, 3, "zombie", 0, 0,
            "Кулачный боец", "Убей 3 зомби голыми руками (пустая основная рука).",
            "Brawler", "Kill 3 zombies with bare hands (empty main hand)."),

    // ===== Золото / Gold =====
    GOLD_PIGLIN("gold_piglin", Ore.GOLD, 10, Goal.PIGLIN, 0, "", 0, 0,
            "Торговля без золота на теле", "Обменяй золотой слиток у пиглина, НЕ надев ни одной золотой вещи брони. Торговля в золотой броне — провал.",
            "Barter without gold on you", "Trade a gold ingot with a piglin WITHOUT wearing any golden armor. Trading in golden armor fails the task."),
    GOLD_ZOMBIFIED("gold_zombified", Ore.GOLD, 7.5, Goal.KILL, 5, "zombified_piglin", NO_ARMOR, 0,
            "Охота на свинозомби", "Убей 5 зомбированных пиглинов без брони (10 сек, чтобы её снять).",
            "Zombified hunt", "Kill 5 zombified piglins without armor (10 seconds to take it off)."),
    GOLD_FAST("gold_fast", Ore.GOLD, 5, Goal.TIMER, 12000, "", NO_FOOD | NO_DEATH, 0,
            "Пост", "Проживи 10 минут, ничего не съев.",
            "Fasting", "Survive 10 minutes without eating anything."),

    // ===== Золото Незера (можно пропустить) / Nether Gold (optional) =====
    NG_CALM("ng_calm", Ore.NETHER_GOLD, 5, Goal.TIMER, 6000, "", NO_DEATH, 0,
            "Пять минут в аду", "Проживи 5 минут, не умерев.",
            "Five minutes in hell", "Stay alive for 5 minutes."),
    NG_PIGS("ng_pigs", Ore.NETHER_GOLD, 5, Goal.KILL, 3, "zombified_piglin", 0, 0,
            "Золотая лихорадка", "Убей 3 зомбированных пиглинов.",
            "Gold rush", "Kill 3 zombified piglins."),

    // ===== Редстоун / Redstone =====
    RED_NAKED("red_naked", Ore.REDSTONE, 10, Goal.TIMER, 24000, "", NO_ARMOR | NO_DEATH, 0,
            "Сутки нагишом", "Проживи игровые сутки (20 минут) без брони любого типа.",
            "A day in the buff", "Survive an in-game day (20 minutes) without any armor."),
    RED_CREEPERS("red_creepers", Ore.REDSTONE, 5, Goal.KILL, 3, "creeper", NO_SHIELD, 0,
            "Сапёр", "Убей 3 криперов, не пользуясь щитом.",
            "Sapper", "Kill 3 creepers without using a shield."),
    RED_STILL("red_still", Ore.REDSTONE, 5, Goal.TIMER, 12000, "", NO_JUMP | NO_SPRINT | NO_DEATH, 0,
            "Тише воды", "10 минут без прыжков и бега.",
            "Still waters", "10 minutes without jumping or sprinting."),

    // ===== Кварц / Quartz =====
    QZ_FORTRESS("qz_fortress", Ore.QUARTZ, 10, Goal.BLAZE_FORTRESS, 0, "", 0, 0,
            "Крепость и ифрит", "Найди крепость Незера и убей блейза внутри неё.",
            "Fortress and blaze", "Find a Nether Fortress and kill a blaze inside it."),
    QZ_ALLMOBS("qz_allmobs", Ore.QUARTZ, 15, Goal.KILL_ALL_NETHER, 0, "", 0, 0,
            "Чистка ада", "Убей по одному каждого обитателя Незера: зомбированный пиглин, пиглин, хоглин, гаст, лавовый куб, скелет-иссушитель, блейз, лавоход.",
            "Nether purge", "Kill one of every Nether resident: zombified piglin, piglin, hoglin, ghast, magma cube, wither skeleton, blaze, strider."),
    QZ_BASTION("qz_bastion", Ore.QUARTZ, 7.5, Goal.BASTION, 0, "", 0, 0,
            "Найти бастион", "Просто дойди до бастиона (внутрь структуры).",
            "Find a bastion", "Just reach a bastion (get inside the structure)."),

    // ===== Лазурит / Lapis =====
    LAPIS_TRADES("lapis_trades", Ore.LAPIS, 5, Goal.TRADES, 3, "", 0, 0,
            "Торговец", "Соверши 3 обмена с жителями.",
            "Merchant", "Make 3 trades with villagers."),
    LAPIS_ENDERMAN("lapis_enderman", Ore.LAPIS, 10, Goal.KILL, 1, "enderman", NO_SHIELD, 0,
            "Не смотри ему в глаза", "Убей эндермена, не пользуясь щитом.",
            "Don't look him in the eye", "Kill an enderman without using a shield."),
    LAPIS_HEARTS("lapis_hearts", Ore.LAPIS, 10, Goal.TIMER, 12000, "", NO_DEATH, HALF_HEARTS,
            "Хрупкая жизнь", "Проживи 10 минут, не умерев. Пока идёт задание — только 5 сердец.",
            "Fragile life", "Survive 10 minutes without dying. Only 5 hearts while the task is active."),

    // ===== Алмаз / Diamond =====
    DIA_BLAZE("dia_blaze", Ore.DIAMOND, 10, Goal.KILL, 5, "blaze", NO_SHIELD, 0,
            "Огненный охотник", "Убей 5 блейзов, не пользуясь щитом.",
            "Fire hunter", "Kill 5 blazes without using a shield."),
    DIA_WITHER("dia_wither", Ore.DIAMOND, 10, Goal.KILL, 2, "wither_skeleton", NO_SHIELD, 0,
            "Чёрные кости", "Убей 2 скелетов-иссушителей, не пользуясь щитом.",
            "Black bones", "Kill 2 wither skeletons without using a shield."),
    DIA_UNTOUCHED("dia_untouched", Ore.DIAMOND, 12.5, Goal.TIMER, 18000, "", NO_DAMAGE | NO_DEATH, 0,
            "Неприкосновенный", "Проживи 15 минут без единой единицы урона.",
            "Untouchable", "Survive 15 minutes without taking any damage."),

    // ===== Изумруд / Emerald =====
    EME_PILLAGER("eme_pillager", Ore.EMERALD, 10, Goal.KILL, 3, "pillager", NO_SHIELD, 0,
            "Налётчик на налётчиков", "Убей 3 разбойников, не пользуясь щитом.",
            "Raider of raiders", "Kill 3 pillagers without using a shield."),
    EME_FAST("eme_fast", Ore.EMERALD, 15, Goal.TIMER, 24000, "", NO_FOOD | NO_DEATH, 0,
            "Большая голодовка", "Проживи игровые сутки (20 минут), ничего не съев.",
            "The great fast", "Survive an in-game day (20 minutes) without eating anything."),
    EME_TRADES("eme_trades", Ore.EMERALD, 7.5, Goal.TRADES, 5, "", NO_ARMOR, 0,
            "Бизнесмен в рубашке", "Соверши 5 обменов с жителями без брони (10 сек, чтобы её снять).",
            "Businessman in a shirt", "Make 5 villager trades without armor (10 seconds to take it off)."),

    // ===== Незерит / Netherite =====
    NETH_GHAST("neth_ghast", Ore.NETHERITE, 12.5, Goal.KILL, 1, "ghast", NO_SHIELD, 0,
            "Охотник на плакс", "Убей гаста, не пользуясь щитом.",
            "Crybaby hunter", "Kill a ghast without using a shield."),
    NETH_WITHER("neth_wither", Ore.NETHERITE, 15, Goal.KILL, 3, "wither_skeleton", NO_ARMOR, 0,
            "Голый против иссушителей", "Убей 3 скелетов-иссушителей без брони (10 сек, чтобы её снять).",
            "Naked vs. withers", "Kill 3 wither skeletons without armor (10 seconds to take it off)."),
    NETH_CALM("neth_calm", Ore.NETHERITE, 15, Goal.TIMER, 12000, "", NO_DAMAGE | NO_DEATH, HALF_HEARTS,
            "Хрупкий и неуязвимый", "10 минут без единой единицы урона и только с 5 сердцами.",
            "Fragile and untouchable", "10 minutes without any damage and with only 5 hearts.");

    public final String id;
    public final Ore ore;
    public final double reward;
    public final Goal goal;
    public final int arg;
    public final String entity;
    public final int rules;
    public final int costs;
    public final String titleRu, descRu, titleEn, descEn;

    Task(String id, Ore ore, double reward, Goal goal, int arg, String entity, int rules, int costs,
         String titleRu, String descRu, String titleEn, String descEn) {
        this.id = id;
        this.ore = ore;
        this.reward = reward;
        this.goal = goal;
        this.arg = arg;
        this.entity = entity;
        this.rules = rules;
        this.costs = costs;
        this.titleRu = titleRu;
        this.descRu = descRu;
        this.titleEn = titleEn;
        this.descEn = descEn;
    }

    public String title(boolean en) { return en ? titleEn : titleRu; }
    public String desc(boolean en) { return en ? descEn : descRu; }

    public static Task byId(String id) {
        if (id == null || id.isEmpty()) return null;
        for (Task t : values()) if (t.id.equals(id)) return t;
        return null;
    }

    public static List<Task> forOre(Ore ore) {
        List<Task> out = new ArrayList<>();
        for (Task t : values()) if (t.ore == ore) out.add(t);
        return out;
    }
}
