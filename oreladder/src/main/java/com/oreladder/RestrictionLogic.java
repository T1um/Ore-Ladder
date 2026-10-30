package com.oreladder;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;
import net.minecraft.block.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Ограничения режима «Очень Экстримальный». */
public final class RestrictionLogic {
    private RestrictionLogic() {}

    private static final Set<String> MEAT = Set.of(
            "beef", "cooked_beef", "porkchop", "cooked_porkchop", "mutton", "cooked_mutton",
            "chicken", "cooked_chicken", "rabbit", "cooked_rabbit", "rabbit_stew", "cod", "cooked_cod",
            "salmon", "cooked_salmon", "tropical_fish", "pufferfish", "rotten_flesh");
    private static final String[] GEAR = {"_sword", "_axe", "_shovel", "_hoe", "_helmet", "_chestplate", "_leggings", "_boots"};
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public static boolean has(LadderData.P d, Restriction r) {
        return d.restrictions.contains(r.id);
    }

    private static String path(ItemStack s) {
        return Registries.ITEM.getId(s.getItem()).getPath();
    }

    private static boolean isGear(String p) {
        for (String g : GEAR) if (p.endsWith(g)) return true;
        return false;
    }

    // ---------- Совместимость с заданиями ----------

    public static boolean compatible(Task t, LadderData.P d) {
        for (String id : d.restrictions) {
            Restriction r = Restriction.byId(id);
            if (r != null && r.conflicts(t)) return false;
        }
        return true;
    }

    // ---------- Выдача нового случайного ограничения ----------

    public static void roll(ServerPlayerEntity p, LadderData.P d) {
        List<Restriction> pool = new ArrayList<>();
        for (Restriction r : Restriction.values()) {
            if (has(d, r)) continue;
            boolean bad = false;
            for (String id : d.restrictions) {
                Restriction o = Restriction.byId(id);
                if (o != null && r.excludes(o)) bad = true;
            }
            if (!bad) pool.add(r);
        }
        if (pool.isEmpty()) return;
        Restriction r = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        d.restrictions.add(r.id);
        applyImmediate(p, r);
        LadderLogic.applyAttributes(p);
        boolean en = LadderLogic.en(d);
        p.sendMessage(Text.literal("☠ " + LadderLogic.tr(d, "НОВОЕ ОГРАНИЧЕНИЕ (навсегда): ", "NEW RESTRICTION (forever): ")
                + r.title(en)).formatted(Formatting.DARK_RED, Formatting.BOLD), false);
        p.sendMessage(Text.literal(r.desc(en)).formatted(Formatting.RED), false);
    }

