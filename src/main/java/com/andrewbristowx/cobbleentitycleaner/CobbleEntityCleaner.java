package com.andrewbristowx.cobbleentitycleaner;

import com.andrewbristowx.cobbleentitycleaner.cleanup.CleanupService;
import com.andrewbristowx.cobbleentitycleaner.command.CleanerCommand;
import com.andrewbristowx.cobbleentitycleaner.config.CleanerConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CobbleEntityCleaner implements ModInitializer {
    public static final String MOD_ID = "cobbleentitycleaner";
    public static final Logger LOGGER = LoggerFactory.getLogger("CobbleEntity Cleaner");

    @Override
    public void onInitialize() {
        CleanerConfig.load();

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> CleanupService.markLoaded(entity));
        ServerTickEvents.END_SERVER_TICK.register(CleanupService::tick);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                CleanerCommand.register(dispatcher)
        );

        LOGGER.info("CobbleEntity Cleaner 0.1.0-alpha.2 enabled: safe wild-Pokemon cleanup and clickable yes/no vote active.");
    }
}
