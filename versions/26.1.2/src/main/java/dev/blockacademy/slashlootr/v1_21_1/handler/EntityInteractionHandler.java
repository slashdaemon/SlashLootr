package dev.blockacademy.slashlootr.v1_21_1.handler;

import dev.blockacademy.slashlootr.v1_21_1.config.SlashLootrConfig;
import dev.blockacademy.slashlootr.v1_21_1.core.ContainerKind;
import dev.blockacademy.slashlootr.v1_21_1.core.LootRoller;
import dev.blockacademy.slashlootr.v1_21_1.store.PlayerLootEntry;
import dev.blockacademy.slashlootr.v1_21_1.store.SlashLootrState;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.ChestBoat;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.entity.vehicle.minecart.MinecartHopper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Chest minecart / hopper minecart / chest boat equivalent of
 * {@link ContainerInteractionHandler}. Same logic, different identity (entity UUID).
 */
public class EntityInteractionHandler implements UseEntityCallback {

    @Override
    public InteractionResult interact(Player player, Level world, InteractionHand hand, Entity entity, EntityHitResult hit) {
        if (world.isClientSide()) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        if (player.isSpectator()) return InteractionResult.PASS;
        if (!(entity instanceof ContainerEntity ce)) return InteractionResult.PASS;

        ResourceKey<LootTable> table = ce.getContainerLootTable();
        if (table == null) return InteractionResult.PASS;

        ServerLevel level = (ServerLevel) world;
        if (SlashLootrConfig.get().isDimensionBlocked(level.dimension().identifier())) return InteractionResult.PASS;
        if (SlashLootrConfig.get().isLootTableBlocked(table.identifier())) return InteractionResult.PASS;

        ContainerKind kind;
        if (entity instanceof MinecartHopper) kind = ContainerKind.MINECART_HOPPER;
        else if (entity instanceof MinecartChest) kind = ContainerKind.MINECART_CHEST;
        else if (entity instanceof ChestBoat) kind = ContainerKind.CHEST_BOAT;
        else return InteractionResult.PASS;

        // Chest boat additionally checks "player not sneaking" — vanilla unmounts otherwise.
        if (entity instanceof ChestBoat && player.isSecondaryUseActive()) return InteractionResult.PASS;

        SlashLootrState store = SlashLootrState.get(level);
        PlayerLootEntry entry = store.entityEntry(entity.getUUID());
        SimpleContainer container = entry.get(sp.getUUID());
        if (container == null) {
            container = LootRoller.rollForEntity(level, entity, table, ce.getContainerLootTableSeed(), sp, kind.slots);
            entry.put(sp.getUUID(), container);
            store.setDirty();
        }

        sp.openMenu(kind.menuProvider(container));
        return InteractionResult.SUCCESS;
    }
}
