package dev.blockacademy.slashlootr.handler;

import dev.blockacademy.slashlootr.core.Handling;
import dev.blockacademy.slashlootr.core.LootContainer;
import dev.blockacademy.slashlootr.core.LootRoller;
import dev.blockacademy.slashlootr.store.PlayerLootEntry;
import dev.blockacademy.slashlootr.store.SlashLootrState;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Band A copy of the container-entity handler. MC 1.20.1 has no shared {@code ContainerEntity}
 * interface, so {@link Handling} branches on {@code AbstractMinecartContainer} vs {@code ChestBoat}
 * and reads the private loot-table fields through {@code @Accessor} mixins.
 *
 * <p>No animation delegation: minecarts and chest boats have no {@code startOpen} override to
 * forward to.
 */
public class EntityInteractionHandler implements UseEntityCallback {

    @Override
    public InteractionResult interact(Player player, Level world, InteractionHand hand, Entity entity,
            EntityHitResult hit) {
        if (world.isClientSide()) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        if (player.isSpectator()) return InteractionResult.PASS;

        ServerLevel level = (ServerLevel) world;
        Handling.Decision decision = Handling.forEntity(level, entity);
        Handling.logEntity(level, entity, decision);
        if (!decision.instanced()) return InteractionResult.PASS;

        // Sneaking on a chest boat is "mount", not "open" — leave it to vanilla.
        if (entity instanceof ChestBoat && player.isSecondaryUseActive()) return InteractionResult.PASS;

        SlashLootrState store = SlashLootrState.get(level);
        PlayerLootEntry entry = store.entityEntry(entity.getUUID());

        LootContainer container = entry.get(sp.getUUID());
        if (container == null) {
            ResourceLocation table = Handling.lootTableOf(entity);
            container = entry.newContainer(decision.slots());
            if (table != null) {
                LootRoller.rollForEntity(level, entity, table, Handling.lootSeedOf(entity), sp, container);
            }
            entry.put(sp.getUUID(), container);
            store.setDirty();
        }

        sp.openMenu(decision.kind().menuProvider(container));
        return InteractionResult.SUCCESS;
    }
}
