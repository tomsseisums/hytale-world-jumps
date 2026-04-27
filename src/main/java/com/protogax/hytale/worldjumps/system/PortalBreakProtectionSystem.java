package com.protogax.hytale.worldjumps.system;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;
import java.util.logging.Level;

public class PortalBreakProtectionSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    @Nonnull
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final String ADMIN_GROUP = "hytale:Admin";
    private static final String PORTAL_ID_MARKER = "WorldJumps_Portal";

    public PortalBreakProtectionSystem() {
        super(BreakBlockEvent.class);
    }

    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull BreakBlockEvent event
    ) {
        BlockType blockType = event.getBlockType();
        if (blockType == null) {
            return;
        }

        String blockId = blockType.getId();
        if (blockId == null || !blockId.contains(PORTAL_ID_MARKER)) {
            return;
        }

        // This is a WorldJumps portal — only allow OP to break it
        UUIDComponent uuidComponent = archetypeChunk.getComponent(index, UUIDComponent.getComponentType());
        if (uuidComponent == null) {
            event.setCancelled(true);
            return;
        }

        Set<String> groups = PermissionsModule.get().getGroupsForUser(uuidComponent.getUuid());
        if (!groups.contains(ADMIN_GROUP)) {
            event.setCancelled(true);
            LOGGER.at(Level.FINE).log("[WorldJumps] Blocked non-OP player from breaking portal at %s", event.getTargetBlock());
        }
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }
}
