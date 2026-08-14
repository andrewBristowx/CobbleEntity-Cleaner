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
    private static final Set<UUID> SKIP_VOTES = new HashSet<>();

    private static long serverTicks;
    private static long nextCleanupTick;
    private static boolean voteActive;
    private static boolean warnedTenSeconds;
    private static int requiredSkipVotes;

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
            broadcast(server, Component.literal("⚠ Limpieza de Pokémon salvajes en 10 segundos.")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        }

        if (remaining <= 0L) {
            closeVote();
            CleanupResult result = performCleanup(server, false);
            announceResult(server, result);
            scheduleNext();
        }
    }

    public static int voteSkip(ServerPlayer player) {
        CleanerConfig config = CleanerConfig.get();
        if (!config.voteSkipEnabled) {
            player.sendSystemMessage(Component.literal("La votación para saltar limpiezas está desactivada.")
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
        if (!SKIP_VOTES.add(player.getUUID())) {
            player.sendSystemMessage(Component.literal("Ya votaste para saltar esta limpieza.")
                    .withStyle(ChatFormatting.GRAY));
            return 0;
        }

        MinecraftServer server = player.server;
        broadcast(server, Component.literal("☑ ")
                .withStyle(ChatFormatting.GREEN)
                .append(player.getName().copy().withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" votó por saltar la limpieza. ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(SKIP_VOTES.size() + "/" + requiredSkipVotes)
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)));

        if (SKIP_VOTES.size() >= requiredSkipVotes) {
            broadcast(server, Component.literal("✓ Votación aprobada: esta limpieza de Pokémon salvajes fue cancelada.")
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            closeVote();
            scheduleNext();
            return 1;
        }
        return 1;
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
            component.append(Component.literal(" • Votos skip: " + SKIP_VOTES.size() + "/" + requiredSkipVotes)
                    .withStyle(ChatFormatting.YELLOW));
        }
        return component;
    }

    private static void openVote(MinecraftServer server) {
        CleanerConfig config = CleanerConfig.get();
        voteActive = true;
        warnedTenSeconds = false;
        VOTE_ELIGIBLE.clear();
        SKIP_VOTES.clear();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            VOTE_ELIGIBLE.add(player.getUUID());
        }

        int percentageRequired = (int) Math.ceil(VOTE_ELIGIBLE.size() * (config.voteRequiredPercent / 100.0D));
        requiredSkipVotes = Math.max(config.voteMinimumYes, percentageRequired);
        requiredSkipVotes = Math.max(1, requiredSkipVotes);

        if (config.announceWarnings) {
            broadcast(server, Component.literal("⚠ Limpieza de Pokémon salvajes en " + config.voteWindowSeconds + " segundos.")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        }

        MutableComponent voteButton = Component.literal("[ VOTAR PARA SALTAR ]")
                .withStyle(style -> style
                        .withColor(ChatFormatting.GREEN)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cobblecleaner vote skip"))
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Cancela esta ronda de limpieza si se alcanzan suficientes votos")
                        )));

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(
                    Component.literal("Si estás intentando capturar Pokémon y necesitas más tiempo, vota aquí: ")
                            .withStyle(ChatFormatting.GRAY)
                            .append(voteButton.copy())
            );
        }

        CobbleEntityCleaner.LOGGER.info(
                "Cleanup vote opened: {} eligible player(s), {} skip vote(s) required",
                VOTE_ELIGIBLE.size(),
                requiredSkipVotes
        );
    }

    private static void closeVote() {
        voteActive = false;
        VOTE_ELIGIBLE.clear();
        SKIP_VOTES.clear();
        requiredSkipVotes = 0;
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

    private enum ProtectionReason {
        NONE,
        SPECIAL,
        PLAYER_OR_TRAINER,
        BATTLE_OR_BUSY,
        RECENT,
        NEAR_PLAYER,
        OTHER
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
