package dev.blockacademy.slashlootr.mixin;

import dev.blockacademy.slashlootr.core.Handling;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecartContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cancels the vanilla loot roll for chest and hopper minecarts SlashLoot will serve itself. */
@Mixin(AbstractMinecartContainer.class)
public abstract class MixinAbstractMinecartContainer {

    @Inject(method = "unpackLootTable", at = @At("HEAD"), cancellable = true)
    private void slashlootr$cancelVanillaRoll(Player player, CallbackInfo ci) {
        AbstractMinecartContainer self = (AbstractMinecartContainer) (Object) this;
        if (self.level().isClientSide()) return;
        if (Handling.instancesEntity(self.level(), self)) {
            ci.cancel();
        }
    }
}
