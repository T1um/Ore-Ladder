package com.oreladder;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;

public class OreLadder implements ModInitializer {
    public static final String MOD_ID = "oreladder";

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(FpsPayload.ID, FpsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(LangPayload.ID, LangPayload.CODEC);

        ServerLifecycleEvents.SERVER_STARTED.register(LadderData::load);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> LadderData.save());

        // Язык приходит от клиента сразу после входа — тогда и приветствуем.
        ServerPlayNetworking.registerGlobalReceiver(LangPayload.ID, (payload, context) -> {
            ServerPlayerEntity p = context.player();
            LadderData.P d = LadderData.get(p);
            if (!d.langManual) d.lang = "ru".equals(payload.lang()) ? "ru" : "en";
            LadderLogic.greet(p);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity p = handler.player;
            LadderLogic.applyAttributes(p);
            LadderLogic.sync(p);
        });

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> LadderCommands.register(dispatcher));

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
            if (player instanceof ServerPlayerEntity sp) LadderLogic.onBlockBroken(sp, state);
        });

        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, entity, killed) -> {
            if (entity instanceof ServerPlayerEntity sp) LadderLogic.onKill(sp, killed);
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            LadderLogic.onDamage(entity, source);
            return true;
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity.getType() == EntityType.ENDER_DRAGON) LadderLogic.onDragonDeath(entity.getServer());
        });

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!world.isClient && player instanceof ServerPlayerEntity sp) {
                LadderData.P d = LadderData.get(sp);
                if (hand == Hand.OFF_HAND && RestrictionLogic.has(d, Restriction.OFFHAND)) return ActionResult.FAIL;
                LadderLogic.onUseBlock(sp, world.getBlockState(hit.getBlockPos()), sp.getStackInHand(hand));
            }
            return ActionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!world.isClient && player instanceof ServerPlayerEntity sp) {
                LadderData.P d = LadderData.get(sp);
                if (hand == Hand.OFF_HAND && RestrictionLogic.has(d, Restriction.OFFHAND)) return ActionResult.FAIL;
                LadderLogic.onUseEntity(sp, entity, sp.getStackInHand(hand));
            }
            return ActionResult.PASS;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!world.isClient && player instanceof ServerPlayerEntity sp) {
                LadderData.P d = LadderData.get(sp);
                if ((hand == Hand.OFF_HAND && RestrictionLogic.has(d, Restriction.OFFHAND))
                        || RestrictionLogic.foodForbidden(d, stack)) {
                    RestrictionLogic.warn(sp, d);
                    return TypedActionResult.fail(stack);
                }
            }
            return TypedActionResult.pass(stack);
        });

        ServerTickEvents.END_SERVER_TICK.register(LadderLogic::tick);
    }
}
