package dev.blockacademy.slashlootr.v1_21_1.core;

import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;

/**
 * Maps a vanilla container type to its display name + menu factory.
 * Used by {@link dev.blockacademy.slashlootr.v1_21_1.handler.ContainerInteractionHandler}
 * and {@link dev.blockacademy.slashlootr.v1_21_1.handler.EntityInteractionHandler}.
 */
public enum ContainerKind {
    CHEST(27, "container.chest", false),
    BARREL(27, "container.barrel", false),
    SHULKER(27, "container.shulkerBox", true),
    DOUBLE_CHEST(54, "container.chestDouble", false),
    MINECART_CHEST(27, "container.minecart", false),
    MINECART_HOPPER(5, "container.hopper", false),
    CHEST_BOAT(27, "container.chestBoat", false);

    public final int slots;
    public final String translationKey;
    public final boolean shulker;

    ContainerKind(int slots, String translationKey, boolean shulker) {
        this.slots = slots;
        this.translationKey = translationKey;
        this.shulker = shulker;
    }

    public MenuProvider menuProvider(Container container) {
        Component title = Component.translatable(translationKey);
        if (shulker) {
            return new SimpleMenuProvider(
                    (id, inv, p) -> new ShulkerBoxMenu(id, inv, container),
                    title);
        }
        if (slots == 54) {
            return new SimpleMenuProvider(
                    (id, inv, p) -> ChestMenu.sixRows(id, inv, container),
                    title);
        }
        if (slots == 5) {
            return new SimpleMenuProvider(
                    (id, inv, p) -> new net.minecraft.world.inventory.HopperMenu(id, inv, container),
                    title);
        }
        return new SimpleMenuProvider(
                (id, inv, p) -> ChestMenu.threeRows(id, inv, container),
                title);
    }
}
