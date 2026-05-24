package dev.blockacademy.slashlootr.v1_21_1.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Entity-container equivalent of {@link MixinRandomizableContainer}: cancels the lazy loot
 * roll for chest minecarts, hopper minecarts, and chest boats. Per-player rolling happens in
 * {@link dev.blockacademy.slashlootr.v1_21_1.handler.EntityInteractionHandler}.
 */
@Mixin(ContainerEntity.class)
public interface MixinContainerEntity {

    @Inject(method = "unpackChestVehicleLootTable", at = @At("HEAD"), cancellable = true)
    default void slashlootr$cancelVanillaRoll(Player player, CallbackInfo ci) {
        ContainerEntity self = (ContainerEntity) this;
        if (!(self instanceof Entity entity)) return;
        if (entity.level().isClientSide()) return;
        ResourceKey<LootTable> table = self.getLootTable();
        if (table == null) return;
        ci.cancel();
    }
}
