package dev.blockacademy.slashlootr.store;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The one line that differs between MC 1.21.6 - 1.21.11 and MC 26.1+: from 26.1 the
 * {@code SavedDataType} identity is an {@code Identifier}. The resulting file name is unchanged
 * ({@code slashlootr.dat}), so saves carry over.
 */
final class StateType {
    private StateType() {}

    static SavedDataType<SlashLootrState> create(Codec<SlashLootrState> codec) {
        return new SavedDataType<>(
                Identifier.fromNamespaceAndPath("slashlootr", "slashlootr"),
                SlashLootrState::new, codec, null);
    }
}
