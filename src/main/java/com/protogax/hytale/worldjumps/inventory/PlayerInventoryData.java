package com.protogax.hytale.worldjumps.inventory;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.packets.inventory.SetActiveSlot;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class PlayerInventoryData {

    @Nonnull
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private List<ItemData> items = new ArrayList<>();
    private int activeHotbarSlot = 0;

    public PlayerInventoryData() {
    }

    public static PlayerInventoryData fromPlayer(@Nonnull Player player) {
        PlayerInventoryData data = new PlayerInventoryData();
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            LOGGER.at(Level.WARNING).log("[WorldJumps] SNAPSHOT: player ref is null/invalid — returning empty snapshot");
            return data;
        }

        ComponentAccessor<EntityStore> accessor = ref.getStore();
        data.activeHotbarSlot = player.getInventory().getActiveHotbarSlot();

        CombinedItemContainer combined = InventoryComponent.getCombined(accessor, ref, InventoryComponent.EVERYTHING);
        if (combined == null) {
            LOGGER.at(Level.WARNING).log("[WorldJumps] SNAPSHOT: getCombined returned null — returning empty snapshot");
            return data;
        }

        short capacity = combined.getCapacity();
        for (short i = 0; i < capacity; i++) {
            ItemStack item = combined.getItemStack(i);
            if (item != null && !item.isEmpty()) {
                data.items.add(new ItemData(i, item));
            }
        }
        LOGGER.at(Level.FINE).log("[WorldJumps] SNAPSHOT: captured %d items, activeHotbar=%d", data.items.size(), data.activeHotbarSlot);

        return data;
    }

    public void applyToPlayer(@Nonnull Player player) {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            LOGGER.at(Level.WARNING).log("[WorldJumps] RESTORE: player ref is null/invalid — aborting restore");
            return;
        }

        ComponentAccessor<EntityStore> accessor = ref.getStore();
        CombinedItemContainer combined = InventoryComponent.getCombined(accessor, ref, InventoryComponent.EVERYTHING);
        if (combined == null) {
            LOGGER.at(Level.WARNING).log("[WorldJumps] RESTORE: getCombined returned null — aborting restore");
            return;
        }

        short capacity = combined.getCapacity();
        Map<Integer, ItemStack> slotMap = new HashMap<>();

        for (ItemData itemData : this.items) {
            ItemStack restored = itemData.toItemStack();
            if (restored != null && !restored.isEmpty()) {
                slotMap.put(itemData.getSlot(), restored);
            } else {
                LOGGER.at(Level.WARNING).log("[WorldJumps] RESTORE: slot %d toItemStack failed (itemId=%s)", itemData.getSlot(), itemData.getItemId());
            }
        }

        for (short i = 0; i < capacity; i++) {
            ItemStack item = slotMap.getOrDefault((int) i, ItemStack.EMPTY);
            try {
                combined.setItemStackForSlot(i, item);
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).log("[WorldJumps] RESTORE: setItemStackForSlot(%d) threw: %s", i, e.getMessage());
            }
        }

        try {
            InventoryComponent.Hotbar hotbar = accessor.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
            if (hotbar != null) {
                hotbar.setActiveSlot((byte) this.activeHotbarSlot, ref, accessor);
            }
            PlayerRef playerRef = accessor.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef != null) {
                playerRef.getPacketHandler().writeNoCache(new SetActiveSlot(-1, this.activeHotbarSlot));
            }
        } catch (Exception ignored) {
        }
        LOGGER.at(Level.FINE).log("[WorldJumps] RESTORE: applied %d items across %d capacity, activeSlot=%d", slotMap.size(), capacity, this.activeHotbarSlot);
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
