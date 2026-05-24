package dev.blockacademy.slashlootr.v1_21_1.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.20.1: RandomizableContainer interface doesn't exist — target the BlockEntity class.
 */
@Mixin(RandomizableContainerBlockEntity.class)
public abstract class MixinRandomizableContainer {

    @Inject(method = "unpackLootTable", at = @At("HEAD"), cancellable = true)
    private void slashlootr$cancelVanillaRoll(Player player, CallbackInfo ci) {
        RandomizableContainerBlockEntity self = (RandomizableContainerBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null || level.isClientSide()) return;
        if (((AccessorRandomizableContainerBlockEntity) (Object) self).slashlootr$getLootTable() == null) return;
        ci.cancel();
    }
}
