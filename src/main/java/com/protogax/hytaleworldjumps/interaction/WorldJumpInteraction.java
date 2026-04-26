package com.protogax.hytaleworldjumps.interaction;

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
        .documentation("Teleports the Player to a named permanent world, creating it if required.")
        .<String>appendInherited(
            new KeyedCodec<>("WorldName", Codec.STRING),
            (o, i) -> o.worldName = i, o -> o.worldName, (o, p) -> o.worldName = p.worldName
        )
        .documentation("The name of the permanent world to teleport to.")
        .add()
        .<String>appendInherited(
            new KeyedCodec<>("WorldGenType", Codec.STRING),
            (o, i) -> o.worldGenType = i, o -> o.worldGenType, (o, p) -> o.worldGenType = p.worldGenType
        )
        .documentation("The world generator type to use when creating the world (e.g., 'Flat', 'Hytale').")
        .add()
        .build();

    private String worldName;
    private String worldGenType;

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
        World targetWorld = universe.getWorld(this.worldName);

        if (targetWorld != null) {
            teleportToLoadedWorld(ref, commandBuffer, targetWorld, playerComponent);
        } else {
            CompletableFuture<World> worldFuture;
            if (universe.isWorldLoadable(this.worldName)) {
                worldFuture = universe.loadWorld(this.worldName);
            } else {
                worldFuture = universe.addWorld(this.worldName, this.worldGenType != null ? this.worldGenType : "Flat", null);
                worldFuture.thenAccept(world -> {
                    if (world.getWorldConfig().getDisplayName() == null) {
                        world.getWorldConfig().setDisplayName(
                            com.hypixel.hytale.server.core.universe.world.WorldConfig.formatDisplayName(this.worldName)
                        );
                    }
                    world.getWorldConfig().setGameMode(com.hypixel.hytale.protocol.GameMode.Creative);
                });
            }

            // Wait for the world to load, then teleport via Teleport component on the current world's thread
            PlayerRef playerRefComponent = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
            UUIDComponent uuidComponent = commandBuffer.getComponent(ref, UUIDComponent.getComponentType());
            if (playerRefComponent == null || uuidComponent == null) {
                return;
            }
            UUID playerUUID = uuidComponent.getUuid();

            worldFuture.orTimeout(1L, TimeUnit.MINUTES).thenAcceptAsync(world -> {
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
                LOGGER.at(Level.SEVERE).withCause(ex).log("Failed to create/load world '%s'", this.worldName);
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
