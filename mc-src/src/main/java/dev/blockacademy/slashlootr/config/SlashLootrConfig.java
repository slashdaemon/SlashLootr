package dev.blockacademy.slashlootr.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.blockacademy.slashlootr.SlashLootrCore;
import dev.blockacademy.slashlootr.core.DebugLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * {@code config/slashlootr.json}. Loader-agnostic: the directory arrives from
 * {@link dev.blockacademy.slashlootr.loader.LoaderBridge#configDir()}.
 *
 * <p>Blocklists are compared as plain namespaced strings so this class never has to name
 * {@code ResourceLocation} / {@code Identifier}, which swapped identity at MC 1.21.11. Callers pass
 * strings produced by {@link dev.blockacademy.slashlootr.compat.Ids}.
 *
 * <p>Every field has a safe default and unknown/missing keys are tolerated, so a config written by
 * an older version keeps working after an update.
 */
public final class SlashLootrConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static SlashLootrConfig INSTANCE = new SlashLootrConfig();
    private static Path path;

    /** Master switch. False makes SlashLoot inert — every container behaves like vanilla. */
    public boolean enabled = true;

    /** Dimension ids (e.g. {@code minecraft:the_nether}) served as plain vanilla shared loot. */
    public Set<String> dimensionBlocklist = new HashSet<>();

    /** Loot table ids (e.g. {@code minecraft:chests/end_city_treasure}) served as vanilla loot. */
    public Set<String> lootTableBlocklist = new HashSet<>();

    /**
     * Instance containers we do not specifically recognise but which still implement
     * {@code RandomizableContainer} / {@code ContainerEntity}. Off by default: leaving unknown
     * modded containers to vanilla is the compatible choice.
     */
    public boolean handleUnknownContainers = false;

    /**
     * Forward open/close to the real world container so lids animate, barrels flip their
     * {@code open} state, and trapped chests emit redstone. Vanilla plays the open sound itself
     * when this is on, so {@link #playOpenCloseSounds} only applies when this is off.
     */
    public boolean delegateContainerAnimation = true;

    /** Manual open sound. Only used when {@link #delegateContainerAnimation} is false. */
    public boolean playOpenCloseSounds = true;

    /** Drop a container's stored per-player loot when a player breaks it. */
    public boolean cleanupOnBreak = true;

    /**
     * Ticks between background prune passes over stored entries in loaded chunks, catching
     * containers destroyed by explosions, pistons, or world edits. 0 disables.
     */
    public int pruneIntervalTicks = 6000;

    /** Entries examined per prune pass. Keeps the sweep off the tick budget on large saves. */
    public int pruneBatchSize = 256;

    /** Log every instance-or-vanilla decision, with position, loot table, and reason. */
    public boolean debugLogging = false;

    public static SlashLootrConfig get() {
        return INSTANCE;
    }

    public static void load(Path configDir) {
        path = configDir.resolve("slashlootr.json");
        reload();
    }

    /** Re-reads the file from disk. Safe to call at runtime; used by {@code /slashloot reload}. */
    public static void reload() {
        if (path == null) return;
        try {
            if (Files.exists(path)) {
                SlashLootrConfig loaded = GSON.fromJson(Files.readString(path), SlashLootrConfig.class);
                INSTANCE = loaded == null ? new SlashLootrConfig() : loaded.withDefaults();
            } else {
                Files.createDirectories(path.getParent());
                Files.writeString(path, GSON.toJson(INSTANCE));
            }
        } catch (IOException | RuntimeException e) {
            SlashLootrCore.LOG.warn("Failed to load slashlootr.json, using defaults", e);
            INSTANCE = new SlashLootrConfig();
        }
        DebugLog.reset();
    }

    /** Repairs nulls left by a partial or hand-edited JSON file. */
    private SlashLootrConfig withDefaults() {
        if (dimensionBlocklist == null) dimensionBlocklist = new HashSet<>();
        if (lootTableBlocklist == null) lootTableBlocklist = new HashSet<>();
        if (pruneBatchSize <= 0) pruneBatchSize = 256;
        return this;
    }

    public boolean isDimensionBlocked(String dimensionId) {
        return !dimensionBlocklist.isEmpty() && dimensionBlocklist.contains(dimensionId);
    }

    public boolean isLootTableBlocked(String lootTableId) {
        return !lootTableBlocklist.isEmpty() && lootTableBlocklist.contains(lootTableId);
    }
}
