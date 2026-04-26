package com.protogax.hytaleworldjumps.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

import java.util.Set;
import java.util.logging.Level;

public class ProtectedBreakInteraction extends SimpleBlockInteraction {

    @Nonnull
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final String ADMIN_GROUP = "hytale:Admin";

    @Nonnull
    public static final BuilderCodec<ProtectedBreakInteraction> CODEC = BuilderCodec.builder(
            ProtectedBreakInteraction.class, ProtectedBreakInteraction::new, SimpleBlockInteraction.CODEC
        )
        .documentation("Break interaction restricted to OP players in Creative mode.")
        .build();

    public ProtectedBreakInteraction() {
    }

    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    protected void interactWithBlock(
        @Nonnull World world,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InteractionType type,
        @Nonnull InteractionContext context,
        @Nullable ItemStack heldItemStack,
        @Nonnull Vector3i targetBlock,
        @Nonnull CooldownHandler cooldownHandler
    ) {
        Ref<EntityStore> ref = context.getEntity();
        Player playerComponent = commandBuffer.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        // Check Creative mode
        if (playerComponent.getGameMode() != GameMode.Creative) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        // Check OP (hytale:Admin group)
        UUIDComponent uuidComponent = commandBuffer.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComponent == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        Set<String> groups = PermissionsModule.get().getGroupsForUser(uuidComponent.getUuid());
        if (!groups.contains(ADMIN_GROUP)) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        // OP + Creative: perform the break
        ChunkStore chunkStore = world.getChunkStore();
        long chunkIndex = ChunkUtil.indexChunkFromBlock(targetBlock.x, targetBlock.z);
        Ref<ChunkStore> chunkReference = chunkStore.getChunkReference(chunkIndex);
        if (chunkReference != null && chunkReference.isValid()) {
            BlockHarvestUtils.performBlockBreak(ref, heldItemStack, targetBlock, chunkReference, commandBuffer, chunkStore.getStore());
        } else {
            context.getState().state = InteractionState.Failed;
        }
    }

    @Override
    protected void simulateInteractWithBlock(
        @Nonnull InteractionType type,
        @Nonnull InteractionContext context,
        @Nullable ItemStack itemInHand,
        @Nonnull World world,
        @Nonnull Vector3i targetBlock
    ) {
        context.getState().state = InteractionState.Failed;
    }
}
