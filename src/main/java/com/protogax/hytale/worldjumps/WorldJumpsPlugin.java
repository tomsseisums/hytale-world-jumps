package com.protogax.hytale.worldjumps;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.BootEvent;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.DrainPlayerFromWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.events.AddWorldEvent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.protogax.hytale.worldjumps.command.WorldJumpsCommand;
import com.protogax.hytale.worldjumps.interaction.WorldJumpInteraction;
import com.protogax.hytale.worldjumps.marker.PortalMapMarker;
import com.protogax.hytale.worldjumps.marker.PortalMapMarkersResource;
import com.protogax.hytale.worldjumps.system.PortalBreakProtectionSystem;
import com.protogax.hytale.worldjumps.inventory.InventoryManager;
import com.protogax.hytale.worldjumps.listener.WorldTransitionListener;

import javax.annotation.Nonnull;
import java.util.logging.Level;

public class WorldJumpsPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static InventoryManager inventoryManagerInstance;
    private static WorldJumpsConfig configInstance;
    private static WorldManager worldManagerInstance;
    private static ComponentType<ChunkStore, PortalMapMarker> portalMarkerComponentType;
    private static ResourceType<ChunkStore, PortalMapMarkersResource> portalMarkersResourceType;

    private final Config<WorldJumpsConfig> config = this.withConfig("config", WorldJumpsConfig.CODEC);
    private InventoryManager inventoryManager;

    public static InventoryManager getInventoryManager() {
        return inventoryManagerInstance;
    }

    public static WorldJumpsConfig getPluginConfig() {
        return configInstance;
    }

    public static WorldManager getWorldManager() {
        return worldManagerInstance;
    }

    public static ComponentType<ChunkStore, PortalMapMarker> getPortalMarkerComponentType() {
        return portalMarkerComponentType;
    }

    public static ResourceType<ChunkStore, PortalMapMarkersResource> getPortalMarkersResourceType() {
        return portalMarkersResourceType;
    }

    public WorldJumpsPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        LOGGER.at(Level.INFO).log("[WorldJumps] Setup starting...");

        config.save();
        configInstance = config.get();

        WorldManager worldManager = new WorldManager(configInstance);
        worldManagerInstance = worldManager;
        inventoryManager = new InventoryManager(getDataDirectory());
        inventoryManagerInstance = inventoryManager;
        WorldTransitionListener listener = new WorldTransitionListener(worldManager, inventoryManager);

        // Register events
        EventRegistry registry = getEventRegistry();
        registry.register(BootEvent.class, event -> worldManager.applyDisplayNames());
        registry.registerGlobal(EventPriority.NORMAL, DrainPlayerFromWorldEvent.class, listener::onDrainPlayer);
        registry.registerGlobal(EventPriority.NORMAL, AddPlayerToWorldEvent.class, listener::onAddPlayer);
        registry.registerGlobal(EventPriority.NORMAL, PlayerReadyEvent.class, listener::onPlayerReady);
        registry.registerGlobal(EventPriority.NORMAL, PlayerDisconnectEvent.class, listener::onPlayerDisconnect);

        // Register interactions
        Interaction.CODEC.register("WorldJumpPortal", WorldJumpInteraction.class, WorldJumpInteraction.CODEC);

        // Register ECS systems
        ComponentRegistryProxy<EntityStore> entityStoreRegistry = getEntityStoreRegistry();
        entityStoreRegistry.registerSystem(new PortalBreakProtectionSystem());

        // Register portal map-marker chunk-store component, resource, system, and provider
        ComponentRegistryProxy<ChunkStore> chunkStoreRegistry = getChunkStoreRegistry();
        portalMarkersResourceType = chunkStoreRegistry.registerResource(
            PortalMapMarkersResource.class, "WorldJumpsPortalMarkers", PortalMapMarkersResource.CODEC);
        portalMarkerComponentType = chunkStoreRegistry.registerComponent(
            PortalMapMarker.class, "WorldJumpsPortalMarker", PortalMapMarker.CODEC);
        chunkStoreRegistry.registerSystem(new PortalMapMarker.OnAddRemove());
        registry.registerGlobal(AddWorldEvent.class,
            event -> event.getWorld().getWorldMapManager().addMarkerProvider("worldjumps", PortalMapMarker.MarkerProvider.INSTANCE));

        // Register commands
        getCommandRegistry().registerCommand(new WorldJumpsCommand());

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
