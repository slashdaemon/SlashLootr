package dev.blockacademy.slashlootr.loader.fabric;

import dev.blockacademy.slashlootr.SlashLootrCore;
import net.fabricmc.api.ModInitializer;

/** Fabric entrypoint. Builds the bridge, hands it to the shared core, and gets out of the way. */
public class SlashLootrFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        SlashLootrCore.boot(new FabricBridge());
    }
}
