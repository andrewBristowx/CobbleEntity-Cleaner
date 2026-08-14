package com.andrewbristowx.cobbleentitycleaner.command;

import com.andrewbristowx.cobbleentitycleaner.cleanup.CleanupService;
import com.andrewbristowx.cobbleentitycleaner.config.CleanerConfig;
import com.andrewbristowx.cobbleentitycleaner.diagnostics.CleanerDiagnostics;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class CleanerCommand {
    private CleanerCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("cobblecleaner")
                        .then(Commands.literal("status")
                                .executes(context -> {
                                    Component status = CleanerDiagnostics.decorateStatus(
                                            context.getSource().getServer(),
                                            CleanupService.status()
                                    );
                                    context.getSource().sendSuccess(() -> status, false);
                                    return 1;
                                }))
                        .then(Commands.literal("stats")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    for (Component line : CleanerDiagnostics.stats(context.getSource().getServer())) {
                                        context.getSource().sendSuccess(() -> line, false);
                                    }
                                    return 1;
                                })
                                .then(Commands.literal("worlds")
                                        .executes(context -> {
                                            for (Component line : CleanerDiagnostics.worldStats(context.getSource().getServer())) {
                                                context.getSource().sendSuccess(() -> line, false);
                                            }
                                            return 1;
                                        })))
                        .then(Commands.literal("hotspots")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    for (Component line : CleanerDiagnostics.hotspots(context.getSource().getServer(), 8)) {
                                        context.getSource().sendSuccess(() -> line, false);
                                    }
                                    return 1;
                                }))
                        .then(Commands.literal("vote")
                                .then(Commands.literal("yes")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            return CleanupService.vote(player, true);
                                        }))
                                .then(Commands.literal("no")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            return CleanupService.vote(player, false);
                                        }))
                                .then(Commands.literal("skip")
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            return CleanupService.voteSkip(player);
                                        })))
                        .then(Commands.literal("run")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    CleanupService.CleanupResult result = CleanupService.runNow(context.getSource().getServer());
                                    context.getSource().sendSuccess(
                                            () -> Component.literal("Limpieza manual: " + result.removed() + " eliminados de " + result.scanned() + " revisados.")
                                                    .withStyle(ChatFormatting.GREEN),
                                            false
                                    );
                                    return result.removed();
                                }))
                        .then(Commands.literal("preview")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    CleanupService.CleanupResult result = CleanupService.preview(context.getSource().getServer());
                                    context.getSource().sendSuccess(
                                            () -> Component.literal("Vista previa: " + result.removable() + " Pokémon serían eliminados de " + result.scanned() + " revisados. ")
                                                    .withStyle(ChatFormatting.YELLOW)
                                                    .append(Component.literal(
                                                            "Protegidos → especiales: " + result.protectedSpecial()
                                                                    + ", jugador/entrenador: " + result.protectedPlayerOrTrainer()
                                                                    + ", batalla/busy: " + result.protectedBattleOrBusy()
                                                                    + ", cerca de jugador: " + result.protectedNearby()
                                                                    + ", otros: " + result.protectedOther()
                                                    ).withStyle(ChatFormatting.GRAY)),
                                            false
                                    );
                                    return result.removable();
                                }))
                        .then(Commands.literal("reload")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    CleanerConfig.reload();
                                    CleanupService.reschedule(context.getSource().getServer());
                                    context.getSource().sendSuccess(
                                            () -> Component.literal("CobbleEntity Cleaner: configuración recargada.")
                                                    .withStyle(ChatFormatting.GREEN),
                                            false
                                    );
                                    return 1;
                                }))
        );
    }
}
