package dev.blockacademy.slashlootr;

import dev.blockacademy.slashlootr.command.SlashLootCommand;
import dev.blockacademy.slashlootr.config.SlashLootrConfig;
import dev.blockacademy.slashlootr.handler.CleanupHandler;
import dev.blockacademy.slashlootr.handler.ContainerInteractionHandler;
import dev.blockacademy.slashlootr.handler.EntityInteractionHandler;
import dev.blockacademy.slashlootr.loader.LoaderBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loader-agnostic entrypoint. Each loader's entrypoint builds a {@link LoaderBridge} and calls
 * {@link #boot(LoaderBridge)}; everything after that point is identical on Fabric and NeoForge.
 */
public final class SlashLootrCore {
    public static final String MOD_ID = "slashlootr";
    public static final Logger LOG = LoggerFactory.getLogger("SlashLoot");

    private static LoaderBridge bridge;

    private SlashLootrCore() {}

    public static void boot(LoaderBridge loaderBridge) {
        bridge = loaderBridge;
        SlashLootrConfig.load(loaderBridge.configDir());

        loaderBridge.onUseBlock(ContainerInteractionHandler::interact);
        loaderBridge.onUseEntity(EntityInteractionHandler::interact);
        loaderBridge.onRegisterCommands(SlashLootCommand::register);
        loaderBridge.onPlayerBreakBlock(CleanupHandler::onBlockBroken);
        loaderBridge.onServerTick(CleanupHandler::onServerTick);

        LOG.info("SlashLoot loaded - server-side per-player loot, no client install required");
    }

    public static LoaderBridge bridge() {
        return bridge;
    }
}
