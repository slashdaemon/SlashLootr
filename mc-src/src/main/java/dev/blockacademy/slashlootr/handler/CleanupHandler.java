package dev.blockacademy.slashlootr.handler;

import dev.blockacademy.slashlootr.compat.Ids;
import dev.blockacademy.slashlootr.config.SlashLootrConfig;
import dev.blockacademy.slashlootr.core.DebugLog;
import dev.blockacademy.slashlootr.core.Handling;
import dev.blockacademy.slashlootr.store.SlashLootrState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps {@code world/&lt;dim&gt;/data/slashlootr.dat} from growing without bound on a long-running
 * server. Three layers, because no single one covers every way a container can disappear:
 *
 * <ol>
 *   <li><b>Player break</b> — immediate, via the loader's block-break event. Covers the common case.
 *   <li><b>Entity removal</b> — {@code MixinEntityRemoved}, loader-neutral, covers minecarts and
 *       chest boats destroyed by any means.
 *   <li><b>Background prune</b> — this class. Catches everything else (TNT, creepers, pistons,
 *       WorldEdit, /setblock) by re-checking stored positions whose chunk happens to be loaded.
 *       Entries in unloaded chunks are left untouched and revisited on a later pass.
 * </ol>
 */
public final class CleanupHandler {

    /**
     * Per-dimension drain queue for the background prune. Keyed by {@code ResourceKey<Level>}
     * rather than a dimension string: this map is consulted on every server tick, and rendering
     * the id would allocate a String per dimension per tick for nothing.
     */
    private static final Map<ResourceKey<Level>, Deque<Long>> PENDING = new HashMap<>();

    private static int tickCounter = 0;
    private static int lastRemoved = 0;

    private CleanupHandler() {}

    // ------------------------------------------------------------ immediate

    public static void onBlockBroken(ServerLevel level, BlockPos pos) {
        if (!SlashLootrConfig.get().cleanupOnBreak) return;
        SlashLootrState store = SlashLootrState.get(level);
        if (!store.hasBlockEntry(pos.asLong())) return;
        store.forgetBlock(pos.asLong());
        DebugLog.action("[SlashLoot] forgot stored loot at " + Ids.dimension(level) + " ["
                + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "] (block broken)");
    }

    /** Called from {@code MixinEntityRemoved} when a container entity is destroyed for good. */
    public static void onEntityDestroyed(ServerLevel level, UUID entityId) {
        if (!SlashLootrConfig.get().cleanupOnBreak) return;
        SlashLootrState store = SlashLootrState.get(level);
        if (!store.hasEntityEntry(entityId)) return;
        store.forgetEntity(entityId);
        DebugLog.action("[SlashLoot] forgot stored loot for entity " + entityId + " (destroyed)");
    }

    // ----------------------------------------------------------- background

    public static void onServerTick(MinecraftServer server) {
        SlashLootrConfig config = SlashLootrConfig.get();
        if (config.pruneIntervalTicks <= 0) return;

        tickCounter++;
        boolean startNewPass = tickCounter % config.pruneIntervalTicks == 0;

        for (ServerLevel level : server.getAllLevels()) {
            ResourceKey<Level> key = level.dimension();
            Deque<Long> queue = PENDING.get(key);

            if (startNewPass && (queue == null || queue.isEmpty())) {
                long[] keys = SlashLootrState.get(level).blockKeys();
                if (keys.length == 0) continue;
                queue = new ArrayDeque<>(keys.length);
                for (long packed : keys) queue.add(packed);
                PENDING.put(key, queue);
            }
            if (queue == null || queue.isEmpty()) continue;

            drain(level, queue, config.pruneBatchSize);
        }
    }

    private static void drain(ServerLevel level, Deque<Long> queue, int budget) {
        SlashLootrState store = SlashLootrState.get(level);
        int removed = 0;
        for (int i = 0; i < budget && !queue.isEmpty(); i++) {
            long packed = queue.poll();
            BlockPos pos = BlockPos.of(packed);
            // An unloaded chunk tells us nothing — leave the entry for a later pass.
            if (!level.isLoaded(pos)) continue;
            if (Handling.forBlock(level, pos, level.getBlockEntity(pos)).instanced()) continue;
            store.forgetBlock(packed);
            removed++;
        }
        if (removed > 0) {
            lastRemoved += removed;
            DebugLog.action("[SlashLoot] pruned " + removed + " stale container entries in "
                    + Ids.dimension(level));
        }
    }

    /**
     * Runs a full synchronous sweep of one dimension. Backs {@code /slashloot prune}.
     *
     * @return number of entries dropped
     */
    public static int pruneNow(ServerLevel level) {
        SlashLootrState store = SlashLootrState.get(level);
        int removed = 0;
        int skipped = 0;
        for (long packed : store.blockKeys()) {
            BlockPos pos = BlockPos.of(packed);
            if (!level.isLoaded(pos)) {
                skipped++;
                continue;
            }
            if (Handling.forBlock(level, pos, level.getBlockEntity(pos)).instanced()) continue;
            store.forgetBlock(packed);
            removed++;
        }
        PENDING.remove(level.dimension());
        DebugLog.action("[SlashLoot] manual prune in " + Ids.dimension(level)
                + ": removed=" + removed + " skipped(unloaded)=" + skipped);
        return removed;
    }

    /** Entries the background prune has dropped since server start. */
    public static int totalPruned() {
        return lastRemoved;
    }

    /** Positions still queued for the current background pass, across all dimensions. */
    public static int queuedForPrune() {
        int n = 0;
        for (Deque<Long> q : PENDING.values()) n += q.size();
        return n;
    }
}
