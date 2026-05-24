package dev.blockacademy.slashlootr.v1_21_1.store;

import net.minecraft.world.SimpleContainer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Per-container map of player UUID → personal SimpleContainer. */
public class PlayerLootEntry {
    private final SlashLootrState owner;
    private final Map<UUID, SimpleContainer> perPlayer = new HashMap<>();

    public PlayerLootEntry(SlashLootrState owner) {
        this.owner = owner;
    }

    public SimpleContainer get(UUID player) {
        return perPlayer.get(player);
    }

    public SimpleContainer put(UUID player, SimpleContainer container) {
        owner.wrap(container);
        perPlayer.put(player, container);
        return container;
    }

    public boolean removePlayer(UUID player) {
        return perPlayer.remove(player) != null;
    }

    List<SlashLootrState.PlayerSlots> toPlayerSlotsList() {
        List<SlashLootrState.PlayerSlots> out = new ArrayList<>();
        for (Map.Entry<UUID, SimpleContainer> e : perPlayer.entrySet()) {
            out.add(SlashLootrState.toPlayerSlots(e.getKey(), e.getValue()));
        }
        return out;
    }
}
