package com.andrewbristowx.cobbleentitycleaner.diagnostics;

import com.andrewbristowx.cobbleentitycleaner.cleanup.CleanupService;
import com.andrewbristowx.cobbleentitycleaner.config.CleanerConfig;
import com.cobblemon.mod.common.api.pokemon.labels.CobblemonPokemonLabels;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.OriginalTrainerType;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CleanerDiagnostics {
    private CleanerDiagnostics() {
    }

    public static Component decorateStatus(MinecraftServer server, Component base) {
        Snapshot snapshot = snapshot(server);
        CleanerConfig config = CleanerConfig.get();
        return base.copy()
                .append(Component.literal(" • Cargados: " + snapshot.total()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" • Elegibles: " + snapshot.eligible()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" • Radio: " + config.playerSafetyRadiusBlocks + "b").withStyle(ChatFormatting.GRAY));
    }

    public static List<Component> stats(MinecraftServer server) {
        Snapshot snapshot = snapshot(server);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("CobbleEntity Cleaner — Estadísticas")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        lines.add(Component.literal("Cargados: " + snapshot.total())
                .withStyle(ChatFormatting.WHITE)
                .append(Component.literal(" • Salvajes sin dueño: " + snapshot.wildUnowned()).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" • Elegibles ahora: " + snapshot.eligible()).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)));
        lines.add(Component.literal("Protegidos → ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("especiales: " + snapshot.special()).withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal(" | jugador/NPC: " + snapshot.owned()).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" | batalla/busy: " + snapshot.battleOrBusy()).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" | Pasture: " + snapshot.tethered()).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" | radio: " + snapshot.nearby()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" | manual: " + snapshot.manual()).withStyle(ChatFormatting.GRAY)));
        lines.add(Component.literal("Radio seguro actual: " + CleanerConfig.get().playerSafetyRadiusBlocks + " bloques")
                .withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    public static List<Component> worldStats(MinecraftServer server) {
        Snapshot snapshot = snapshot(server);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("CobbleEntity Cleaner — Pokémon por mundo")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));

        if (snapshot.worldCounts().isEmpty()) {
            lines.add(Component.literal("No hay Pokémon cargados.").withStyle(ChatFormatting.GRAY));
            return lines;
        }

        snapshot.worldCounts().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> lines.add(
                        Component.literal(entry.getKey() + ": ").withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(String.valueOf(entry.getValue())).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                ));
        return lines;
    }

    public static List<Component> hotspots(MinecraftServer server, int limit) {
        Map<HotspotKey, HotspotAccumulator> hotspots = new HashMap<>();

        for (ServerLevel level : server.getAllLevels()) {
            String dimension = level.dimension().location().toString();
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof PokemonEntity pokemonEntity) || pokemonEntity.isRemoved()) {
                    continue;
                }

                BlockPos pos = pokemonEntity.blockPosition();
                int chunkX = pos.getX() >> 4;
                int chunkZ = pos.getZ() >> 4;
                HotspotKey key = new HotspotKey(dimension, chunkX, chunkZ);
                HotspotAccumulator accumulator = hotspots.computeIfAbsent(
                        key,
                        ignored -> new HotspotAccumulator(dimension, chunkX, chunkZ, pos.getX(), pos.getY(), pos.getZ())
                );
                accumulator.count++;
            }
        }

        List<HotspotAccumulator> sorted = hotspots.values().stream()
                .sorted(Comparator.comparingInt((HotspotAccumulator value) -> value.count).reversed())
                .limit(Math.max(1, limit))
                .toList();

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("CobbleEntity Cleaner — Hotspots")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));

        if (sorted.isEmpty()) {
            lines.add(Component.literal("No hay Pokémon cargados.").withStyle(ChatFormatting.GRAY));
            return lines;
        }

        int index = 1;
        for (HotspotAccumulator hotspot : sorted) {
            String coords = hotspot.sampleX + " " + hotspot.sampleY + " " + hotspot.sampleZ;
            MutableComponent copyButton = Component.literal("[COPIAR]")
                    .withStyle(style -> style
                            .withColor(ChatFormatting.YELLOW)
                            .withBold(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, coords))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("Copiar coordenadas " + coords))));

            String teleportCommand = "/execute in " + hotspot.dimension
                    + " run tp @s " + hotspot.sampleX + " " + hotspot.sampleY + " " + hotspot.sampleZ;
            MutableComponent teleportButton = Component.literal("[TP]")
                    .withStyle(style -> style
                            .withColor(ChatFormatting.GREEN)
                            .withBold(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, teleportCommand))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("Teletransportarte a una posición de este chunk"))));

            lines.add(Component.literal(index + ". ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(hotspot.dimension).withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(" • chunk " + hotspot.chunkX + ", " + hotspot.chunkZ).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" → " + hotspot.count + " Pokémon ").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                    .append(copyButton)
                    .append(Component.literal(" "))
                    .append(teleportButton));
            index++;
        }
        return lines;
    }

    public static Snapshot snapshot(MinecraftServer server) {
        CleanerConfig config = CleanerConfig.get();
        int total = 0;
        int wildUnowned = 0;
        int eligible = 0;
        int special = 0;
        int owned = 0;
        int battleOrBusy = 0;
        int tethered = 0;
        int nearby = 0;
        int manual = 0;
        Map<String, Integer> worldCounts = new LinkedHashMap<>();

        for (ServerLevel level : server.getAllLevels()) {
            String dimension = level.dimension().location().toString();
            int worldTotal = 0;

            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof PokemonEntity pokemonEntity) || pokemonEntity.isRemoved()) {
                    continue;
                }

                total++;
                worldTotal++;
                Pokemon pokemon = pokemonEntity.getPokemon();
                OriginalTrainerType trainerType = pokemon.getOriginalTrainerType();
                if (trainerType != OriginalTrainerType.PLAYER && trainerType != OriginalTrainerType.NPC) {
                    wildUnowned++;
                }

                Reason reason = primaryProtectionReason(pokemonEntity, config);
                switch (reason) {
                    case NONE -> eligible++;
                    case SPECIAL -> special++;
                    case OWNED -> owned++;
                    case BATTLE_OR_BUSY -> battleOrBusy++;
                    case TETHERED -> tethered++;
                    case NEAR_PLAYER -> nearby++;
                    case MANUAL -> manual++;
                }
            }

            if (worldTotal > 0) {
                worldCounts.put(dimension, worldTotal);
            }
        }

        return new Snapshot(total, wildUnowned, eligible, special, owned, battleOrBusy, tethered, nearby, manual, worldCounts);
    }

    private static Reason primaryProtectionReason(PokemonEntity entity, CleanerConfig config) {
        if (entity.getTags().contains(CleanupService.PROTECTED_TAG)) {
            return Reason.MANUAL;
        }

        Pokemon pokemon = entity.getPokemon();
        Set<String> labels = pokemon.getSpecies().getLabels();
        if ((config.protectShinies && pokemon.getShiny())
                || (config.protectLegendaries && labels.contains(CobblemonPokemonLabels.LEGENDARY))
                || (config.protectMythicals && labels.contains(CobblemonPokemonLabels.MYTHICAL))) {
            return Reason.SPECIAL;
        }

        OriginalTrainerType trainerType = pokemon.getOriginalTrainerType();
        if ((config.protectPlayerPokemon && trainerType == OriginalTrainerType.PLAYER)
                || (config.protectTrainerPokemon && trainerType == OriginalTrainerType.NPC)) {
            return Reason.OWNED;
        }

        if ((config.protectBattlingPokemon && entity.getBattleId() != null)
                || (config.protectBusyPokemon && !entity.getBusyLocks().isEmpty())) {
            return Reason.BATTLE_OR_BUSY;
        }

        if (config.protectTetheredPokemon && pokemon.getTetheringId() != null) {
            return Reason.TETHERED;
        }

        if (config.protectNearPlayers && config.playerSafetyRadiusBlocks > 0) {
            double radiusSquared = (double) config.playerSafetyRadiusBlocks * config.playerSafetyRadiusBlocks;
            for (ServerPlayer player : ((ServerLevel) entity.level()).players()) {
                if (player.distanceToSqr(entity) <= radiusSquared) {
                    return Reason.NEAR_PLAYER;
                }
            }
        }

        return Reason.NONE;
    }

    private enum Reason {
        NONE,
        SPECIAL,
        OWNED,
        BATTLE_OR_BUSY,
        TETHERED,
        NEAR_PLAYER,
        MANUAL
    }

    private record HotspotKey(String dimension, int chunkX, int chunkZ) {
    }

    private static final class HotspotAccumulator {
        private final String dimension;
        private final int chunkX;
        private final int chunkZ;
        private final int sampleX;
        private final int sampleY;
        private final int sampleZ;
        private int count;

        private HotspotAccumulator(String dimension, int chunkX, int chunkZ, int sampleX, int sampleY, int sampleZ) {
            this.dimension = dimension;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.sampleX = sampleX;
            this.sampleY = sampleY;
            this.sampleZ = sampleZ;
        }
    }

    public record Snapshot(
            int total,
            int wildUnowned,
            int eligible,
            int special,
            int owned,
            int battleOrBusy,
            int tethered,
            int nearby,
            int manual,
            Map<String, Integer> worldCounts
    ) {
    }
}
