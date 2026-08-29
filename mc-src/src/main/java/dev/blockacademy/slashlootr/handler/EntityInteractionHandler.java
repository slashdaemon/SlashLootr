package dev.blockacademy.slashlootr.handler;

import dev.blockacademy.slashlootr.compat.Vehicles;
import dev.blockacademy.slashlootr.core.Handling;
import dev.blockacademy.slashlootr.core.LootContainer;
import dev.blockacademy.slashlootr.core.LootRoller;
import dev.blockacademy.slashlootr.store.PlayerLootEntry;
import dev.blockacademy.slashlootr.store.SlashLootrState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Chest minecart / hopper minecart / chest boat equivalent of {@link ContainerInteractionHandler}.
 * Same decision function, different identity (entity UUID instead of block position).
 *
 * <p>No animation delegation here: {@code AbstractMinecartContainer} has no {@code startOpen}
 * override, so there is nothing to forward to.
 */
public final class EntityInteractionHandler {

    private EntityInteractionHandler() {}

    public static InteractionResult interact(
            ServerPlayer player, ServerLevel level, InteractionHand hand, Entity entity) {

        if (player.isSpectator()) return InteractionResult.PASS;

        Handling.Decision decision = Handling.forEntity(level, entity);
        Handling.logEntity(level, entity, decision);
        if (!decision.instanced()) return InteractionResult.PASS;

        // Sneaking on a chest boat is "mount", not "open" — leave it to vanilla.
        if (Vehicles.isChestBoat(entity) && player.isSecondaryUseActive()) return InteractionResult.PASS;

        ContainerEntity ce = (ContainerEntity) entity;
        SlashLootrState store = SlashLootrState.get(level);
        PlayerLootEntry entry = store.entityEntry(entity.getUUID());

        LootContainer container = entry.get(player.getUUID());
        if (container == null) {
            ResourceKey<LootTable> table = Vehicles.lootTable(ce);
            container = entry.newContainer(decision.slots());
            if (table != null) {
                LootRoller.rollForEntity(level, entity, table, Vehicles.lootSeed(ce), player, container);
            }
            entry.put(player.getUUID(), container);
            store.setDirty();
        }

        player.openMenu(decision.kind().menuProvider(container));
        return InteractionResult.SUCCESS;
    }
}
