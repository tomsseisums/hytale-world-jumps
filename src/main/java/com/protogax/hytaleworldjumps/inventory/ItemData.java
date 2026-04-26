package com.protogax.hytaleworldjumps.inventory;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.bson.BsonDocument;

public class ItemData {

    private int slot;
    private String itemId;
    private int quantity;
    private double durability;
    private double maxDurability;
    private String metadata;

    public ItemData() {
    }

    public ItemData(int slot, ItemStack item) {
        this.slot = slot;
        this.itemId = item.getItemId();
        this.quantity = item.getQuantity();
        this.durability = item.getDurability();
        this.maxDurability = item.getMaxDurability();
        BsonDocument meta = item.getMetadata();
        this.metadata = meta != null ? meta.toJson() : null;
    }

    public ItemStack toItemStack() {
        try {
            BsonDocument metaDoc = null;
            if (this.metadata != null && !this.metadata.isBlank()) {
                try {
                    metaDoc = BsonDocument.parse(this.metadata);
                } catch (Exception e) {
                    metaDoc = null;
                }
            }
            return new ItemStack(this.itemId, this.quantity, this.durability, this.maxDurability, metaDoc);
        } catch (Exception e) {
            return null;
        }
    }

    public int getSlot() {
        return slot;
    }

    public void setSlot(int slot) {
        this.slot = slot;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public double getDurability() {
        return durability;
    }

    public void setDurability(double durability) {
        this.durability = durability;
    }

    public double getMaxDurability() {
        return maxDurability;
    }

    public void setMaxDurability(double maxDurability) {
        this.maxDurability = maxDurability;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
}
