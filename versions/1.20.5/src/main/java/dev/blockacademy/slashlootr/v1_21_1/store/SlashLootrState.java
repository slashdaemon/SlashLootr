package dev.blockacademy.slashlootr.v1_21_1.store;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-dimension SavedData holding every player's personal copy of every naturally-generated
 * container they've opened.
 *
 * <p>Two top-level maps: one keyed by packed BlockPos for block-entity containers, one keyed
 * by entity UUID for chest minecarts / chest boats. Inside each is a per-player
 * {@code Map<UUID, SimpleContainer>} of the actual loot.
 *
 * <p>Persisted at {@code world/<dim>/data/slashlootr.dat}.
 */
public class SlashLootrState extends SavedData {
    private static final String STATE_ID = "slashlootr";

    private final Map<Long, PlayerLootEntry> blocks = new HashMap<>();
    private final Map<UUID, PlayerLootEntry> entities = new HashMap<>();

    public static SavedData.Factory<SlashLootrState> factory() {
        return new SavedData.Factory<>(SlashLootrState::new, SlashLootrState::load, null);
    }

    public static SlashLootrState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), STATE_ID);
    }

    public PlayerLootEntry blockEntry(long packedPos) {
        return blocks.computeIfAbsent(packedPos, k -> new PlayerLootEntry(this));
    }

    public PlayerLootEntry entityEntry(UUID uuid) {
        return entities.computeIfAbsent(uuid, k -> new PlayerLootEntry(this));
    }

    public void forgetBlock(long packedPos) {
        if (blocks.remove(packedPos) != null) setDirty();
    }

    public void forgetEntity(UUID uuid) {
        if (entities.remove(uuid) != null) setDirty();
    }

    public void forgetPlayerEverywhere(UUID player) {
        boolean changed = false;
        for (PlayerLootEntry entry : blocks.values()) changed |= entry.removePlayer(player);
        for (PlayerLootEntry entry : entities.values()) changed |= entry.removePlayer(player);
        if (changed) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag blockList = new ListTag();
        for (Map.Entry<Long, PlayerLootEntry> e : blocks.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("pos", e.getKey());
            entry.put("players", e.getValue().toNbt(registries));
            blockList.add(entry);
        }
        tag.put("blocks", blockList);

        ListTag entityList = new ListTag();
        for (Map.Entry<UUID, PlayerLootEntry> e : entities.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("uuid", e.getKey());
            entry.put("players", e.getValue().toNbt(registries));
            entityList.add(entry);
        }
        tag.put("entities", entityList);
        return tag;
    }

    public static SlashLootrState load(CompoundTag tag, HolderLookup.Provider registries) {
        SlashLootrState state = new SlashLootrState();
        ListTag blockList = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blockList.size(); i++) {
            CompoundTag entry = blockList.getCompound(i);
            long pos = entry.getLong("pos");
            PlayerLootEntry players = new PlayerLootEntry(state);
            players.fromNbt(entry.getList("players", Tag.TAG_COMPOUND), registries);
            state.blocks.put(pos, players);
        }
        ListTag entityList = tag.getList("entities", Tag.TAG_COMPOUND);
        for (int i = 0; i < entityList.size(); i++) {
            CompoundTag entry = entityList.getCompound(i);
            UUID uuid = entry.getUUID("uuid");
            PlayerLootEntry players = new PlayerLootEntry(state);
            players.fromNbt(entry.getList("players", Tag.TAG_COMPOUND), registries);
            state.entities.put(uuid, players);
        }
        return state;
    }

    /** Wraps a SimpleContainer so any mutation marks this SavedData dirty. */
    public SimpleContainer wrap(SimpleContainer container) {
        container.addListener(c -> setDirty());
        return container;
    }
}
