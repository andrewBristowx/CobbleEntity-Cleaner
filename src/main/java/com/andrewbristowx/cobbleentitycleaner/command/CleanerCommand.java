package com.andrewbristowx.cobbleentitycleaner.command;

import com.andrewbristowx.cobbleentitycleaner.cleanup.CleanupService;
import com.andrewbristowx.cobbleentitycleaner.config.CleanerConfig;
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
                                    context.getSource().sendSuccess(CleanupService::status, false);
                                    return 1;
                                }))
                        .then(Commands.literal("vote")
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
                                                                    + ", recientes: " + result.protectedRecent()
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
