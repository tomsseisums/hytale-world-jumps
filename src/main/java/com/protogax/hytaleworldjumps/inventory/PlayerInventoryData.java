package com.protogax.hytaleworldjumps.inventory;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.InventoryUtils;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerInventoryData {

    private List<ItemData> items = new ArrayList<>();
    private int activeHotbarSlot = 0;

    public PlayerInventoryData() {
    }

    public static PlayerInventoryData fromPlayer(@Nonnull Player player) {
        PlayerInventoryData data = new PlayerInventoryData();
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return data;
        }

        ComponentAccessor<EntityStore> accessor = ref.getStore();
        data.activeHotbarSlot = player.getInventory().getActiveHotbarSlot();

        CombinedItemContainer combined = InventoryComponent.getCombined(accessor, ref, InventoryComponent.EVERYTHING);
        if (combined == null) {
            return data;
        }

        short capacity = combined.getCapacity();
        for (short i = 0; i < capacity; i++) {
            ItemStack item = combined.getItemStack(i);
            if (item != null && !item.isEmpty()) {
                data.items.add(new ItemData(i, item));
            }
        }

        return data;
    }

    public void applyToPlayer(@Nonnull Player player) {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }

        ComponentAccessor<EntityStore> accessor = ref.getStore();
        CombinedItemContainer combined = InventoryComponent.getCombined(accessor, ref, InventoryComponent.EVERYTHING);
        if (combined == null) {
            return;
        }

        short capacity = combined.getCapacity();
        Map<Integer, ItemStack> slotMap = new HashMap<>();

        for (ItemData itemData : this.items) {
            ItemStack restored = itemData.toItemStack();
            if (restored != null && !restored.isEmpty()) {
                slotMap.put(itemData.getSlot(), restored);
            }
        }

        for (short i = 0; i < capacity; i++) {
            ItemStack item = slotMap.getOrDefault((int) i, ItemStack.EMPTY);
            try {
                combined.setItemStackForSlot(i, item);
            } catch (Exception ignored) {
            }
        }

        try {
            InventoryUtils.setActiveSlot(ref, -1, (byte) this.activeHotbarSlot, accessor);
        } catch (Exception ignored) {
        }
    }

    public List<ItemData> getItems() {
        return items;
    }

    public void setItems(List<ItemData> items) {
        this.items = items;
    }

    public int getActiveHotbarSlot() {
        return activeHotbarSlot;
    }

    public void setActiveHotbarSlot(int activeHotbarSlot) {
        this.activeHotbarSlot = activeHotbarSlot;
    }
}
