package dev.blockacademy.slashlootr.v1_21_1.handler;

import dev.blockacademy.slashlootr.v1_21_1.config.SlashLootrConfig;
import dev.blockacademy.slashlootr.v1_21_1.core.ContainerKind;
import dev.blockacademy.slashlootr.v1_21_1.core.LootRoller;
import dev.blockacademy.slashlootr.v1_21_1.core.OpenSoundFx;
import dev.blockacademy.slashlootr.v1_21_1.mixin.AccessorRandomizableContainerBlockEntity;
import dev.blockacademy.slashlootr.v1_21_1.store.PlayerLootEntry;
import dev.blockacademy.slashlootr.v1_21_1.store.SlashLootrState;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;

/** 1.20.1: target the RandomizableContainerBlockEntity class (interface doesn't exist yet). */
public class ContainerInteractionHandler implements UseBlockCallback {

    @Override
    public InteractionResult interact(Player player, Level world, InteractionHand hand, BlockHitResult hit) {
        if (world.isClientSide()) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        if (player.isSpectator()) return InteractionResult.PASS;
        if (player.isShiftKeyDown() && !player.getMainHandItem().isEmpty()) return InteractionResult.PASS;

        ServerLevel level = (ServerLevel) world;
        if (SlashLootrConfig.get().isDimensionBlocked(level.dimension().location())) return InteractionResult.PASS;

        BlockPos pos = hit.getBlockPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof RandomizableContainerBlockEntity rc)) return InteractionResult.PASS;
        ResourceLocation table = ((AccessorRandomizableContainerBlockEntity) (Object) rc).slashlootr$getLootTable();
        if (table == null) return InteractionResult.PASS;
        if (SlashLootrConfig.get().isLootTableBlocked(table)) return InteractionResult.PASS;

        BlockState state = level.getBlockState(pos);

        if (be instanceof ShulkerBoxBlockEntity sbe) {
            if (!sbe.canOpen(sp)) return InteractionResult.PASS;
        }
        if (be instanceof ChestBlockEntity) {
            if (ChestBlock.isChestBlockedAt(level, pos)) return InteractionResult.PASS;
        }

        ContainerKind kind = classify(state, be);
        if (kind == null) return InteractionResult.PASS;

        Container backing = buildPerPlayerContainer(level, pos, state, rc, table, sp, kind);
        if (backing == null) return InteractionResult.PASS;

        sp.openMenu(kind.menuProvider(backing));
        if (SlashLootrConfig.get().playOpenCloseSounds) {
            OpenSoundFx.playOpen(level, pos, kind);
        }
        return InteractionResult.SUCCESS;
    }

    private static ContainerKind classify(BlockState state, BlockEntity be) {
        if (be instanceof ShulkerBoxBlockEntity) return ContainerKind.SHULKER;
        if (be instanceof BarrelBlockEntity) return ContainerKind.BARREL;
        if (be instanceof ChestBlockEntity) {
            if (state.getBlock() instanceof ChestBlock) {
                ChestType type = state.getValue(ChestBlock.TYPE);
                if (type != ChestType.SINGLE) return ContainerKind.DOUBLE_CHEST;
            }
            return ContainerKind.CHEST;
        }
        return null;
    }

    private static Container buildPerPlayerContainer(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            RandomizableContainerBlockEntity rc,
            ResourceLocation table,
            ServerPlayer sp,
            ContainerKind kind) {

        SlashLootrState store = SlashLootrState.get(level);

        if (kind != ContainerKind.DOUBLE_CHEST) {
            return getOrRoll(store, level, pos, rc, table, sp, 27);
        }

        Direction connected = ChestBlock.getConnectedDirection(state);
        BlockPos otherPos = pos.relative(connected);
        BlockEntity otherBe = level.getBlockEntity(otherPos);
        if (!(otherBe instanceof RandomizableContainerBlockEntity otherRc) || !(otherBe instanceof ChestBlockEntity)) {
            return getOrRoll(store, level, pos, rc, table, sp, 27);
        }
        ResourceLocation otherTable = ((AccessorRandomizableContainerBlockEntity) (Object) otherRc).slashlootr$getLootTable();

        BlockPos first, second;
        RandomizableContainerBlockEntity firstRc, secondRc;
        ResourceLocation firstTable, secondTable;
        if (pos.asLong() < otherPos.asLong()) {
            first = pos; second = otherPos;
            firstRc = rc; secondRc = otherRc;
            firstTable = table; secondTable = otherTable;
        } else {
            first = otherPos; second = pos;
            firstRc = otherRc; secondRc = rc;
            firstTable = otherTable; secondTable = table;
        }

        SimpleContainer firstC = firstTable == null
                ? store.wrap(new SimpleContainer(27))
                : getOrRoll(store, level, first, firstRc, firstTable, sp, 27);
        SimpleContainer secondC = secondTable == null
                ? store.wrap(new SimpleContainer(27))
                : getOrRoll(store, level, second, secondRc, secondTable, sp, 27);
        return new CompoundContainer(firstC, secondC);
    }

    private static SimpleContainer getOrRoll(
            SlashLootrState store,
            ServerLevel level,
            BlockPos pos,
            RandomizableContainerBlockEntity rc,
            ResourceLocation table,
            ServerPlayer sp,
            int slots) {
        PlayerLootEntry entry = store.blockEntry(pos.asLong());
        SimpleContainer existing = entry.get(sp.getUUID());
        if (existing != null) return existing;
        long seed = ((AccessorRandomizableContainerBlockEntity) (Object) rc).slashlootr$getLootTableSeed();
        SimpleContainer fresh = LootRoller.rollForBlock(level, pos, table, seed, sp, slots);
        entry.put(sp.getUUID(), fresh);
        store.setDirty();
        return fresh;
    }
}
