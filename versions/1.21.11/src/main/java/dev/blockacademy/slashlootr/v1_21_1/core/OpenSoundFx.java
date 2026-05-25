package dev.blockacademy.slashlootr.v1_21_1.core;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Plays the same open sound vanilla would play, so players still hear the chest "creak."
 * Lid animation is intentionally skipped in v1 (requires touching ContainerOpenersCounter
 * on the underlying BlockEntity which would risk side effects with our cancel-loot mixin).
 */
public final class OpenSoundFx {
    private OpenSoundFx() {}

    public static void playOpen(ServerLevel level, BlockPos pos, ContainerKind kind) {
        SoundEvent sound = switch (kind) {
            case CHEST, DOUBLE_CHEST -> SoundEvents.CHEST_OPEN;
            case BARREL -> SoundEvents.BARREL_OPEN;
            case SHULKER -> SoundEvents.SHULKER_BOX_OPEN;
            default -> null;
        };
        if (sound == null) return;
        float pitch = 0.9F + level.getRandom().nextFloat() * 0.1F;
        level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sound, SoundSource.BLOCKS, 0.5F, pitch);
    }
}