    private static void applyImmediate(ServerPlayerEntity p, Restriction r) {
        switch (r) {
            case LEATHER: {
                if (p.getEquippedStack(EquipmentSlot.HEAD).isEmpty()) p.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
                if (p.getEquippedStack(EquipmentSlot.CHEST).isEmpty()) p.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
                if (p.getEquippedStack(EquipmentSlot.LEGS).isEmpty()) p.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
                if (p.getEquippedStack(EquipmentSlot.FEET).isEmpty()) p.equipStack(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
                break;
            }
            case BOW_ONLY: {
                p.getInventory().offerOrDrop(new ItemStack(Items.BOW));
                p.getInventory().offerOrDrop(new ItemStack(Items.ARROW, 32));
                break;
            }
            case HARD: {
                MinecraftServer s = p.getServer();
                if (s != null) {
                    s.setDifficulty(Difficulty.HARD, true);
                    s.setDifficultyLocked(true);
                }
                break;
            }
            default:
                break;
        }
    }

    // ---------- Еда ----------

    public static boolean foodForbidden(LadderData.P d, ItemStack s) {
        if (d.restrictions.isEmpty() || s.get(DataComponentTypes.FOOD) == null) return false;
        boolean meat = MEAT.contains(path(s));
        if (has(d, Restriction.VEGETARIAN) && meat) return true;
        return has(d, Restriction.CARNIVORE) && !meat;
    }

    public static void warn(ServerPlayerEntity p, LadderData.P d) {
        p.sendMessage(Text.literal(LadderLogic.tr(d, "Твоё ограничение этого не позволяет!", "Your restriction doesn't allow that!"))
                .formatted(Formatting.RED), true);
    }

    // ---------- Слоты ----------

    private static boolean banned(LadderData.P d, int slot) {
        if (slot < 0 || slot > 35) return false;
        if (has(d, Restriction.HOTBAR_HALF) && slot >= 5 && slot <= 8) return true;
        return has(d, Restriction.HOTBAR_ONLY) && slot >= 9;
    }

    private static void moveOut(ServerPlayerEntity p, LadderData.P d, ItemStack s) {
        PlayerInventory inv = p.getInventory();
        for (int j = 0; j < 36; j++) {
            if (!banned(d, j) && inv.getStack(j).isEmpty()) {
                inv.setStack(j, s);
                return;
            }
        }
        p.dropItem(s, false);
    }

    private static void enforceSlots(ServerPlayerEntity p, LadderData.P d) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < 36; i++) {
            if (!banned(d, i)) continue;
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) continue;
            inv.setStack(i, ItemStack.EMPTY);
            moveOut(p, d, s);
        }
        if (has(d, Restriction.OFFHAND)) {
            ItemStack off = inv.getStack(PlayerInventory.OFF_HAND_SLOT);
            if (!off.isEmpty()) {
                inv.setStack(PlayerInventory.OFF_HAND_SLOT, ItemStack.EMPTY);
                moveOut(p, d, off);
            }
        }
    }

    private static boolean purge(LadderData.P d, ItemStack s) {
        String p = path(s);
        if (has(d, Restriction.BOW_ONLY) && (s.isIn(ItemTags.SWORDS) || s.isIn(ItemTags.AXES))) return true;
        if (has(d, Restriction.NO_DIAMOND) && (p.startsWith("diamond_") || p.startsWith("netherite_")) && isGear(p)) return true;
        return has(d, Restriction.GOLD_ONLY) && isGear(p) && !p.startsWith("golden_");
    }

    // ---------- Тик ----------

    public static void tick(MinecraftServer server, ServerPlayerEntity p, LadderData.P d, int tk) {
        if (d.restrictions.isEmpty()) {
            d.jitterCap = 0;
            return;
        }
        boolean en = LadderLogic.en(d);

        if (p.isUsingItem() && foodForbidden(d, p.getActiveItem())) {
            p.clearActiveItem();
            warn(p, d);
        }

        if (tk % 10 == 0) {
            if (has(d, Restriction.HUNGER4) && p.getHungerManager().getFoodLevel() > 16) {
                p.getHungerManager().setFoodLevel(16);
            }
            if (has(d, Restriction.NO_SLEEP) && p.isSleeping()) {
                p.wakeUp(true, true);
                p.sendMessage(Text.literal(LadderLogic.tr(d, "Тебе нельзя спать!", "You can't sleep!")).formatted(Formatting.RED), true);
            }
            enforceSlots(p, d);
        }

        if (tk % 20 == 0) {
            PlayerInventory inv = p.getInventory();
            boolean removed = false;
            for (int i = 0; i < inv.size(); i++) {
                ItemStack s = inv.getStack(i);
                if (!s.isEmpty() && purge(d, s)) {
                    inv.setStack(i, ItemStack.EMPTY);
                    removed = true;
                }
            }
            if (removed) {
                p.sendMessage(Text.literal(LadderLogic.tr(d, "Запрещённый предмет исчез!", "A forbidden item vanished!"))
                        .formatted(Formatting.RED), true);
            }

            if (has(d, Restriction.LEATHER)) {
                for (EquipmentSlot slot : ARMOR) {
                    ItemStack s = p.getEquippedStack(slot);
                    if (!s.isEmpty() && !path(s).startsWith("leather_")) {
                        p.equipStack(slot, ItemStack.EMPTY);
                        moveOut(p, d, s);
                        warn(p, d);
                    }
                }
            }

            ServerWorld w = p.getServerWorld();
            if (has(d, Restriction.NIGHT_ONLY) && w.getRegistryKey() == World.OVERWORLD && w.isDay()
                    && w.isSkyVisible(p.getBlockPos()) && !w.isRaining() && !p.isTouchingWater()) {
                p.damage(w.getDamageSources().onFire(), 2.0f);
                p.sendMessage(Text.literal(LadderLogic.tr(d, "☀ Солнце жжёт! Прячься до ночи.", "☀ The sun burns! Hide until night."))
                        .formatted(Formatting.GOLD), true);
            }

            if (has(d, Restriction.HARD) && tk % 200 == 0) {
                server.setDifficulty(Difficulty.HARD, true);
                server.setDifficultyLocked(true);
            }
        }

        if (tk % 40 == 0 && has(d, Restriction.PLANKS)) {
            ServerWorld w = p.getServerWorld();
            if (p.isOnGround() && !p.isTouchingWater() && !p.hasVehicle() && !p.isClimbing()) {
                BlockState below = w.getBlockState(BlockPos.ofFloored(p.getX(), p.getY() - 0.2, p.getZ()));
                boolean ok = below.isIn(BlockTags.PLANKS) || below.isIn(BlockTags.WOODEN_SLABS) || below.isIn(BlockTags.WOODEN_STAIRS);
                if (!ok && !below.isAir()) {
                    p.damage(w.getDamageSources().magic(), 2.0f);
                    p.sendMessage(Text.literal(LadderLogic.tr(d, "Стой только на досках!", "Stand only on planks!"))
                            .formatted(Formatting.RED), true);
                }
            }
        }

        if (tk % 6000 == 0 && has(d, Restriction.DARKNESS)) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 300));
        }

        if (tk % 20 == 0) {
            if (has(d, Restriction.JITTER)) {
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                if (d.jitterAt == 0) d.jitterAt = tk + rnd.nextInt(1200);
                if (tk >= d.jitterAt) {
                    d.jitterCap = 5 + rnd.nextInt(36); // 5..40
                    d.jitterAt = (tk / 1200L + 1) * 1200L + rnd.nextInt(1200);
                    LadderLogic.sync(p);
                    p.sendMessage(Text.literal(LadderLogic.tr(d, "⚡ FPS скачет: сейчас максимум " + d.jitterCap,
                            "⚡ FPS jumps: cap is now " + d.jitterCap)).formatted(Formatting.YELLOW), true);
                }
            } else if (d.jitterCap != 0) {
                d.jitterCap = 0;
            }
        }
    }
}
