package dev.blockacademy.slashlootr.v1_21_1;

import dev.blockacademy.slashlootr.v1_21_1.command.SlashLootrCommand;
import dev.blockacademy.slashlootr.v1_21_1.config.SlashLootrConfig;
import dev.blockacademy.slashlootr.v1_21_1.handler.ContainerInteractionHandler;
import dev.blockacademy.slashlootr.v1_21_1.handler.EntityInteractionHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SlashLootrMod implements ModInitializer {
    public static final String MOD_ID = "slashlootr";
    public static final Logger LOG = LoggerFactory.getLogger("SlashLootr");

    @Override
    public void onInitialize() {
        SlashLootrConfig.load();
        UseBlockCallback.EVENT.register(new ContainerInteractionHandler());
        UseEntityCallback.EVENT.register(new EntityInteractionHandler());
        CommandRegistrationCallback.EVENT.register(SlashLootrCommand::register);
        LOG.info("SlashLootr loaded - server-side per-player loot, no client install required");
    }
}
