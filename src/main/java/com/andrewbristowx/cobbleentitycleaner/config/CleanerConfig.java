package com.andrewbristowx.cobbleentitycleaner.config;

import com.andrewbristowx.cobbleentitycleaner.CobbleEntityCleaner;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CleanerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("cobbleentitycleaner.json");

    private static CleanerConfig INSTANCE = new CleanerConfig();

    public boolean enabled = true;

    public int cleanupIntervalMinutes = 20;
    public int minimumEntityAgeMinutes = 5;
    public boolean protectNearPlayers = true;
    public int playerSafetyRadiusBlocks = 48;
    public int maxRemovalsPerCleanup = 2000;

    public boolean protectShinies = true;
    public boolean protectLegendaries = true;
    public boolean protectMythicals = true;
    public boolean protectPlayerPokemon = true;
    public boolean protectTrainerPokemon = true;
    public boolean protectBattlingPokemon = true;
    public boolean protectBusyPokemon = true;
    public boolean protectTetheredPokemon = true;

    public boolean voteSkipEnabled = true;
    public int voteWindowSeconds = 60;
    public int voteRequiredPercent = 50;
    public int voteMinimumYes = 1;

    public boolean announceWarnings = true;
    public boolean announceCleanupSummary = true;

    public static CleanerConfig get() {
        return INSTANCE;
    }

    public static void load() {
        CleanerConfig loaded = new CleanerConfig();

        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                CleanerConfig parsed = GSON.fromJson(reader, CleanerConfig.class);
                if (parsed != null) {
                    loaded = parsed;
                }
            } catch (Exception exception) {
                CobbleEntityCleaner.LOGGER.error("Could not read {}. Using safe defaults.", CONFIG_PATH, exception);
            }
        }

        loaded.normalize();
        INSTANCE = loaded;
        save();
    }

    public static void reload() {
        load();
    }

    private void normalize() {
        cleanupIntervalMinutes = Math.max(2, Math.min(cleanupIntervalMinutes, 1440));
        minimumEntityAgeMinutes = Math.max(0, Math.min(minimumEntityAgeMinutes, 120));
        playerSafetyRadiusBlocks = Math.max(0, Math.min(playerSafetyRadiusBlocks, 256));
        maxRemovalsPerCleanup = Math.max(1, Math.min(maxRemovalsPerCleanup, 10000));

        voteWindowSeconds = Math.max(10, Math.min(voteWindowSeconds, Math.max(10, cleanupIntervalMinutes * 60 - 1)));
        voteRequiredPercent = Math.max(1, Math.min(voteRequiredPercent, 100));
        voteMinimumYes = Math.max(1, Math.min(voteMinimumYes, 100));
    }

    private static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (IOException exception) {
            CobbleEntityCleaner.LOGGER.error("Could not write {}", CONFIG_PATH, exception);
        }
    }
}
