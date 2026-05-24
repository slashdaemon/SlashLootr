package dev.blockacademy.slashlootr.v1_21_1.store;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.SimpleContainer;

import java.util.HashMap;
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

    public ListTag toNbt(HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, SimpleContainer> e : perPlayer.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("uuid", e.getKey());
            entry.putInt("size", e.getValue().getContainerSize());
            entry.put("items", e.getValue().createTag(registries));
            list.add(entry);
        }
        return list;
    }

    public void fromNbt(ListTag list, HolderLookup.Provider registries) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            UUID uuid = entry.getUUID("uuid");
            int size = entry.getInt("size");
            SimpleContainer container = new SimpleContainer(size > 0 ? size : 27);
            container.fromTag(entry.getList("items", Tag.TAG_COMPOUND), registries);
            put(uuid, container);
        }
    }
}
