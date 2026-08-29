package dev.blockacademy.slashlootr.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.vehicle.ChestBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChestBoat.class)
public interface AccessorChestBoat {
    @Accessor("lootTable") ResourceLocation slashlootr$getLootTable();
    @Accessor("lootTableSeed") long slashlootr$getLootTableSeed();
}
