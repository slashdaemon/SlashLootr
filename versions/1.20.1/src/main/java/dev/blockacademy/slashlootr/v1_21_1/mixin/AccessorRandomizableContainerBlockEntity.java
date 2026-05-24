package dev.blockacademy.slashlootr.v1_21_1.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RandomizableContainerBlockEntity.class)
public interface AccessorRandomizableContainerBlockEntity {
    @Accessor("lootTable") ResourceLocation slashlootr$getLootTable();
    @Accessor("lootTableSeed") long slashlootr$getLootTableSeed();
}
