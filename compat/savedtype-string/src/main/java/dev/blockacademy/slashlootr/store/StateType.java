package dev.blockacademy.slashlootr.store;

import com.mojang.serialization.Codec;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The one line that differs between MC 1.21.6 - 1.21.11 and MC 26.1+: on this generation the
 * {@code SavedDataType} identity is still a plain String.
 */
final class StateType {
    private StateType() {}

    static SavedDataType<SlashLootrState> create(Codec<SlashLootrState> codec) {
        return new SavedDataType<>("slashlootr", SlashLootrState::new, codec, null);
    }
}
