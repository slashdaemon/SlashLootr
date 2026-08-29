package dev.blockacademy.slashlootr;

import dev.blockacademy.slashlootr.command.SlashLootCommand;
import dev.blockacademy.slashlootr.config.SlashLootrConfig;
import dev.blockacademy.slashlootr.handler.CleanupHandler;
import dev.blockacademy.slashlootr.handler.ContainerInteractionHandler;
import dev.blockacademy.slashlootr.handler.EntityInteractionHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Band A entrypoint. The other bands go through a LoaderBridge so one entrypoint serves both
 * Fabric and NeoForge; 1.20.1 is Fabric-only and self-contained, so it wires the events directly.
 */
public class SlashLootrMod implements ModInitializer {
    public static final String MOD_ID = "slashlootr";
    public static final Logger LOG = LoggerFactory.getLogger("SlashLoot");

    @Override
    public void onInitialize() {
        SlashLootrConfig.load();
        UseBlockCallback.EVENT.register(new ContainerInteractionHandler());
        UseEntityCallback.EVENT.register(new EntityInteractionHandler());
        CommandRegistrationCallback.EVENT.register(SlashLootCommand::register);
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world instanceof ServerLevel level) CleanupHandler.onBlockBroken(level, pos);
        });
        ServerTickEvents.END_SERVER_TICK.register(CleanupHandler::onServerTick);
        LOG.info("SlashLoot loaded - server-side per-player loot, no client install required");
    }
}
