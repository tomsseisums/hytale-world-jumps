package com.protogax.hytaleworldjumps.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.CommandBuffer;
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
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.spawn.ISpawnProvider;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.logging.Level;

public class ReturnInteraction extends SimpleInstantInteraction {

    @Nonnull
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Nonnull
    public static final BuilderCodec<ReturnInteraction> CODEC = BuilderCodec.builder(
            ReturnInteraction.class, ReturnInteraction::new, SimpleInstantInteraction.CODEC
        )
        .documentation("Teleports the Player back to the default exploration world.")
        .build();

    public ReturnInteraction() {
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

        World explorationWorld = Universe.get().getDefaultWorld();
        if (explorationWorld == null) {
            LOGGER.at(Level.SEVERE).log("Cannot return player — no default world available");
            return;
        }

        Map<String, PlayerWorldData> perWorldData = playerComponent.getPlayerConfigData().getPerWorldData();
        PlayerWorldData worldData = perWorldData.get(explorationWorld.getName());
        Transform spawnPoint;
        if (worldData != null && worldData.getLastPosition() != null) {
            spawnPoint = worldData.getLastPosition();
        } else {
            UUIDComponent uuidComponent = commandBuffer.getComponent(ref, UUIDComponent.getComponentType());
            assert uuidComponent != null;
            ISpawnProvider spawnProvider = explorationWorld.getWorldConfig().getSpawnProvider();
            spawnPoint = spawnProvider != null ? spawnProvider.getSpawnPoint(explorationWorld, uuidComponent.getUuid()) : new Transform();
        }

        Teleport teleportComponent = Teleport.createForPlayer(explorationWorld, spawnPoint);
        commandBuffer.addComponent(ref, Teleport.getComponentType(), teleportComponent);
    }
}
