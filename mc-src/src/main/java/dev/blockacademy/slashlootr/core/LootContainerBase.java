package dev.blockacademy.slashlootr.core;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;

/**
 * A player's personal copy of a naturally-generated container's loot: everything except the
 * open/close overrides, which is all {@code compat/open-*} has to supply.
 *
 * <p>Two jobs beyond plain {@link SimpleContainer}:
 *
 * <ul>
 *   <li><b>Dirty tracking.</b> {@link #setChanged()} marks the owning SavedData dirty. This replaces
 *       the old {@code addListener} approach, which stopped existing in MC 26.1 — overriding
 *       {@code setChanged} works identically on every band we ship.
 *   <li><b>Animation delegation.</b> Subclasses forward open/close to the real world container, so
 *       vanilla's {@code ContainerOpenersCounter} runs: chest and shulker lids animate, barrels flip
 *       their {@code open} blockstate, and trapped chests emit redstone again. Vanilla's
 *       {@code CompoundContainer} forwards both calls to each half, so double chests work for free.
 * </ul>
 *
 * <p>The delegate is set immediately before the menu opens and cleared on close, so a stale
 * BlockEntity reference is never held across a chunk unload.
 *
 * <p>The subclass exists only because MC 1.21.9 changed the open/close parameter from {@code Player}
 * to {@code ContainerUser}. Nothing else about this class varies by band.
 */
public abstract class LootContainerBase extends SimpleContainer {

    private final Runnable onDirty;
    private Container delegate;

    protected LootContainerBase(int size, Runnable onDirty) {
        super(size);
        this.onDirty = onDirty;
    }

    /** Point this container's open/close animation at the real world container for one session. */
    public void delegateTo(Container worldContainer) {
        this.delegate = worldContainer;
    }

    protected Container delegate() {
        return delegate;
    }

    /** Reads and clears the delegate — used on close so no world reference outlives the session. */
    protected Container takeDelegate() {
        Container d = this.delegate;
        this.delegate = null;
        return d;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (onDirty != null) onDirty.run();
    }
}
