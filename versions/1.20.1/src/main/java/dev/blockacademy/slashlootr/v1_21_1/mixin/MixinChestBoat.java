package dev.blockacademy.slashlootr.v1_21_1.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.ChestBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cancel the vanilla loot roll for chest boats. */
@Mixin(ChestBoat.class)
public abstract class MixinChestBoat {

    @Inject(method = "unpackLootTable", at = @At("HEAD"), cancellable = true)
    private void slashlootr$cancelVanillaRoll(Player player, CallbackInfo ci) {
        ChestBoat self = (ChestBoat) (Object) this;
        if (self.level().isClientSide()) return;
        if (((AccessorChestBoat) (Object) self).slashlootr$getLootTable() == null) return;
        ci.cancel();
    }
}
