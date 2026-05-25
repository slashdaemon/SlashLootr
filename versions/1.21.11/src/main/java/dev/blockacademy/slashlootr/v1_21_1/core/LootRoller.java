package dev.blockacademy.slashlootr.v1_21_1.core;

import dev.blockacademy.slashlootr.common.SeedDeriver;
import dev.blockacademy.slashlootr.v1_21_1.SlashLootrMod;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/** Rolls a per-player SimpleContainer from a loot table using a player-derived seed. */
public final class LootRoller {
    private LootRoller() {}

    public static SimpleContainer rollForBlock(
            ServerLevel level,
            BlockPos pos,
            ResourceKey<LootTable> tableKey,
            long containerSeed,
            ServerPlayer player,
            int slots) {
        SimpleContainer container = new SimpleContainer(slots);
        LootTable table = level.getServer().reloadableRegistries().getLootTable(tableKey);
        if (table == LootTable.EMPTY) {
            SlashLootrMod.LOG.warn("Loot table {} not found for chest at {} - serving empty inventory", tableKey.identifier(), pos);
            return container;
        }
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withLuck(player.getLuck())
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .create(LootContextParamSets.CHEST);
        long seed = SeedDeriver.derive(containerSeed, player.getUUID());
        table.fill(container, params, seed);
        return container;
    }

    public static SimpleContainer rollForEntity(
            ServerLevel level,
            Entity entity,
            ResourceKey<LootTable> tableKey,
            long containerSeed,
            ServerPlayer player,
            int slots) {
        SimpleContainer container = new SimpleContainer(slots);
        LootTable table = level.getServer().reloadableRegistries().getLootTable(tableKey);
        if (table == LootTable.EMPTY) {
            SlashLootrMod.LOG.warn("Loot table {} not found for entity {} - serving empty inventory", tableKey.identifier(), entity.getUUID());
            return container;
        }
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, entity.position())
                .withLuck(player.getLuck())
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .create(LootContextParamSets.CHEST);
        long seed = SeedDeriver.derive(containerSeed, player.getUUID());
        table.fill(container, params, seed);
        return container;
    }
}
