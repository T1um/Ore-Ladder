package com.oreladder;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Arrays;
import java.util.List;

public class LadderCommands {
    private static final List<String> TASK_IDS = Arrays.stream(Task.values()).map(t -> t.id).toList();

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("ladder")
                .executes(ctx -> {
                    LadderLogic.status(ctx.getSource().getPlayerOrThrow());
                    return 1;
                })
                .then(CommandManager.literal("swap")
                        .then(CommandManager.argument("id", StringArgumentType.word())
                                .suggests((c, b) -> CommandSource.suggestMatching(TASK_IDS, b))
                                .executes(ctx -> {
                                    LadderLogic.swap(ctx.getSource().getPlayerOrThrow(), StringArgumentType.getString(ctx, "id"));
                                    return 1;
                                })))
                .then(CommandManager.literal("lang")
                        .then(CommandManager.argument("code", StringArgumentType.word())
                                .suggests((c, b) -> CommandSource.suggestMatching(List.of("ru", "en"), b))
                                .executes(ctx -> {
                                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                    String code = StringArgumentType.getString(ctx, "code").toLowerCase();
                                    LadderData.P d = LadderData.get(p);
                                    if (!code.equals("ru") && !code.equals("en")) {
                                        p.sendMessage(Text.literal("ru | en").formatted(Formatting.RED), false);
                                        return 0;
                                    }
                                    d.lang = code;
                                    d.langManual = true;
                                    LadderData.save();
                                    p.sendMessage(Text.literal(code.equals("ru") ? "Язык: русский" : "Language: English")
                                            .formatted(Formatting.GREEN), false);
                                    return 1;
                                })))
                .then(CommandManager.literal("mode")
                        .requires(src -> src.hasPermissionLevel(2))
                        .then(CommandManager.argument("name", StringArgumentType.word())
                                .suggests((c, b) -> CommandSource.suggestMatching(List.of("normal", "extreme"), b))
                                .executes(ctx -> {
                                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                    LadderData.P d = LadderData.get(p);
                                    String name = StringArgumentType.getString(ctx, "name").toLowerCase();
                                    int m = name.equals("extreme") ? 1 : (name.equals("normal") ? 0 : -1);
                                    if (m < 0) {
                                        p.sendMessage(Text.literal("normal | extreme").formatted(Formatting.RED), false);
                                        return 0;
                                    }
                                    if (LadderData.anyProgress()) {
                                        p.sendMessage(Text.literal(LadderLogic.tr(d, "Режим нельзя менять: игра уже началась.",
                                                "Can't change the mode: the game has already started.")).formatted(Formatting.RED), false);
                                        return 0;
                                    }
                                    LadderData.setMode(m);
                                    for (ServerPlayerEntity o : ctx.getSource().getServer().getPlayerManager().getPlayerList()) {
                                        LadderLogic.sync(o);
                                    }
                                    p.sendMessage(Text.literal(LadderLogic.tr(d, "Режим: ", "Mode: ")
                                            + (m == 1 ? LadderLogic.tr(d, "ОЧЕНЬ ЭКСТРИМАЛЬНЫЙ", "VERY EXTREME")
                                            : LadderLogic.tr(d, "Нормальный", "Normal"))).formatted(Formatting.GOLD), false);
                                    return 1;
                                })))
                .then(CommandManager.literal("reset")
                        .requires(src -> src.hasPermissionLevel(2))
                        .executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            LadderData.P d = LadderData.get(p);
                            if (d.finalPhase == 4) {
                                p.sendMessage(Text.literal(LadderLogic.tr(d, "Проклятие не снять командой. Только удалить мод.",
                                        "The curse can't be lifted by command. Only remove the mod.")).formatted(Formatting.DARK_RED), false);
                                return 0;
                            }
                            LadderData.reset(p);
                            LadderLogic.applyAttributes(p);
                            LadderLogic.sync(p);
                            p.sendMessage(Text.literal(LadderLogic.tr(d, "Прогресс сброшен.", "Progress reset.")).formatted(Formatting.YELLOW), false);
                            return 1;
                        })));
    }
}
