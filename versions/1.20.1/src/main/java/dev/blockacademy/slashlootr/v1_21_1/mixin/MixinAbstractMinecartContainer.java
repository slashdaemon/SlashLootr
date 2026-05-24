package dev.blockacademy.slashlootr.v1_21_1.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecartContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cancel the vanilla loot roll for chest/hopper minecarts. */
@Mixin(AbstractMinecartContainer.class)
public abstract class MixinAbstractMinecartContainer {

    @Inject(method = "unpackLootTable", at = @At("HEAD"), cancellable = true)
    private void slashlootr$cancelVanillaRoll(Player player, CallbackInfo ci) {
        AbstractMinecartContainer self = (AbstractMinecartContainer) (Object) this;
        if (self.level().isClientSide()) return;
        if (((AccessorAbstractMinecartContainer) (Object) self).slashlootr$getLootTable() == null) return;
        ci.cancel();
    }
}
