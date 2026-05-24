package dev.blockacademy.slashlootr.v1_21_1.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels vanilla's "lazy bake the loot table into the container on first read" pass for
 * naturally-generated containers on server levels. Result: the BlockEntity's LootTable and
 * LootTableSeed NBT tags persist forever; the container stays "unrolled" from the world's
 * perspective. The actual per-player loot is rolled at menu-open time by
 * {@link dev.blockacademy.slashlootr.v1_21_1.handler.ContainerInteractionHandler}.
 */
@Mixin(RandomizableContainer.class)
public interface MixinRandomizableContainer {

    @Inject(method = "unpackLootTable", at = @At("HEAD"), cancellable = true)
    default void slashlootr$cancelVanillaRoll(Player player, CallbackInfo ci) {
        RandomizableContainer self = (RandomizableContainer) this;
        if (!(self instanceof BlockEntity be)) return;
        Level level = be.getLevel();
        if (level == null || level.isClientSide()) return;
        ResourceKey<LootTable> table = self.getLootTable();
        if (table == null) return;
        ci.cancel();
    }
}
