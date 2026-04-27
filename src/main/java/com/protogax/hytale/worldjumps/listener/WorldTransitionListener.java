package com.protogax.hytale.worldjumps.listener;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.DrainPlayerFromWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.protogax.hytale.worldjumps.WorldManager;
import com.protogax.hytale.worldjumps.inventory.InventoryManager;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.logging.Level;

public class WorldTransitionListener {

    @Nonnull
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final WorldManager worldManager;
    private final InventoryManager inventoryManager;

    public WorldTransitionListener(@Nonnull WorldManager worldManager, @Nonnull InventoryManager inventoryManager) {
        this.worldManager = worldManager;
        this.inventoryManager = inventoryManager;
    }

    public void onDrainPlayer(@Nonnull DrainPlayerFromWorldEvent event) {
        try {
            Holder<EntityStore> holder = event.getHolder();
            World world = event.getWorld();

            UUIDComponent uuidComp = holder.getComponent(UUIDComponent.getComponentType());
            if (uuidComp == null) {
                return;
            }

            UUID playerId = uuidComp.getUuid();
            Player player = holder.getComponent(Player.getComponentType());
            if (player == null) {
                return;
            }

            String worldName = world.getName();
            LOGGER.at(Level.FINE).log("[WorldJumps] DRAIN: player=%s leaving world='%s'", playerId, worldName);
            inventoryManager.saveInventory(player, playerId, worldName);
        } catch (Exception e) {
            LOGGER.at(Level.SEVERE).log("[WorldJumps] ERROR in onDrainPlayer: %s", e.getMessage());
        }
    }

    public void onAddPlayer(@Nonnull AddPlayerToWorldEvent event) {
        try {
            Holder<EntityStore> holder = event.getHolder();
            if (holder == null) {
                return;
            }

            UUIDComponent uuidComp = holder.getComponent(UUIDComponent.getComponentType());
            if (uuidComp == null) {
                return;
            }

            UUID playerId = uuidComp.getUuid();
            World world = event.getWorld();
            String worldName = world.getName();

            if (worldName == null || worldName.isBlank()) {
                return;
            }

            LOGGER.at(Level.FINE).log("[WorldJumps] ADD: player=%s entering world='%s'", playerId, worldName);
            inventoryManager.setPendingLoad(playerId, worldName);
        } catch (Exception e) {
            LOGGER.at(Level.SEVERE).log("[WorldJumps] ERROR in onAddPlayer: %s", e.getMessage());
        }
    }

    public void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        try {
            Ref<EntityStore> entityRef = event.getPlayerRef();
            if (entityRef == null) {
                return;
            }

            UUIDComponent uuidComp = entityRef.getStore().getComponent(entityRef, UUIDComponent.getComponentType());
            if (uuidComp == null) {
                return;
            }

            UUID playerId = uuidComp.getUuid();
            Player player = event.getPlayer();
            if (player == null) {
                return;
            }

            LOGGER.at(Level.FINE).log("[WorldJumps] READY: player=%s — applying pending inventory load", playerId);
            inventoryManager.applyPendingLoad(player, playerId);

            // Determine world name from the entity store's world
            World world = entityRef.getStore().getExternalData().getWorld();
            String worldName = world != null ? world.getName() : null;

            if (worldManager.isCreativeWorld(worldName)) {
                Player.setGameMode(entityRef, GameMode.Creative, entityRef.getStore());
                LOGGER.at(Level.FINE).log("[WorldJumps] READY: set Creative mode for player=%s in '%s'", playerId, worldName);
            } else {
                Player.setGameMode(entityRef, GameMode.Adventure, entityRef.getStore());
                LOGGER.at(Level.FINE).log("[WorldJumps] READY: set Adventure mode for player=%s in '%s'", playerId, worldName);
            }
        } catch (Exception e) {
            LOGGER.at(Level.SEVERE).log("[WorldJumps] ERROR in onPlayerReady: %s", e.getMessage());
        }
    }

    public void onPlayerDisconnect(@Nonnull PlayerDisconnectEvent event) {
        try {
            PlayerRef playerRef = event.getPlayerRef();
            if (playerRef == null) {
                return;
            }

            UUID playerId = playerRef.getUuid();
            LOGGER.at(Level.INFO).log("[WorldJumps] Player disconnected: %s", playerRef.getUsername());
            inventoryManager.evict(playerId);
        } catch (Exception e) {
            LOGGER.at(Level.SEVERE).log("[WorldJumps] ERROR in onPlayerDisconnect: %s", e.getMessage());
        }
    }
}
