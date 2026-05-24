package dev.blockacademy.slashlootr.v1_21_1.handler;

import dev.blockacademy.slashlootr.v1_21_1.config.SlashLootrConfig;
import dev.blockacademy.slashlootr.v1_21_1.core.ContainerKind;
import dev.blockacademy.slashlootr.v1_21_1.core.LootRoller;
import dev.blockacademy.slashlootr.v1_21_1.mixin.AccessorAbstractMinecartContainer;
import dev.blockacademy.slashlootr.v1_21_1.mixin.AccessorChestBoat;
import dev.blockacademy.slashlootr.v1_21_1.store.PlayerLootEntry;
import dev.blockacademy.slashlootr.v1_21_1.store.SlashLootrState;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecartContainer;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.entity.vehicle.MinecartHopper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

/**
 * 1.20.1: no shared ContainerEntity interface — branch on AbstractMinecartContainer vs ChestBoat.
 * Loot-table accessors via mixin Accessors (fields are private).
 */
public class EntityInteractionHandler implements UseEntityCallback {

    @Override
    public InteractionResult interact(Player player, Level world, InteractionHand hand, Entity entity, EntityHitResult hit) {
        if (world.isClientSide()) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        if (player.isSpectator()) return InteractionResult.PASS;

        ResourceLocation table;
        long seed;
        ContainerKind kind;

        if (entity instanceof AbstractMinecartContainer mc) {
            table = ((AccessorAbstractMinecartContainer) (Object) mc).slashlootr$getLootTable();
            seed = ((AccessorAbstractMinecartContainer) (Object) mc).slashlootr$getLootTableSeed();
            if (entity instanceof MinecartHopper) kind = ContainerKind.MINECART_HOPPER;
            else if (entity instanceof MinecartChest) kind = ContainerKind.MINECART_CHEST;
            else return InteractionResult.PASS;
        } else if (entity instanceof ChestBoat boat) {
            if (player.isSecondaryUseActive()) return InteractionResult.PASS;
            table = ((AccessorChestBoat) (Object) boat).slashlootr$getLootTable();
            seed = ((AccessorChestBoat) (Object) boat).slashlootr$getLootTableSeed();
            kind = ContainerKind.CHEST_BOAT;
        } else {
            return InteractionResult.PASS;
        }

        if (table == null) return InteractionResult.PASS;

        ServerLevel level = (ServerLevel) world;
        if (SlashLootrConfig.get().isDimensionBlocked(level.dimension().location())) return InteractionResult.PASS;
        if (SlashLootrConfig.get().isLootTableBlocked(table)) return InteractionResult.PASS;

        SlashLootrState store = SlashLootrState.get(level);
        PlayerLootEntry entry = store.entityEntry(entity.getUUID());
        SimpleContainer container = entry.get(sp.getUUID());
        if (container == null) {
            container = LootRoller.rollForEntity(level, entity, table, seed, sp, kind.slots);
            entry.put(sp.getUUID(), container);
            store.setDirty();
        }

        sp.openMenu(kind.menuProvider(container));
        return InteractionResult.SUCCESS;
    }
}
