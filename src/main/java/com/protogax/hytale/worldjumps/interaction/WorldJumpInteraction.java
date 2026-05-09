package com.protogax.hytale.worldjumps.interaction;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerWorldData;
import com.hypixel.hytale.server.core.modules.entity.teleport.PendingTeleport;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.spawn.ISpawnProvider;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.worldgen.provider.FlatWorldGenProvider;
import com.hypixel.hytale.protocol.Color;

import java.nio.file.Path;

import com.protogax.hytale.worldjumps.WorldJumpsPlugin;
import com.protogax.hytale.worldjumps.WorldJumpsConfig;
import com.protogax.hytale.worldjumps.WorldManager;
import com.protogax.hytale.worldjumps.inventory.InventoryManager;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class WorldJumpInteraction extends SimpleInstantInteraction {

    @Nonnull
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Nonnull
    public static final BuilderCodec<WorldJumpInteraction> CODEC = BuilderCodec.builder(
            WorldJumpInteraction.class, WorldJumpInteraction::new, SimpleInstantInteraction.CODEC
        )
        .documentation("Teleports the Player to a world. TargetWorld can be 'HytaleCreative' or 'FlatCreative'. When omitted, teleports to the default world.")
        .<String>appendInherited(
            new KeyedCodec<>("TargetWorld", Codec.STRING),
            (o, i) -> o.targetWorld = i, o -> o.targetWorld, (o, p) -> o.targetWorld = p.targetWorld
        )
        .documentation("The target world key: 'HytaleCreative' or 'FlatCreative'. When omitted, teleports to the default (exploration) world.")
        .add()
        .build();

    private String targetWorld;

    public WorldJumpInteraction() {
    }

    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        assert commandBuffer != null;

        Ref<EntityStore> ref = context.getEntity();
        Player playerComponent = commandBuffer.getComponent(ref, Player.getComponentType());
        if (playerComponent == null || playerComponent.isWaitingForClientReady()) {
            return;
        }

        Archetype<EntityStore> archetype = commandBuffer.getArchetype(ref);
        if (archetype.contains(Teleport.getComponentType()) || archetype.contains(PendingTeleport.getComponentType())) {
            return;
        }

        World currentWorld = commandBuffer.getExternalData().getWorld();
        Universe universe = Universe.get();

        // Save current inventory before teleporting
        UUIDComponent uuidComp = commandBuffer.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComp != null) {
            InventoryManager invManager = WorldJumpsPlugin.getInventoryManager();
            if (invManager != null) {
                String currentWorldName = currentWorld != null ? currentWorld.getName() : null;
                if (currentWorldName != null) {
                    LOGGER.at(Level.FINE).log("[WorldJumps] PRE-TELEPORT: saving inventory for player=%s in world='%s'", uuidComp.getUuid(), currentWorldName);
                    invManager.saveInventory(playerComponent, uuidComp.getUuid(), currentWorldName);
                }
            }
        }

        // Resolve TargetWorld key to actual world name; null/empty treated as Default
        String effectiveTarget = (this.targetWorld == null || this.targetWorld.isEmpty()) ? "Default" : this.targetWorld;
        String resolvedWorldName = WorldJumpsConfig.resolveWorldName(effectiveTarget);
        if (resolvedWorldName == null) {
            LOGGER.at(Level.SEVERE).log("[WorldJumps] Unknown TargetWorld '%s' — must be 'Default', 'HytaleCreative', or 'FlatCreative'", effectiveTarget);
            return;
        }

        // Default target uses the universe's default world directly
        if ("Default".equals(effectiveTarget)) {
            World defaultWorld = universe.getDefaultWorld();
            if (defaultWorld == null) {
                LOGGER.at(Level.SEVERE).log("Cannot teleport player — no default world available");
                return;
            }
            teleportToLoadedWorld(ref, commandBuffer, defaultWorld, playerComponent);
            return;
        }

        // Determine world gen type from the target
        String genType = "HytaleCreative".equals(this.targetWorld) ? "Hytale" : "Flat";

        World existingWorld = universe.getWorld(resolvedWorldName);

        if (existingWorld != null) {
            teleportToLoadedWorld(ref, commandBuffer, existingWorld, playerComponent);
        } else {
            CompletableFuture<World> worldFuture;
            if (universe.isWorldLoadable(resolvedWorldName)) {
                worldFuture = universe.loadWorld(resolvedWorldName);
            } else {
                WorldConfig config = new WorldConfig();
                WorldJumpsConfig pluginConfig = WorldJumpsPlugin.getPluginConfig();
                String displayName = pluginConfig != null ? pluginConfig.getDisplayName(resolvedWorldName) : null;
                config.setDisplayName(displayName != null ? displayName : WorldConfig.formatDisplayName(resolvedWorldName));
                config.setGameMode(com.hypixel.hytale.protocol.GameMode.Creative);

                if ("Flat".equals(genType)) {
                    String env = "Env_Zone1_Plains";
                    config.setWorldGenProvider(new FlatWorldGenProvider(
                        new Color((byte) 87, (byte) -118, (byte) 36),
                        new FlatWorldGenProvider.Layer[]{
                            new FlatWorldGenProvider.Layer(0, 1, env, "Rock_Bedrock"),
                            new FlatWorldGenProvider.Layer(1, 70, env, "Rock_Stone"),
                            new FlatWorldGenProvider.Layer(70, 78, env, "Soil_Dirt"),
                            new FlatWorldGenProvider.Layer(78, 79, env, "Soil_Grass"),
                            new FlatWorldGenProvider.Layer(79, 320, env, "Empty")
                        }
                    ));
                } else {
                    var providerCodec = com.hypixel.hytale.server.core.universe.world.worldgen.provider.IWorldGenProvider.CODEC.getCodecFor(genType);
                    if (providerCodec != null) {
                        config.setWorldGenProvider(providerCodec.getDefaultValue());
                    }
                    // Seed Hytale Creative from the default world so its terrain matches.
                    World defaultWorld = universe.getDefaultWorld();
                    if (defaultWorld != null) {
                        long defaultSeed = defaultWorld.getWorldConfig().getSeed();
                        config.setSeed(defaultSeed);
                        LOGGER.at(Level.INFO).log("[WorldJumps] Seeding new world '%s' from default world seed %d", resolvedWorldName, defaultSeed);
                    } else {
                        LOGGER.at(Level.WARNING).log("[WorldJumps] Default world not available — '%s' will use a random seed", resolvedWorldName);
                    }
                }

                Path savePath = universe.validateWorldPath(resolvedWorldName);
                worldFuture = universe.makeWorld(resolvedWorldName, savePath, config);
            }

            // After world loads, apply display name and teleport
            PlayerRef playerRefComponent = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
            UUIDComponent uuidComponent = commandBuffer.getComponent(ref, UUIDComponent.getComponentType());
            if (playerRefComponent == null || uuidComponent == null) {
                return;
            }
            UUID playerUUID = uuidComponent.getUuid();

            worldFuture.orTimeout(1L, TimeUnit.MINUTES).thenAcceptAsync(world -> {
                // Apply display name on load (in case it was loaded from disk without one)
                WorldManager worldManager = WorldJumpsPlugin.getWorldManager();
                if (worldManager != null) {
                    worldManager.applyDisplayName(world);
                }

                Ref<EntityStore> entityRef = playerRefComponent.getReference();
                if (entityRef == null || !entityRef.isValid()) {
                    return;
                }
                ComponentAccessor<EntityStore> accessor = entityRef.getStore();
                Archetype<EntityStore> currentArchetype = accessor.getArchetype(entityRef);
                if (currentArchetype.contains(Teleport.getComponentType()) || currentArchetype.contains(PendingTeleport.getComponentType())) {
                    return;
                }

                Map<String, PlayerWorldData> perWorldData = playerComponent.getPlayerConfigData().getPerWorldData();
                PlayerWorldData worldData = perWorldData.get(world.getName());
                Transform spawnPoint;
                if (worldData != null && worldData.getLastPosition() != null) {
                    spawnPoint = worldData.getLastPosition();
                } else {
                    ISpawnProvider spawnProvider = world.getWorldConfig().getSpawnProvider();
                    spawnPoint = spawnProvider != null ? spawnProvider.getSpawnPoint(world, playerUUID) : new Transform();
                }
                Teleport teleport = Teleport.createForPlayer(world, spawnPoint);
                accessor.addComponent(entityRef, Teleport.getComponentType(), teleport);
            }, currentWorld).exceptionally(ex -> {
                LOGGER.at(Level.SEVERE).withCause(ex).log("Failed to create/load world '%s'", resolvedWorldName);
                return null;
            });
        }
    }

    private static void teleportToLoadedWorld(
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull ComponentAccessor<EntityStore> componentAccessor,
        @Nonnull World targetWorld,
        @Nonnull Player playerComponent
    ) {
        Map<String, PlayerWorldData> perWorldData = playerComponent.getPlayerConfigData().getPerWorldData();
        PlayerWorldData worldData = perWorldData.get(targetWorld.getName());
        Transform spawnPoint;
        if (worldData != null && worldData.getLastPosition() != null) {
            spawnPoint = worldData.getLastPosition();
        } else {
            UUIDComponent uuidComponent = componentAccessor.getComponent(playerRef, UUIDComponent.getComponentType());
            assert uuidComponent != null;
            ISpawnProvider spawnProvider = targetWorld.getWorldConfig().getSpawnProvider();
            spawnPoint = spawnProvider != null ? spawnProvider.getSpawnPoint(targetWorld, uuidComponent.getUuid()) : new Transform();
        }

        Teleport teleportComponent = Teleport.createForPlayer(targetWorld, spawnPoint);
        componentAccessor.addComponent(playerRef, Teleport.getComponentType(), teleportComponent);
    }

}
