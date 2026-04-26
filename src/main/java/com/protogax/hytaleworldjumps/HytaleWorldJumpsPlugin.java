package com.protogax.hytaleworldjumps;

import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.DrainPlayerFromWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.protogax.hytaleworldjumps.interaction.ProtectedBreakInteraction;
import com.protogax.hytaleworldjumps.interaction.ReturnInteraction;
import com.protogax.hytaleworldjumps.interaction.WorldJumpInteraction;
import com.protogax.hytaleworldjumps.inventory.InventoryManager;
import com.protogax.hytaleworldjumps.listener.WorldTransitionListener;

import javax.annotation.Nonnull;
import java.util.logging.Level;

public class HytaleWorldJumpsPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private InventoryManager inventoryManager;

    public HytaleWorldJumpsPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        LOGGER.at(Level.INFO).log("[WorldJumps] Setup starting...");

        WorldManager worldManager = new WorldManager();
        inventoryManager = new InventoryManager(getDataDirectory());
        WorldTransitionListener listener = new WorldTransitionListener(worldManager, inventoryManager);

        // Register events
        EventRegistry registry = getEventRegistry();
        registry.registerGlobal(EventPriority.NORMAL, DrainPlayerFromWorldEvent.class, listener::onDrainPlayer);
        registry.registerGlobal(EventPriority.NORMAL, AddPlayerToWorldEvent.class, listener::onAddPlayer);
        registry.registerGlobal(EventPriority.NORMAL, PlayerReadyEvent.class, listener::onPlayerReady);
        registry.registerGlobal(EventPriority.NORMAL, PlayerDisconnectEvent.class, listener::onPlayerDisconnect);

        // Register interactions
        Interaction.CODEC.register("WorldJumpPortal", WorldJumpInteraction.class, WorldJumpInteraction.CODEC);
        Interaction.CODEC.register("ReturnToExploration", ReturnInteraction.class, ReturnInteraction.CODEC);
        Interaction.CODEC.register("ProtectedBreak", ProtectedBreakInteraction.class, ProtectedBreakInteraction.CODEC);

        LOGGER.at(Level.INFO).log("[WorldJumps] Setup complete — events and interactions registered.");
    }

    @Override
    protected void shutdown() {
        if (inventoryManager != null) {
            LOGGER.at(Level.INFO).log("[WorldJumps] Flushing inventories to disk...");
            inventoryManager.saveAll();
            inventoryManager.shutdown();
        }
        LOGGER.at(Level.INFO).log("[WorldJumps] Shutdown complete.");
    }
}
