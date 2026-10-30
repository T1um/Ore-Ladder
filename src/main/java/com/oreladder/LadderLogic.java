package com.oreladder;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureKeys;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class LadderLogic {
    public static final Ore[] ORES = Ore.values();
    public static final int TOTAL = ORES.length;

    public static final double FAIL_PENALTY = 15.0;  // −FPS за провал / пропуск
    public static final double COAL_REPAY = 5.0;     // +FPS за блок угля при штрафе
    public static final double MIN_FPS = 5.0;
    public static final long OPTIONAL_GRACE = 6000L; // 5 минут на золото Незера после незерита
    public static final long DRAGON_TICKS = 36000L;  // 30 минут на дракона (с входа в Энд)
    public static final long DELIVERY_TICKS = 36000L; // 30 минут на сдачу после смерти дракона
    /** true: не убил дракона за 30 минут — навсегда 5 FPS (как при несданных предметах). */
    public static final boolean DRAGON_TIMEOUT_CURSE = true;

    static final int F_NIGHT = 1, F_ANIMAL = 2, F_DONE = 4, F_BED = 8, F_ARROW = 16, F_BADTRADE = 32;
    static final int B_JUMP = 0, B_DEATH = 1, B_SPRINT = 2, B_WALK = 3, B_DMG = 4, B_TRADE = 5;

    private static final String[] NETHER_MOBS = {
            "zombified_piglin", "piglin", "hoglin", "ghast", "magma_cube", "wither_skeleton", "blaze", "strider"};

    private static final Identifier HALF_HEARTS_ID = Identifier.of(OreLadder.MOD_ID, "half_hearts");
    private static final Identifier HEARTS5_ID = Identifier.of(OreLadder.MOD_ID, "restr_hearts");
    private static final Identifier SLOW_ID = Identifier.of(OreLadder.MOD_ID, "restr_slow");

    private static final Item[] BEDS = {
            Items.WHITE_BED, Items.ORANGE_BED, Items.MAGENTA_BED, Items.LIGHT_BLUE_BED,
            Items.YELLOW_BED, Items.LIME_BED, Items.PINK_BED, Items.GRAY_BED,
            Items.LIGHT_GRAY_BED, Items.CYAN_BED, Items.PURPLE_BED, Items.BLUE_BED,
            Items.BROWN_BED, Items.GREEN_BED, Items.RED_BED, Items.BLACK_BED};

    // ================= Язык / сообщения =================

    public static boolean en(LadderData.P d) {
        return "en".equals(d.lang);
    }

    public static String tr(LadderData.P d, String ru, String en) {
        return en(d) ? en : ru;
    }

    private static void say(ServerPlayerEntity p, String s, Formatting... f) {
        p.sendMessage(Text.literal(s).formatted(f), false);
    }

    private static String num(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v);
    }

    private static String mmss(long ticks) {
        long s = Math.max(0, ticks) / 20;
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    // ================= FPS =================

    public static double startFps() { return LadderData.mode() == 1 ? 5.0 : 10.0; }
    public static double oreBonus() { return LadderData.mode() == 1 ? 5.0 : 10.0; }

    public static double baseFps(LadderData.P d) {
        return startFps() + oreBonus() * d.oresDone + d.rewards;
    }

    /** 0 = без лимита. */
    public static int fpsCap(LadderData.P d) {
        if (d.finalPhase == 3) return 0;
        if (d.finalPhase == 4) return (int) MIN_FPS;
        int cap = (int) Math.round(Math.max(MIN_FPS, baseFps(d) - d.penalty));
        if (d.jitterCap > 0 && RestrictionLogic.has(d, Restriction.JITTER)) cap = Math.min(cap, d.jitterCap);
        return cap;
    }

    public static String fpsText(int cap) {
        return cap == 0 ? "∞" : cap + " FPS";
    }

    public static void addPenalty(LadderData.P d, double amount) {
        double max = Math.max(0, baseFps(d) - MIN_FPS);
        d.penalty = Math.min(max, d.penalty + amount);
    }

    public static void sync(ServerPlayerEntity p) {
        ServerPlayNetworking.send(p, new FpsPayload(fpsCap(LadderData.get(p))));
    }

    // ================= Помощники =================

    private static int stat(ServerPlayerEntity p, Identifier id) {
        return p.getStatHandler().getStat(Stats.CUSTOM, id);
    }

    private static boolean wearsArmor(ServerPlayerEntity p) {
        return !p.getEquippedStack(EquipmentSlot.HEAD).isEmpty() || !p.getEquippedStack(EquipmentSlot.CHEST).isEmpty()
                || !p.getEquippedStack(EquipmentSlot.LEGS).isEmpty() || !p.getEquippedStack(EquipmentSlot.FEET).isEmpty();
    }

    private static boolean wearsGold(ServerPlayerEntity p) {
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack st = p.getEquippedStack(s);
            if (st.isOf(Items.GOLDEN_HELMET) || st.isOf(Items.GOLDEN_CHESTPLATE)
                    || st.isOf(Items.GOLDEN_LEGGINGS) || st.isOf(Items.GOLDEN_BOOTS)) return true;
        }
        return false;
    }

    private static String biomeId(ServerPlayerEntity p) {
        return p.getServerWorld().getBiome(p.getBlockPos()).getKey().map(k -> k.getValue().toString()).orElse("");
    }

    private static boolean inStructure(ServerWorld w, BlockPos pos, RegistryKey<Structure> key) {
        return w.getStructureAccessor().getStructureContaining(pos, entry -> entry.matchesKey(key)).hasChildren();
    }

    private static int bedsCrafted(ServerPlayerEntity p, LadderData.P d) {
        int n = 0;
        for (int i = 0; i < BEDS.length; i++) {
            if (p.getStatHandler().getStat(Stats.CRAFTED, BEDS[i]) > d.bedBase[i]) n++;
        }
        return n;
    }

    public static int oreIndex(BlockState st) {
        if (st.isOf(Blocks.NETHER_GOLD_ORE)) return Ore.NETHER_GOLD.ordinal();
        if (st.isOf(Blocks.NETHER_QUARTZ_ORE)) return Ore.QUARTZ.ordinal();
        if (st.isOf(Blocks.ANCIENT_DEBRIS)) return Ore.NETHERITE.ordinal();
        if (st.isIn(BlockTags.COAL_ORES)) return Ore.COAL.ordinal();
        if (st.isIn(BlockTags.COPPER_ORES)) return Ore.COPPER.ordinal();
        if (st.isIn(BlockTags.IRON_ORES)) return Ore.IRON.ordinal();
        if (st.isIn(BlockTags.GOLD_ORES)) return Ore.GOLD.ordinal();
        if (st.isIn(BlockTags.REDSTONE_ORES)) return Ore.REDSTONE.ordinal();
        if (st.isIn(BlockTags.LAPIS_ORES)) return Ore.LAPIS.ordinal();
        if (st.isIn(BlockTags.DIAMOND_ORES)) return Ore.DIAMOND.ordinal();
        if (st.isIn(BlockTags.EMERALD_ORES)) return Ore.EMERALD.ordinal();
        return -1;
    }

    // ================= Атрибуты =================

    private static void setMod(ServerPlayerEntity p, RegistryEntry<EntityAttribute> attr, Identifier id,
                               double value, EntityAttributeModifier.Operation op, boolean on) {
        EntityAttributeInstance inst = p.getAttributeInstance(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        if (on) inst.addPersistentModifier(new EntityAttributeModifier(id, value, op));
    }

    public static void applyAttributes(ServerPlayerEntity p) {
        LadderData.P d = LadderData.get(p);
        boolean hearts5 = RestrictionLogic.has(d, Restriction.HEARTS5);
        Task t = Task.byId(d.task);
        boolean half = !hearts5 && t != null && (t.costs & Rule.HALF_HEARTS) != 0;
        setMod(p, EntityAttributes.GENERIC_MAX_HEALTH, HALF_HEARTS_ID, -10.0, EntityAttributeModifier.Operation.ADD_VALUE, half);
        setMod(p, EntityAttributes.GENERIC_MAX_HEALTH, HEARTS5_ID, -10.0, EntityAttributeModifier.Operation.ADD_VALUE, hearts5);
        setMod(p, EntityAttributes.GENERIC_MOVEMENT_SPEED, SLOW_ID, -0.25,
                EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE, RestrictionLogic.has(d, Restriction.SLOW));
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    private static void loseHalfItems(ServerPlayerEntity p) {
        Inventory inv = p.getInventory();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inv.size(); i++) if (!inv.getStack(i).isEmpty()) slots.add(i);
        Collections.shuffle(slots);
        int n = slots.size() / 2;
        for (int i = 0; i < n; i++) inv.setStack(slots.get(i), ItemStack.EMPTY);
    }

    // ================= Приветствие / статус =================

    public static void greet(ServerPlayerEntity p) {
        LadderData.P d = LadderData.get(p);
        Task t = Task.byId(d.task);
        boolean extreme = LadderData.mode() == 1;
        String modeName = extreme ? tr(d, "ОЧЕНЬ ЭКСТРИМАЛЬНЫЙ", "VERY EXTREME") : tr(d, "Нормальный", "Normal");
        say(p, "⛏ ORE LADDER — " + tr(d, "режим: ", "mode: ") + modeName
                + (en(d) ? "  (/ladder lang ru — русский)" : "  (/ladder lang en — English)"), Formatting.GOLD);
        if (d.finalPhase == 4) {
            say(p, tr(d, "☠ Элитры и яйцо не сданы. Лимит навсегда 5 FPS. Избавиться можно только удалив мод.",
                    "☠ Elytra and egg were not delivered. Capped at 5 FPS forever. Only removing the mod frees you."), Formatting.DARK_RED);
        } else if (d.finalPhase == 3) {
            say(p, tr(d, "🏆 Лестница пройдена. Ограничений нет, FPS максимальный.",
                    "🏆 Ladder complete. No restrictions, max FPS."), Formatting.GOLD);
        } else if (t != null) {
            announce(p, d, t);
        } else if (d.finalPhase == 1 || d.finalPhase == 2) {
            say(p, finalText(d), Formatting.GOLD);
        } else if (d.oresDone == 0) {
            say(p, tr(d, "Старт: " + num(startFps()) + " FPS. Сделай деревянную кирку и добудь уголь. Руды строго по порядку: "
                            + "Уголь → Медь → Железо → Золото → (Золото Незера) → Редстоун → Кварц → Лазурит → Алмаз → Изумруд → Незерит. "
                            + "Каждая руда: +" + num(oreBonus()) + " FPS и случайное задание"
                            + (extreme ? " и случайное ограничение НАВСЕГДА." : ".") + " Пропуск руды = урон и −15 FPS.",
                    "Start: " + num(startFps()) + " FPS. Craft a wooden pickaxe and mine coal. Ores must be mined in order: "
                            + "Coal → Copper → Iron → Gold → (Nether Gold) → Redstone → Quartz → Lapis → Diamond → Emerald → Netherite. "
                            + "Each ore: +" + num(oreBonus()) + " FPS and a random task"
                            + (extreme ? " and a random restriction FOREVER." : ".") + " Skipping an ore = damage and −15 FPS."), Formatting.YELLOW);
        } else {
            say(p, tr(d, "Следующая руда: ", "Next ore: ") + ORES[Math.min(d.stage, TOTAL - 1)].label(en(d))
                    + tr(d, ". Лимит: ", ". Cap: ") + fpsText(fpsCap(d)) + ". /ladder", Formatting.YELLOW);
        }
    }

    private static String finalText(LadderData.P d) {
        if (d.finalPhase == 1) {
            return tr(d, "🐉 ФИНАЛ: убей Эндер-дракона за 30 минут (таймер стартует, когда войдёшь в Энд)! "
                            + "Потом у тебя 30 минут: найди ЭЛИТРЫ и положи ЯЙЦО ДРАКОНА и ЭЛИТРЫ в сундук на координатах 0, 0 Верхнего мира. "
                            + "Не успеешь — навсегда 5 FPS. Успеешь — все ограничения снимутся, FPS максимальный.",
                    "🐉 FINALE: kill the Ender Dragon within 30 minutes (timer starts when you enter the End)! "
                            + "Then you have 30 minutes: find ELYTRA and put the DRAGON EGG and ELYTRA into the chest at 0, 0 in the Overworld. "
                            + "Fail — 5 FPS forever. Succeed — all restrictions are lifted and FPS is maxed.");
        }
        BlockPos pos = new BlockPos(0, LadderData.data().chestY, 0);
        return tr(d, "🐉 Дракон убит! Положи ЯЙЦО ДРАКОНА и ЭЛИТРЫ в сундук на координатах X 0, Y " + pos.getY() + ", Z 0 (Верхний мир).",
                "🐉 The dragon is dead! Put the DRAGON EGG and ELYTRA into the chest at X 0, Y " + pos.getY() + ", Z 0 (Overworld).");
    }

    private static void announce(ServerPlayerEntity p, LadderData.P d, Task t) {
        boolean en = en(d);
        say(p, "⚔ " + tr(d, "ЗАДАНИЕ", "TASK") + " [" + t.ore.label(en) + "]: " + t.title(en), Formatting.GOLD, Formatting.BOLD);
        say(p, t.desc(en), Formatting.YELLOW);
        say(p, tr(d, "Награда: +" + num(t.reward) + " FPS | Провал: −" + num(FAIL_PENALTY) + " FPS. Пока не выполнишь — новую руду копать нельзя!",
                "Reward: +" + num(t.reward) + " FPS | Failure: −" + num(FAIL_PENALTY) + " FPS. You can't mine a new ore until it's done!"), Formatting.GRAY);
        if (!d.swapUsed) {
            for (Task o : Task.forOre(t.ore)) {
                if (o == t || !RestrictionLogic.compatible(o, d)) continue;
                MutableText btn = Text.literal("   ➜ [" + tr(d, "Сменить на: ", "Swap to: ") + o.title(en)
                                + " (+" + num(o.reward) + " FPS)] " + tr(d, "— один раз", "— once"))
                        .styled(s -> s.withColor(Formatting.AQUA)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ladder swap " + o.id)));
                p.sendMessage(btn, false);
            }
        }
    }

    public static String progress(ServerPlayerEntity p, LadderData.P d, Task t) {
        switch (t.goal) {
            case NIGHT:
                return (d.flags & F_NIGHT) != 0 ? tr(d, "доживи до рассвета", "survive until dawn")
                        : tr(d, "дождись ночи и доживи до рассвета", "wait for night and survive until dawn");
            case TIMER:
                return tr(d, "осталось ", "left ") + mmss(t.arg - d.age);
            case TIMER_ANIMAL:
                return tr(d, "осталось ", "left ") + mmss(t.arg - d.age)
                        + ((d.flags & F_ANIMAL) != 0 ? tr(d, ", моб найден ✔", ", mob found ✔") : tr(d, ", моб не найден ✖", ", no mob yet ✖"));
            case KILL:
            case KILL_FISTS:
                return d.count + "/" + t.arg;
            case BEDS:
                return bedsCrafted(p, d) + "/" + t.arg;
            case BIOME:
                return tr(d, "дойди до другого биома", "reach another biome");
            case WALK:
                return Math.max(0, (stat(p, Stats.WALK_ONE_CM) - d.base[B_WALK]) / 100) + "/" + t.arg;
            case SKELETON_IRON:
                return tr(d, "убей скелета железным мечом", "kill a skeleton with an iron sword");
            case PIGLIN:
                return tr(d, "обмен с пиглином без золота на теле", "barter with a piglin with no gold on you");
            case TRADES:
                return Math.max(0, stat(p, Stats.TRADED_WITH_VILLAGER) - d.base[B_TRADE]) + "/" + t.arg;
            case BLAZE_FORTRESS:
                return tr(d, "убей блейза в крепости Незера", "kill a blaze in a Nether Fortress");
            case KILL_ALL_NETHER:
                return Integer.bitCount(d.count) + "/" + NETHER_MOBS.length;
            case BASTION:
                return tr(d, "дойди до бастиона", "reach a bastion");
            default:
                return "";
        }
    }

    public static void status(ServerPlayerEntity p) {
        LadderData.P d = LadderData.get(p);
        boolean en = en(d);
        Task t = Task.byId(d.task);
        StringBuilder sb = new StringBuilder("═══ Ore Ladder ═══\n");
        sb.append(tr(d, "Режим: ", "Mode: ")).append(LadderData.mode() == 1 ? tr(d, "ОЧЕНЬ ЭКСТРИМАЛЬНЫЙ", "VERY EXTREME")
                : tr(d, "Нормальный", "Normal")).append('\n');
        sb.append(tr(d, "Лимит: ", "Cap: ")).append(fpsText(fpsCap(d)))
                .append(tr(d, " (база ", " (base ")).append(num(baseFps(d)))
                .append(tr(d, ", штраф −", ", penalty −")).append(num(d.penalty)).append(")\n");
        for (int i = 0; i < TOTAL; i++) {
            boolean skipped = ORES[i].optional && d.skippedOptional && !d.optionalDone;
            sb.append(i < d.stage ? (skipped ? "✖ " : "✔ ") : (i == d.stage ? "➤ " : "· ")).append(ORES[i].label(en));
            if (i < TOTAL - 1) sb.append(" → ");
        }
        sb.append('\n');
        if (t != null) {
            sb.append(tr(d, "Задание: ", "Task: ")).append(t.title(en)).append(" — ").append(progress(p, d, t)).append('\n').append(t.desc(en));
        } else if (d.finalPhase == 1 || d.finalPhase == 2) {
            sb.append(finalText(d));
        } else if (d.finalPhase == 3) {
            sb.append("🏆 ").append(tr(d, "Победа!", "Victory!"));
        } else if (d.finalPhase == 4) {
            sb.append("☠ ").append(tr(d, "Проклятие: 5 FPS навсегда.", "Curse: 5 FPS forever."));
        } else {
            sb.append(tr(d, "Задания нет — копай: ", "No task — mine: ")).append(ORES[Math.min(d.stage, TOTAL - 1)].label(en));
        }
        if (!d.restrictions.isEmpty()) {
            sb.append('\n').append(tr(d, "Ограничения (навсегда):", "Restrictions (forever):"));
            for (String id : d.restrictions) {
                Restriction r = Restriction.byId(id);
                if (r != null) sb.append("\n ☠ ").append(r.title(en));
            }
        }
        say(p, sb.toString(), Formatting.YELLOW);
    }

    // ================= Задания =================

    private static Task randomTask(Ore ore, Task exclude, LadderData.P d) {
        List<Task> pool = new ArrayList<>();
        for (Task t : Task.forOre(ore)) if (t != exclude && RestrictionLogic.compatible(t, d)) pool.add(t);
        if (pool.isEmpty()) pool.addAll(Task.forOre(ore));
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    private static void startTask(ServerPlayerEntity p, LadderData.P d, Task t) {
        d.task = t.id;
        d.age = 0;
        d.flags = 0;
        d.count = 0;
        d.lastHitSkeleton = "";
        d.base[B_JUMP] = stat(p, Stats.JUMP);
        d.base[B_DEATH] = stat(p, Stats.DEATHS);
        d.base[B_SPRINT] = stat(p, Stats.SPRINT_ONE_CM);
        d.base[B_WALK] = stat(p, Stats.WALK_ONE_CM);
        d.base[B_DMG] = stat(p, Stats.DAMAGE_TAKEN);
        d.base[B_TRADE] = stat(p, Stats.TRADED_WITH_VILLAGER);
        for (int i = 0; i < BEDS.length; i++) d.bedBase[i] = p.getStatHandler().getStat(Stats.CRAFTED, BEDS[i]);
        d.startBiome = biomeId(p);
        d.lastFood = p.getHungerManager().getFoodLevel();
        applyAttributes(p);
        announce(p, d, t);
    }

    public static void swap(ServerPlayerEntity p, String id) {
        LadderData.P d = LadderData.get(p);
        Task cur = Task.byId(d.task);
        Task nw = Task.byId(id);
        if (cur == null) {
            say(p, tr(d, "Сейчас нет активного задания.", "No active task right now."), Formatting.RED);
        } else if (d.swapUsed) {
            say(p, tr(d, "Замена на этом уровне уже использована.", "You already used the swap on this level."), Formatting.RED);
        } else if (nw == null || nw == cur || nw.ore != cur.ore || !RestrictionLogic.compatible(nw, d)) {
            say(p, tr(d, "Это задание выбрать нельзя.", "You can't pick that task."), Formatting.RED);
        } else {
            d.swapUsed = true;
            startTask(p, d, nw);
            LadderData.save();
        }
    }

    private static void complete(ServerPlayerEntity p, LadderData.P d, Task t) {
        d.rewards += t.reward;
        d.task = "";
        d.age = 0;
        applyAttributes(p);
        if ((t.costs & Rule.HALF_ITEMS) != 0) {
            loseHalfItems(p);
            say(p, tr(d, "Цена пути: половина твоих вещей исчезла навсегда.", "Price paid: half of your items vanished forever."), Formatting.RED);
        }
        sync(p);
        LadderData.save();
        say(p, "✔ " + tr(d, "Задание выполнено: ", "Task complete: ") + t.title(en(d)) + "! +" + num(t.reward)
                + " FPS. " + tr(d, "Лимит: ", "Cap: ") + fpsText(fpsCap(d)), Formatting.GREEN);
        if (t.ore == Ore.NETHERITE) {
            d.finalPhase = 1;
            say(p, finalText(d), Formatting.GOLD, Formatting.BOLD);
        } else {
            Ore next = ORES[Math.min(d.stage, TOTAL - 1)];
            say(p, tr(d, "Следующая руда открыта: ", "Next ore unlocked: ") + next.label(en(d)), Formatting.GREEN);
        }
    }

    private static void fail(ServerPlayerEntity p, LadderData.P d, Task t, String why) {
        addPenalty(d, FAIL_PENALTY);
        say(p, "✖ " + tr(d, "Задание «", "Task \"") + t.title(en(d)) + tr(d, "» провалено: ", "\" failed: ") + why + ". −" + num(FAIL_PENALTY)
                + tr(d, " FPS (минимум 5). Вернуть FPS: копай уголь (+5 за блок). Выдано новое задание.",
                " FPS (min 5). Get FPS back by mining coal (+5 per block). A new task was issued."), Formatting.RED);
        startTask(p, d, randomTask(t.ore, t, d));
        sync(p);
        LadderData.save();
    }

    private static String violation(ServerPlayerEntity p, LadderData.P d, Task t) {
        int r = t.rules;
        if ((r & Rule.NO_BED) != 0) {
            if ((d.flags & F_BED) != 0) return tr(d, "поставил кровать", "placed a bed");
            if (p.isSleeping()) return tr(d, "лёг спать", "went to sleep");
            if (bedsCrafted(p, d) > 0) return tr(d, "скрафтил кровать", "crafted a bed");
        }
        if ((r & Rule.NO_JUMP) != 0 && stat(p, Stats.JUMP) > d.base[B_JUMP]) return tr(d, "прыгнул", "jumped");
        if ((r & Rule.NO_FOOD) != 0) {
            int f = p.getHungerManager().getFoodLevel();
            if (f > d.lastFood) return tr(d, "поел", "ate something");
            d.lastFood = f;
        }
        if ((r & Rule.NO_ARMOR) != 0 && d.age > 200 && wearsArmor(p)) return tr(d, "надел броню", "put on armor");
        if ((r & Rule.NO_SHIELD) != 0 && p.isUsingItem() && p.getActiveItem().isOf(Items.SHIELD)) return tr(d, "использовал щит", "used a shield");
        if ((r & Rule.NO_ARROW) != 0 && (d.flags & F_ARROW) != 0) return tr(d, "получил урон от стрелы", "was hit by an arrow");
        if ((r & Rule.NO_DAMAGE) != 0 && stat(p, Stats.DAMAGE_TAKEN) > d.base[B_DMG]) return tr(d, "получил урон", "took damage");
        if ((r & Rule.NO_SPRINT) != 0 && stat(p, Stats.SPRINT_ONE_CM) > d.base[B_SPRINT]) return tr(d, "бегал", "sprinted");
        if ((r & Rule.NO_DEATH) != 0 && stat(p, Stats.DEATHS) > d.base[B_DEATH]) return tr(d, "умер", "died");
        if (t.goal == Goal.PIGLIN && (d.flags & F_BADTRADE) != 0) return tr(d, "торговал в золотой броне", "bartered in golden armor");
        return null;
    }

    private static void tickTask(ServerPlayerEntity p, LadderData.P d) {
        Task t = Task.byId(d.task);
        if (t == null) {
            d.task = "";
            return;
        }
        d.age++;
        if (d.age % 5 != 0) return;

        String why = violation(p, d, t);
        if (why != null) {
            fail(p, d, t, why);
            return;
        }

        boolean done = false;
        switch (t.goal) {
            case NIGHT: {
                long tod = p.getServerWorld().getTimeOfDay() % 24000L;
                if (tod >= 13000 && tod <= 23000) d.flags |= F_NIGHT;
                else if ((d.flags & F_NIGHT) != 0 && tod < 12000) done = true;
                break;
            }
            case TIMER:
                done = d.age >= t.arg;
                break;
            case TIMER_ANIMAL:
                if (d.age % 20 == 0 && (d.flags & F_ANIMAL) == 0) {
                    boolean near = !p.getServerWorld().getEntitiesByClass(
                            MobEntity.class, p.getBoundingBox().expand(4.0), e -> true).isEmpty();
                    if (near) d.flags |= F_ANIMAL;
                }
                done = d.age >= t.arg && (d.flags & F_ANIMAL) != 0;
                break;
            case KILL:
            case KILL_FISTS:
                done = d.count >= t.arg;
                break;
            case BEDS:
                done = bedsCrafted(p, d) >= t.arg;
                break;
            case BIOME: {
                String b = biomeId(p);
                done = !b.isEmpty() && !b.equals(d.startBiome);
                break;
            }
            case WALK:
                done = (stat(p, Stats.WALK_ONE_CM) - d.base[B_WALK]) / 100 >= t.arg;
                break;
            case SKELETON_IRON:
            case PIGLIN:
            case BLAZE_FORTRESS:
                done = (d.flags & F_DONE) != 0;
                break;
            case TRADES:
                done = stat(p, Stats.TRADED_WITH_VILLAGER) - d.base[B_TRADE] >= t.arg;
                break;
            case KILL_ALL_NETHER:
                done = Integer.bitCount(d.count) >= NETHER_MOBS.length;
                break;
            case BASTION:
                done = inStructure(p.getServerWorld(), p.getBlockPos(), StructureKeys.BASTION_REMNANT);
                break;
        }
        if (done) complete(p, d, t);
    }

    // ================= События =================

    public static void onBlockBroken(ServerPlayerEntity p, BlockState st) {
        if (p.isCreative() || p.isSpectator()) return;
        int idx = oreIndex(st);
        if (idx < 0) return;
        LadderData.P d = LadderData.get(p);
        Ore ore = ORES[idx];

        // Уголь после штрафа возвращает FPS
        if (ore == Ore.COAL && d.stage > 0) {
            if (d.penalty > 0) {
                d.penalty = Math.max(0, d.penalty - COAL_REPAY);
                sync(p);
                p.sendMessage(Text.literal("⛏ +" + num(COAL_REPAY) + " FPS " + tr(d, "возвращено. Лимит: ", "restored. Cap: ")
                        + fpsText(fpsCap(d))).formatted(Formatting.GREEN), true);
            }
            return;
        }

        // Ранее пропущенное золото Незера можно добыть позже
        if (idx < d.stage) {
            if (ore.optional && d.skippedOptional && !d.optionalDone) {
                d.optionalDone = true;
                d.optionalDeadline = 0;
                d.oresDone++;
                sync(p);
                say(p, "✔ " + ore.label(en(d)) + tr(d, " добыто! +", " mined! +") + num(oreBonus()) + " FPS. "
                        + tr(d, "Лимит: ", "Cap: ") + fpsText(fpsCap(d)), Formatting.GREEN);
                if (LadderData.mode() == 1) RestrictionLogic.roll(p, d);
                LadderData.save();
            }
            return;
        }

        // Пропуск опциональной руды (золото Незера)
        if (d.stage < TOTAL && ORES[d.stage].optional && idx == d.stage + 1 && d.task.isEmpty()) {
            d.skippedOptional = true;
            d.stage++;
            say(p, tr(d, "Золото Незера пропущено. Когда добудешь незерит, у тебя будет 5 минут, чтобы найти его, — лучше сделай это заранее!",
                    "Nether Gold skipped. Once you mine netherite you'll have 5 minutes to find it — better do it early!"), Formatting.GOLD);
        }

        if (idx == d.stage && d.task.isEmpty() && d.stage < TOTAL) {
            d.stage++;
            d.oresDone++;
            d.swapUsed = false;
            if (ore.optional) d.optionalDone = true;
            sync(p);
            say(p, "✔ " + ore.label(en(d)) + tr(d, " добыт! +", " mined! +") + num(oreBonus()) + " FPS. "
                    + tr(d, "Лимит: ", "Cap: ") + fpsText(fpsCap(d)), Formatting.GREEN);
            if (ore == Ore.NETHERITE && d.skippedOptional && !d.optionalDone) {
                MinecraftServer s = p.getServer();
                if (s != null) d.optionalDeadline = s.getOverworld().getTime() + OPTIONAL_GRACE;
                say(p, tr(d, "⏳ У тебя 5 минут, чтобы добыть золото Незера (иначе штраф)!",
                        "⏳ You have 5 minutes to mine Nether Gold (or take a penalty)!"), Formatting.GOLD, Formatting.BOLD);
            }
            if (LadderData.mode() == 1) RestrictionLogic.roll(p, d);
            startTask(p, d, randomTask(ore, null, d));
            LadderData.save();
            return;
        }

        punish(p, d, idx == d.stage ? tr(d, "Сначала выполни задание!", "Finish your task first!")
                : tr(d, "Нельзя перепрыгивать через руды!", "You can't skip ores!"), false);
    }

    private static void punish(ServerPlayerEntity p, LadderData.P d, String reason, boolean ignoreCooldown) {
        MinecraftServer server = p.getServer();
        int now = server == null ? 0 : server.getTicks();
        if (!ignoreCooldown && now - d.lastPunish < 60) return; // не наказываем за каждый блок жилы
        d.lastPunish = now;
        addPenalty(d, FAIL_PENALTY);
        sync(p);
        p.damage(p.getServerWorld().getDamageSources().magic(), p.getMaxHealth() / 2f);
        String next = d.task.isEmpty() ? ORES[Math.min(d.stage, TOTAL - 1)].label(en(d)) : tr(d, "выполни задание", "finish the task");
        say(p, "✖ " + reason + tr(d, " Урон и −", " Damage and −") + num(FAIL_PENALTY) + tr(d, " FPS. Сейчас нужно: ", " FPS. You need now: ")
                + next + tr(d, ". Вернуть FPS: копай уголь.", ". Get FPS back: mine coal."), Formatting.RED);
        LadderData.save();
    }

    public static void onKill(ServerPlayerEntity p, LivingEntity killed) {
        LadderData.P d = LadderData.get(p);
        Task t = Task.byId(d.task);
        if (t == null) return;
        String id = Registries.ENTITY_TYPE.getId(killed.getType()).getPath();
        switch (t.goal) {
            case KILL:
                if (id.equals(t.entity)) d.count++;
                break;
            case KILL_FISTS:
                if (id.equals(t.entity) && p.getMainHandStack().isEmpty()) d.count++;
                break;
            case SKELETON_IRON:
                if (id.equals("skeleton") && killed.getUuidAsString().equals(d.lastHitSkeleton)) d.flags |= F_DONE;
                break;
            case BLAZE_FORTRESS:
                if (id.equals("blaze") && killed.getWorld() instanceof ServerWorld sw
                        && inStructure(sw, killed.getBlockPos(), StructureKeys.FORTRESS)) d.flags |= F_DONE;
                break;
            case KILL_ALL_NETHER:
                for (int i = 0; i < NETHER_MOBS.length; i++) if (NETHER_MOBS[i].equals(id)) d.count |= (1 << i);
                break;
            default:
                break;
        }
    }

    public static void onDamage(LivingEntity victim, DamageSource source) {
        if (victim instanceof ServerPlayerEntity sp) {
            if (source.isOf(DamageTypes.ARROW)) LadderData.get(sp).flags |= F_ARROW;
        } else if (victim.getType() == EntityType.SKELETON && source.getAttacker() instanceof ServerPlayerEntity sp) {
            boolean ironMelee = source.isOf(DamageTypes.PLAYER_ATTACK) && sp.getMainHandStack().isOf(Items.IRON_SWORD);
            LadderData.get(sp).lastHitSkeleton = ironMelee ? victim.getUuidAsString() : "";
        }
    }

    public static void onUseBlock(ServerPlayerEntity p, BlockState clicked, ItemStack held) {
        if (held.isIn(ItemTags.BEDS) && !(clicked.getBlock() instanceof BedBlock)) {
            LadderData.get(p).flags |= F_BED;
        }
    }

    public static void onUseEntity(ServerPlayerEntity p, Entity entity, ItemStack held) {
        LadderData.P d = LadderData.get(p);
        Task t = Task.byId(d.task);
        if (t == null || t.goal != Goal.PIGLIN) return;
        if (!held.isOf(Items.GOLD_INGOT) || !(entity instanceof PiglinEntity pig) || pig.isBaby()) return;
        if (wearsGold(p)) d.flags |= F_BADTRADE;
        else d.flags |= F_DONE;
    }

    // ================= Финал =================

    private static BlockPos chestPos(MinecraftServer server) {
        LadderData.Save s = LadderData.data();
        ServerWorld w = server.getOverworld();
        if (!s.chestSet) {
            w.getChunk(0, 0);
            BlockPos top = w.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(0, 0, 0));
            s.chestY = top.getY();
            s.chestSet = true;
        }
        return new BlockPos(0, s.chestY, 0);
    }

    private static void ensureChest(MinecraftServer server) {
        ServerWorld w = server.getOverworld();
        BlockPos pos = chestPos(server);
        w.getChunk(0, 0);
        if (!w.getBlockState(pos).isOf(Blocks.CHEST)) w.setBlockState(pos, Blocks.CHEST.getDefaultState());
    }

    private static boolean delivered(MinecraftServer server) {
        ServerWorld w = server.getOverworld();
        BlockPos pos = chestPos(server);
        if (!w.isChunkLoaded(pos)) return false;
        BlockEntity be = w.getBlockEntity(pos);
        if (!(be instanceof ChestBlockEntity chest)) {
            ensureChest(server);
            return false;
        }
        boolean elytra = false, egg = false;
        for (int i = 0; i < chest.size(); i++) {
            ItemStack s = chest.getStack(i);
            if (s.isOf(Items.ELYTRA)) elytra = true;
            if (s.isOf(Items.DRAGON_EGG)) egg = true;
        }
        return elytra && egg;
    }

    public static void onDragonDeath(MinecraftServer server) {
        if (server == null) return;
        long now = server.getOverworld().getTime();
        boolean any = false;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            LadderData.P d = LadderData.get(p);
            if (d.finalPhase == 1 && p.getWorld().getRegistryKey() == World.END) {
                if (!any) {
                    ensureChest(server);
                    any = true;
                }
                d.finalPhase = 2;
                d.deadline = now + DELIVERY_TICKS;
                say(p, finalText(d), Formatting.GOLD, Formatting.BOLD);
            }
        }
        LadderData.save();
    }

    private static void curse(ServerPlayerEntity p, LadderData.P d, String ru, String en) {
        d.finalPhase = 4;
        sync(p);
        LadderData.save();
        say(p, "☠ " + tr(d, ru, en), Formatting.DARK_RED, Formatting.BOLD);
    }

    private static void victory(MinecraftServer server, ServerPlayerEntity p, LadderData.P d) {
        boolean hadHard = RestrictionLogic.has(d, Restriction.HARD);
        d.finalPhase = 3;
        d.restrictions.clear();
        d.jitterCap = 0;
        applyAttributes(p);
        sync(p);
        LadderData.save();
        if (hadHard) server.setDifficultyLocked(false);
        server.getPlayerManager().broadcast(Text.literal("🏆 " + p.getName().getString()
                + " — Ore Ladder complete! / Лестница руд пройдена!").formatted(Formatting.GOLD), false);
        say(p, tr(d, "Все ограничения сняты. FPS максимальный!", "All restrictions lifted. Max FPS!"), Formatting.GOLD);
    }

    private static void tickFinal(MinecraftServer server, ServerPlayerEntity p, LadderData.P d, int tk, long now) {
        if (d.finalPhase == 1) {
            if (d.dragonDeadline == 0) {
                if (p.getWorld().getRegistryKey() == World.END) {
                    d.dragonDeadline = now + DRAGON_TICKS;
                    say(p, tr(d, "⏳ Ты в Энде! 30 минут, чтобы убить дракона.", "⏳ You're in the End! 30 minutes to kill the dragon."),
                            Formatting.GOLD, Formatting.BOLD);
                }
            } else if (now > d.dragonDeadline) {
                if (DRAGON_TIMEOUT_CURSE) {
                    curse(p, d, "Дракон не убит за 30 минут. Навсегда 5 FPS. Избавиться можно только удалив мод.",
                            "The dragon wasn't killed in 30 minutes. 5 FPS forever. Only removing the mod frees you.");
                } else {
                    d.dragonDeadline = 0;
                    addPenalty(d, FAIL_PENALTY);
                    sync(p);
                }
            } else if (tk % 1200 == 0) {
                p.sendMessage(Text.literal("⏳ " + tr(d, "Осталось на дракона: ", "Time left for the dragon: ")
                        + mmss(d.dragonDeadline - now)).formatted(Formatting.GOLD), true);
            }
        } else if (d.finalPhase == 2) {
            if (delivered(server)) {
                victory(server, p, d);
            } else if (now > d.deadline) {
                curse(p, d, "Яйцо и элитры не сданы за 30 минут. Навсегда 5 FPS. Избавиться можно только удалив мод.",
                        "Egg and elytra weren't delivered in 30 minutes. 5 FPS forever. Only removing the mod frees you.");
            } else if (tk % 1200 == 0) {
                p.sendMessage(Text.literal("⏳ " + tr(d, "На сдачу осталось ", "Delivery time left: ")
                        + mmss(d.deadline - now) + " (0, " + LadderData.data().chestY + ", 0)").formatted(Formatting.GOLD), true);
            }
        }
    }

    // ================= Тик =================

    public static void tick(MinecraftServer server) {
        int tk = server.getTicks();
        long now = server.getOverworld().getTime();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isCreative() || p.isSpectator()) continue;
            LadderData.P d = LadderData.get(p);

            if (!d.task.isEmpty()) {
                tickTask(p, d);
                if (tk % 40 == 0) {
                    Task t = Task.byId(d.task);
                    if (t != null) {
                        p.sendMessage(Text.literal("⚔ " + t.title(en(d)) + ": " + progress(p, d, t)).formatted(Formatting.YELLOW), true);
                    }
                }
            }

            if (LadderData.mode() == 1 && d.finalPhase != 3) RestrictionLogic.tick(server, p, d, tk);

            if (tk % 20 == 0) {
                if (d.optionalDeadline > 0 && now > d.optionalDeadline) {
                    d.optionalDeadline = 0;
                    d.optionalDone = true; // упущено
                    punish(p, d, tr(d, "Золото Незера не найдено за 5 минут!", "Nether Gold not found in 5 minutes!"), true);
                }
                tickFinal(server, p, d, tk, now);
            }
        }
        if (tk % 6000 == 0) LadderData.save();
    }
}
