package dev.blockacademy.slashlootr.v1_21_1.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.blockacademy.slashlootr.v1_21_1.SlashLootrMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public final class SlashLootrConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static SlashLootrConfig INSTANCE = new SlashLootrConfig();

    public Set<String> dimensionBlocklist = new HashSet<>();
    public Set<String> lootTableBlocklist = new HashSet<>();
    public boolean playOpenCloseSounds = true;

    public static SlashLootrConfig get() {
        return INSTANCE;
    }

    public static void load() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("slashlootr.json");
        try {
            if (Files.exists(configPath)) {
                String json = Files.readString(configPath);
                INSTANCE = GSON.fromJson(json, SlashLootrConfig.class);
                if (INSTANCE == null) INSTANCE = new SlashLootrConfig();
                if (INSTANCE.dimensionBlocklist == null) INSTANCE.dimensionBlocklist = new HashSet<>();
                if (INSTANCE.lootTableBlocklist == null) INSTANCE.lootTableBlocklist = new HashSet<>();
            } else {
                Files.writeString(configPath, GSON.toJson(INSTANCE));
            }
        } catch (IOException e) {
            SlashLootrMod.LOG.warn("Failed to load slashlootr.json, using defaults", e);
        }
    }

    public boolean isDimensionBlocked(ResourceLocation dimId) {
        return dimensionBlocklist.contains(dimId.toString());
    }

    public boolean isLootTableBlocked(ResourceLocation tableId) {
        return lootTableBlocklist.contains(tableId.toString());
    }
}
