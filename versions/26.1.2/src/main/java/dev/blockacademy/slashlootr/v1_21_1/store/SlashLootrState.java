package dev.blockacademy.slashlootr.v1_21_1.store;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-dimension SavedData holding every player's personal copy of every naturally-generated
 * container they've opened. Codec-based serialization for MC 1.21.5+.
 *
 * <p>Persisted at {@code world/<dim>/data/slashlootr.dat}.
 */
public class SlashLootrState extends SavedData {
    private static final Identifier STATE_ID = Identifier.fromNamespaceAndPath("slashlootr", "slashlootr");

    /** SimpleContainer subclass that marks the owning SavedData dirty on any change.
     *  Replaces the addListener pattern, which was removed in MC 26.1. */
    public static class DirtyContainer extends SimpleContainer {
        private final SlashLootrState owner;
        public DirtyContainer(int size, SlashLootrState owner) {
            super(size);
            this.owner = owner;
        }
        @Override
        public void setChanged() {
            super.setChanged();
            owner.setDirty();
        }
    }

    record SlotItem(int slot, ItemStack item) {
        static final Codec<SlotItem> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("slot").forGetter(SlotItem::slot),
                ItemStack.CODEC.fieldOf("item").forGetter(SlotItem::item)
        ).apply(i, SlotItem::new));
    }

    record PlayerSlots(UUID player, int size, List<SlotItem> items) {
        static final Codec<PlayerSlots> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("uuid").forGetter(PlayerSlots::player),
                Codec.INT.fieldOf("size").forGetter(PlayerSlots::size),
                SlotItem.CODEC.listOf().fieldOf("items").forGetter(PlayerSlots::items)
        ).apply(i, PlayerSlots::new));
    }

    private record BlockEntryRec(long pos, List<PlayerSlots> players) {
        static final Codec<BlockEntryRec> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("pos").forGetter(BlockEntryRec::pos),
                PlayerSlots.CODEC.listOf().fieldOf("players").forGetter(BlockEntryRec::players)
        ).apply(i, BlockEntryRec::new));
    }

    private record EntityEntryRec(UUID uuid, List<PlayerSlots> players) {
        static final Codec<EntityEntryRec> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("uuid").forGetter(EntityEntryRec::uuid),
                PlayerSlots.CODEC.listOf().fieldOf("players").forGetter(EntityEntryRec::players)
        ).apply(i, EntityEntryRec::new));
    }

    public static final Codec<SlashLootrState> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockEntryRec.CODEC.listOf().fieldOf("blocks").forGetter(SlashLootrState::serializeBlocks),
            EntityEntryRec.CODEC.listOf().fieldOf("entities").forGetter(SlashLootrState::serializeEntities)
    ).apply(i, SlashLootrState::deserialize));

    public static final SavedDataType<SlashLootrState> TYPE =
            new SavedDataType<>(STATE_ID, SlashLootrState::new, CODEC, null);

    private final Map<Long, PlayerLootEntry> blocks = new HashMap<>();
    private final Map<UUID, PlayerLootEntry> entities = new HashMap<>();

    public SlashLootrState() {}

    public static SlashLootrState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
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

    /** Copy the input container into a DirtyContainer that auto-marks dirty.
     *  Caller usually discards the input; the returned reference is the one to persist. */
    public SimpleContainer wrap(SimpleContainer container) {
        DirtyContainer wrapped = new DirtyContainer(container.getContainerSize(), this);
        for (int i = 0; i < container.getContainerSize(); i++) {
            wrapped.setItem(i, container.getItem(i));
        }
        return wrapped;
    }

    private static SlashLootrState deserialize(List<BlockEntryRec> blocks, List<EntityEntryRec> entities) {
        SlashLootrState state = new SlashLootrState();
        for (BlockEntryRec e : blocks) {
            PlayerLootEntry pe = state.blocks.computeIfAbsent(e.pos(), k -> new PlayerLootEntry(state));
            for (PlayerSlots ps : e.players()) {
                pe.put(ps.player(), inflate(ps));
            }
        }
        for (EntityEntryRec e : entities) {
            PlayerLootEntry pe = state.entities.computeIfAbsent(e.uuid(), k -> new PlayerLootEntry(state));
            for (PlayerSlots ps : e.players()) {
                pe.put(ps.player(), inflate(ps));
            }
        }
        return state;
    }

    private static SimpleContainer inflate(PlayerSlots ps) {
        SimpleContainer c = new SimpleContainer(Math.max(1, ps.size()));
        for (SlotItem item : ps.items()) {
            if (item.slot() >= 0 && item.slot() < c.getContainerSize()) c.setItem(item.slot(), item.item());
        }
        return c;
    }

    private List<BlockEntryRec> serializeBlocks() {
        List<BlockEntryRec> out = new ArrayList<>();
        for (Map.Entry<Long, PlayerLootEntry> e : blocks.entrySet()) {
            out.add(new BlockEntryRec(e.getKey(), e.getValue().toPlayerSlotsList()));
        }
        return out;
    }

    private List<EntityEntryRec> serializeEntities() {
        List<EntityEntryRec> out = new ArrayList<>();
        for (Map.Entry<UUID, PlayerLootEntry> e : entities.entrySet()) {
            out.add(new EntityEntryRec(e.getKey(), e.getValue().toPlayerSlotsList()));
        }
        return out;
    }

    static PlayerSlots toPlayerSlots(UUID uuid, SimpleContainer c) {
        List<SlotItem> items = new ArrayList<>();
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (!s.isEmpty()) items.add(new SlotItem(i, s.copy()));
        }
        return new PlayerSlots(uuid, c.getContainerSize(), items);
    }
}
