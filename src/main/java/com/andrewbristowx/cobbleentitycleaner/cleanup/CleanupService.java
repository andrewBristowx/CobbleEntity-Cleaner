package com.andrewbristowx.cobbleentitycleaner.cleanup;

import com.andrewbristowx.cobbleentitycleaner.CobbleEntityCleaner;
import com.andrewbristowx.cobbleentitycleaner.config.CleanerConfig;
import com.cobblemon.mod.common.api.pokemon.labels.CobblemonPokemonLabels;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.OriginalTrainerType;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CleanupService {
    public static final String PROTECTED_TAG = "cobbleentitycleaner_protected";

    private static final Map<UUID, Long> FIRST_SEEN_TICK = new HashMap<>();
    private static final Set<UUID> VOTE_ELIGIBLE = new HashSet<>();
    private static final Map<UUID, VoteChoice> VOTES = new HashMap<>();

    private static long serverTicks;
    private static long nextCleanupTick;
    private static boolean voteActive;
    private static boolean warnedTenSeconds;

    private CleanupService() {
    }

    public static void markLoaded(Entity entity) {
        if (entity instanceof PokemonEntity pokemonEntity) {
            FIRST_SEEN_TICK.putIfAbsent(pokemonEntity.getUUID(), serverTicks);
        }
    }

    public static void tick(MinecraftServer server) {
        serverTicks++;
        CleanerConfig config = CleanerConfig.get();
        if (!config.enabled) {
            return;
        }

        if (nextCleanupTick <= 0L) {
            scheduleNext();
        }

        long remaining = nextCleanupTick - serverTicks;
        long voteWindowTicks = (long) config.voteWindowSeconds * 20L;

        if (config.voteSkipEnabled && !voteActive && remaining <= voteWindowTicks && remaining > 0L) {
            openVote(server);
        }

        if (config.announceWarnings && !warnedTenSeconds && remaining <= 200L && remaining > 0L) {
            warnedTenSeconds = true;
            MutableComponent warning = Component.literal("⚠ Limpieza de Pokémon salvajes en 10 segundos.")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
            if (voteActive) {
                warning.append(Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(voteTallyComponent());
            }
            broadcast(server, warning);
        }

        if (remaining <= 0L) {
            if (voteActive) {
                VoteOutcome outcome = currentVoteOutcome();
                announceVoteOutcome(server, outcome);
                closeVote();
                if (outcome.skipCleanup()) {
                    scheduleNext();
                    return;
                }
            }

            CleanupResult result = performCleanup(server, false);
            announceResult(server, result);
            scheduleNext();
        }
    }

    public static int vote(ServerPlayer player, boolean skipCleanup) {
        CleanerConfig config = CleanerConfig.get();
        if (!config.voteSkipEnabled) {
            player.sendSystemMessage(Component.literal("La votación para la limpieza está desactivada.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!voteActive) {
            player.sendSystemMessage(Component.literal("No hay ninguna votación de limpieza activa.")
                    .withStyle(ChatFormatting.GRAY));
            return 0;
        }
        if (!VOTE_ELIGIBLE.contains(player.getUUID())) {
            player.sendSystemMessage(Component.literal("No estabas conectado cuando comenzó esta votación.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        VoteChoice newChoice = skipCleanup ? VoteChoice.YES : VoteChoice.NO;
        VoteChoice previousChoice = VOTES.put(player.getUUID(), newChoice);
        if (previousChoice == newChoice) {
            player.sendSystemMessage(Component.literal("Ese ya es tu voto actual.")
                    .withStyle(ChatFormatting.GRAY));
            return 0;
        }

        String choiceText = newChoice == VoteChoice.YES ? "SÍ, SALTAR" : "NO, CONTINUAR";
        ChatFormatting choiceColor = newChoice == VoteChoice.YES ? ChatFormatting.GREEN : ChatFormatting.RED;

        MutableComponent message = Component.literal("☑ ")
                .withStyle(choiceColor)
                .append(player.getName().copy().withStyle(ChatFormatting.AQUA))
                .append(Component.literal(previousChoice == null ? " votó: " : " cambió su voto a: ")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal(choiceText).withStyle(choiceColor, ChatFormatting.BOLD))
                .append(Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY))
                .append(voteTallyComponent());
        broadcast(player.server, message);
        return 1;
    }

    /** Backwards-compatible alias for alpha.1's /vote skip command. */
    public static int voteSkip(ServerPlayer player) {
        return vote(player, true);
    }

    public static CleanupResult runNow(MinecraftServer server) {
        closeVote();
        CleanupResult result = performCleanup(server, false);
        announceResult(server, result);
        scheduleNext();
        return result;
    }

    public static CleanupResult preview(MinecraftServer server) {
        return performCleanup(server, true);
    }

    public static void reschedule(MinecraftServer server) {
        closeVote();
        scheduleNext();
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("CobbleEntity Cleaner: temporizador reiniciado con la nueva configuración.")
                        .withStyle(ChatFormatting.GREEN),
                false
        );
    }

    public static Component status() {
        CleanerConfig config = CleanerConfig.get();
        long remainingTicks = Math.max(0L, nextCleanupTick - serverTicks);
        long remainingSeconds = (remainingTicks + 19L) / 20L;
        String time = (remainingSeconds / 60L) + "m " + (remainingSeconds % 60L) + "s";

        MutableComponent component = Component.literal("CobbleEntity Cleaner: ")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                .append(Component.literal(config.enabled ? "ACTIVO" : "DESACTIVADO")
                        .withStyle(config.enabled ? ChatFormatting.GREEN : ChatFormatting.RED))
                .append(Component.literal(" • Próxima limpieza: " + time).withStyle(ChatFormatting.GRAY));

        if (voteActive) {
            component.append(Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(voteTallyComponent());
        }
        return component;
    }

    private static void openVote(MinecraftServer server) {
        CleanerConfig config = CleanerConfig.get();
        voteActive = true;
        warnedTenSeconds = false;
        VOTE_ELIGIBLE.clear();
        VOTES.clear();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            VOTE_ELIGIBLE.add(player.getUUID());
        }

        if (config.announceWarnings) {
            broadcast(server, Component.literal("⚠ Limpieza de Pokémon salvajes en " + config.voteWindowSeconds + " segundos.")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        }

        MutableComponent yesButton = Component.literal("[ ✓ SÍ, SALTAR ]")
                .withStyle(style -> style
                        .withColor(ChatFormatting.GREEN)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cobblecleaner vote yes"))
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Vota para saltar esta ronda de limpieza")
                        )));

        MutableComponent noButton = Component.literal("[ ✕ NO, CONTINUAR ]")
                .withStyle(style -> style
                        .withColor(ChatFormatting.RED)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cobblecleaner vote no"))
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Vota para continuar con esta ronda de limpieza")
                        )));

        int minimumParticipants = minimumRequiredParticipants();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(
                    Component.literal("¿Quieres saltar esta limpieza? ")
                            .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)
                            .append(yesButton.copy())
                            .append(Component.literal("  "))
                            .append(noButton.copy())
            );
            player.sendSystemMessage(
                    Component.literal("Participación mínima: " + config.voteMinimumParticipationPercent + "% ("
                                    + minimumParticipants + "/" + VOTE_ELIGIBLE.size() + ")")
                            .withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(" • Gana la mayoría de los votos emitidos.")
                                    .withStyle(ChatFormatting.DARK_GRAY))
            );
        }

        CobbleEntityCleaner.LOGGER.info(
                "Cleanup vote opened: {} eligible player(s), minimum participation {}% ({} player(s))",
                VOTE_ELIGIBLE.size(),
                config.voteMinimumParticipationPercent,
                minimumParticipants
        );
    }

    private static VoteOutcome currentVoteOutcome() {
        int yes = countVotes(VoteChoice.YES);
        int no = countVotes(VoteChoice.NO);
        int participation = yes + no;
        int minimumParticipants = minimumRequiredParticipants();
        boolean participationMet = participation >= minimumParticipants;
        boolean skipCleanup = participationMet && yes > no;
        return new VoteOutcome(yes, no, participation, minimumParticipants, VOTE_ELIGIBLE.size(), participationMet, skipCleanup);
    }

    private static void announceVoteOutcome(MinecraftServer server, VoteOutcome outcome) {
        if (!outcome.participationMet()) {
            broadcast(server,
                    Component.literal("⚠ Votación finalizada: participación insuficiente. ")
                            .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                            .append(Component.literal("SÍ " + outcome.yesVotes() + " • NO " + outcome.noVotes()
                                            + " • Participación " + outcome.participation() + "/" + outcome.minimumParticipants())
                                    .withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(" • La limpieza continuará.")
                                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
            );
            return;
        }

        if (outcome.skipCleanup()) {
            broadcast(server,
                    Component.literal("✓ Votación finalizada: ")
                            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                            .append(Component.literal("SÍ " + outcome.yesVotes() + " • NO " + outcome.noVotes())
                                    .withStyle(ChatFormatting.WHITE))
                            .append(Component.literal(" • Esta limpieza fue SALTADA.")
                                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
            );
        } else {
            String reason = outcome.yesVotes() == outcome.noVotes() ? "empate" : "ganó NO";
            broadcast(server,
                    Component.literal("✕ Votación finalizada: ")
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                            .append(Component.literal("SÍ " + outcome.yesVotes() + " • NO " + outcome.noVotes())
                                    .withStyle(ChatFormatting.WHITE))
                            .append(Component.literal(" • " + reason + "; la limpieza continuará.")
                                    .withStyle(ChatFormatting.GRAY))
            );
        }
    }

    private static MutableComponent voteTallyComponent() {
        int yes = countVotes(VoteChoice.YES);
        int no = countVotes(VoteChoice.NO);
        int participation = yes + no;
        return Component.literal("SÍ " + yes)
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("NO " + no).withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                .append(Component.literal(" | Participación " + participation + "/" + VOTE_ELIGIBLE.size())
                        .withStyle(ChatFormatting.GRAY));
    }

    private static int countVotes(VoteChoice choice) {
        int count = 0;
        for (VoteChoice vote : VOTES.values()) {
            if (vote == choice) {
                count++;
            }
        }
        return count;
    }

    private static int minimumRequiredParticipants() {
        if (VOTE_ELIGIBLE.isEmpty()) {
            return 0;
        }
        CleanerConfig config = CleanerConfig.get();
        return Math.max(1, (int) Math.ceil(VOTE_ELIGIBLE.size() * (config.voteMinimumParticipationPercent / 100.0D)));
    }

    private static void closeVote() {
        voteActive = false;
        VOTE_ELIGIBLE.clear();
        VOTES.clear();
    }

    private static void scheduleNext() {
        CleanerConfig config = CleanerConfig.get();
        nextCleanupTick = serverTicks + (long) config.cleanupIntervalMinutes * 60L * 20L;
        warnedTenSeconds = false;
    }

    private static CleanupResult performCleanup(MinecraftServer server, boolean previewOnly) {
        CleanerConfig config = CleanerConfig.get();
        int scanned = 0;
        int removable = 0;
        int removed = 0;
        int protectedSpecial = 0;
        int protectedPlayer = 0;
        int protectedBattle = 0;
        int protectedRecent = 0;
        int protectedNearby = 0;
        int protectedOther = 0;

        List<PokemonEntity> candidates = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof PokemonEntity pokemonEntity && !pokemonEntity.isRemoved()) {
                    candidates.add(pokemonEntity);
                }
            }
        }

        for (PokemonEntity entity : candidates) {
            scanned++;
            ProtectionReason reason = protectionReason(entity, config);
            if (reason != ProtectionReason.NONE) {
                switch (reason) {
                    case SPECIAL -> protectedSpecial++;
                    case PLAYER_OR_TRAINER -> protectedPlayer++;
                    case BATTLE_OR_BUSY -> protectedBattle++;
                    case RECENT -> protectedRecent++;
                    case NEAR_PLAYER -> protectedNearby++;
                    case OTHER -> protectedOther++;
                    default -> { }
                }
                continue;
            }

            removable++;
            if (!previewOnly && removed < config.maxRemovalsPerCleanup) {
                FIRST_SEEN_TICK.remove(entity.getUUID());
                entity.discard();
                removed++;
            }
        }

        return new CleanupResult(
                scanned,
                removable,
                previewOnly ? 0 : removed,
                protectedSpecial,
                protectedPlayer,
                protectedBattle,
                protectedRecent,
                protectedNearby,
                protectedOther,
                previewOnly
        );
    }

    private static ProtectionReason protectionReason(PokemonEntity entity, CleanerConfig config) {
        if (entity.getTags().contains(PROTECTED_TAG)) {
            return ProtectionReason.OTHER;
        }

        Pokemon pokemon = entity.getPokemon();
        Set<String> labels = pokemon.getSpecies().getLabels();

        if ((config.protectShinies && pokemon.getShiny())
                || (config.protectLegendaries && labels.contains(CobblemonPokemonLabels.LEGENDARY))
                || (config.protectMythicals && labels.contains(CobblemonPokemonLabels.MYTHICAL))) {
            return ProtectionReason.SPECIAL;
        }

        OriginalTrainerType trainerType = pokemon.getOriginalTrainerType();
        if ((config.protectPlayerPokemon && trainerType == OriginalTrainerType.PLAYER)
                || (config.protectTrainerPokemon && trainerType == OriginalTrainerType.NPC)) {
            return ProtectionReason.PLAYER_OR_TRAINER;
        }

        if ((config.protectBattlingPokemon && entity.getBattleId() != null)
                || (config.protectBusyPokemon && !entity.getBusyLocks().isEmpty())) {
            return ProtectionReason.BATTLE_OR_BUSY;
        }

        if (config.protectTetheredPokemon && pokemon.getTetheringId() != null) {
            return ProtectionReason.OTHER;
        }

        long firstSeen = FIRST_SEEN_TICK.computeIfAbsent(entity.getUUID(), ignored -> serverTicks);
        long minimumAgeTicks = (long) config.minimumEntityAgeMinutes * 60L * 20L;
        if (serverTicks - firstSeen < minimumAgeTicks) {
            return ProtectionReason.RECENT;
        }

        if (config.protectNearPlayers && config.playerSafetyRadiusBlocks > 0) {
            double radiusSquared = (double) config.playerSafetyRadiusBlocks * config.playerSafetyRadiusBlocks;
            for (ServerPlayer player : ((ServerLevel) entity.level()).players()) {
                if (player.distanceToSqr(entity) <= radiusSquared) {
                    return ProtectionReason.NEAR_PLAYER;
                }
            }
        }

        return ProtectionReason.NONE;
    }

    private static void announceResult(MinecraftServer server, CleanupResult result) {
        CleanerConfig config = CleanerConfig.get();
        if (!config.announceCleanupSummary) {
            return;
        }

        broadcast(server,
                Component.literal("✓ Limpieza Cobblemon completada: ")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                        .append(Component.literal(result.removed() + " Pokémon salvajes eliminados")
                                .withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" • " + result.scanned() + " revisados")
                                .withStyle(ChatFormatting.GRAY))
        );
    }

    private static void broadcast(MinecraftServer server, Component component) {
        server.getPlayerList().broadcastSystemMessage(component, false);
    }

    private enum VoteChoice {
        YES,
        NO
    }

    private enum ProtectionReason {
        NONE,
        SPECIAL,
        PLAYER_OR_TRAINER,
        BATTLE_OR_BUSY,
        RECENT,
        NEAR_PLAYER,
        OTHER
    }

    private record VoteOutcome(
            int yesVotes,
            int noVotes,
            int participation,
            int minimumParticipants,
            int eligiblePlayers,
            boolean participationMet,
            boolean skipCleanup
    ) {
    }

    public record CleanupResult(
            int scanned,
            int removable,
            int removed,
            int protectedSpecial,
            int protectedPlayerOrTrainer,
            int protectedBattleOrBusy,
            int protectedRecent,
            int protectedNearby,
            int protectedOther,
            boolean preview
    ) {
    }
}
