package com.duongess.lodestone.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.entity.player.Inventory;

public interface InventoryChangedCallback {
    Event<InventoryChangedCallback> EVENT = EventFactory.createArrayBacked(InventoryChangedCallback.class,
        (listeners) -> (inventory) -> {
            for (InventoryChangedCallback listener : listeners) {
                listener.onInventoryChanged(inventory);
            }
        });

    void onInventoryChanged(Inventory inventory);
}
