package dev.blockacademy.slashlootr.loader.neoforge;

import dev.blockacademy.slashlootr.SlashLootrCore;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * NeoForge entrypoint. Builds the bridge, hands it to the shared core, and gets out of the way —
 * the mirror of {@code SlashLootrFabric}.
 *
 * <p>Nothing is registered on the mod event bus: SlashLoot adds no blocks, items, or payloads. All
 * of its hooks are game-bus events, wired inside {@link NeoForgeBridge}.
 */
@Mod("slashlootr")
public class SlashLootrNeoForge {

    public SlashLootrNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        SlashLootrCore.boot(new NeoForgeBridge());
    }
}
