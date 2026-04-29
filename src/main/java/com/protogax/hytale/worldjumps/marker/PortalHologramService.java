package com.protogax.hytale.worldjumps.marker;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * Spawns and removes nameplate hologram entities for placed portal blocks.
 * Holograms are entity-store entities tagged with {@link PortalHologramMarker};
 * lookup-by-position queries the tag rather than maintaining a parallel map.
 */
public final class PortalHologramService {

    @Nonnull
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Pad_Portal hitbox spans [-0.5, +1.5] in X/Z relative to the anchor block,
    // so the visible model's center sits at +0.5 from the anchor in the horizontal plane.
    // Lift the hologram a couple blocks above the pad so it floats over the portal column.
    private static final Vector3d ANCHOR_OFFSET = new Vector3d(0.5, 4.0, 0.5);

    private PortalHologramService() {
    }

    /**
     * Ensures a hologram exists at the given portal position with the given text.
     * If one already exists at that position, its text is updated; otherwise a new one is spawned.
     */
    public static void ensureSpawned(@Nonnull World world, @Nonnull Vector3i portalPos, @Nonnull String text) {
        final Vector3i posCopy = new Vector3i(portalPos);
        world.execute(() -> {
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();
                if (updateExisting(store, posCopy, text)) {
                    return;
                }
                spawn(world, posCopy, text);
            } catch (Throwable t) {
                LOGGER.at(Level.WARNING).withCause(t).log(
                    "[WorldJumps] Failed to ensure hologram at %s in '%s'", posCopy, world.getName());
            }
        });
    }

    /**
     * Updates the text of an existing hologram at this portal position. No-op if none exists.
     * Used on chunk LOAD where the saved hologram is restoring on its own; we just refresh
     * the text in case the world display name changed since the chunk was saved.
     */
    public static void refreshText(@Nonnull World world, @Nonnull Vector3i portalPos, @Nonnull String text) {
        final Vector3i posCopy = new Vector3i(portalPos);
        world.execute(() -> {
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();
                updateExisting(store, posCopy, text);
            } catch (Throwable t) {
                LOGGER.at(Level.WARNING).withCause(t).log(
                    "[WorldJumps] Failed to refresh hologram text at %s in '%s'", posCopy, world.getName());
            }
        });
    }

    /**
     * Removes any hologram(s) tagged with this portal position.
     */
    public static void despawn(@Nonnull World world, @Nonnull Vector3i portalPos) {
        final Vector3i posCopy = new Vector3i(portalPos);
        world.execute(() -> {
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();
                removeMatching(store, posCopy);
            } catch (Throwable t) {
                LOGGER.at(Level.WARNING).withCause(t).log(
                    "[WorldJumps] Failed to despawn hologram at %s in '%s'", posCopy, world.getName());
            }
        });
    }

    private static boolean updateExisting(@Nonnull Store<EntityStore> store, @Nonnull Vector3i portalPos, @Nonnull String text) {
        Vector3d desiredPos = new Vector3d(portalPos.x, portalPos.y, portalPos.z).add(ANCHOR_OFFSET);
        boolean[] found = {false};
        store.forEachChunk(PortalHologramMarker.getComponentType(), (chunk, cmd) -> {
            int size = chunk.size();
            for (int i = 0; i < size; i++) {
                PortalHologramMarker marker = chunk.getComponent(i, PortalHologramMarker.getComponentType());
                if (marker == null || !portalPos.equals(marker.getPortalPos())) {
                    continue;
                }
                Nameplate np = chunk.getComponent(i, Nameplate.getComponentType());
                boolean textChanged = false;
                if (np != null && !np.getText().equals(text)) {
                    np.setText(text);
                    textChanged = true;
                }
                TransformComponent tc = chunk.getComponent(i, TransformComponent.getComponentType());
                boolean positionChanged = tc != null && !tc.getPosition().equals(desiredPos);
                if (positionChanged) {
                    tc.teleportPosition(desiredPos);
                }
                // Nameplate.setText doesn't dirty the chunk on its own, so changes were lost
                // across save/load until the next refresh re-applied them in memory.
                if ((textChanged || positionChanged) && tc != null) {
                    tc.markChunkDirty(cmd);
                }
                found[0] = true;
            }
        });
        return found[0];
    }

    private static void removeMatching(@Nonnull Store<EntityStore> store, @Nonnull Vector3i portalPos) {
        List<Ref<EntityStore>> toRemove = new ArrayList<>();
        store.forEachChunk(PortalHologramMarker.getComponentType(), (chunk, cmd) -> {
            int size = chunk.size();
            for (int i = 0; i < size; i++) {
                PortalHologramMarker marker = chunk.getComponent(i, PortalHologramMarker.getComponentType());
                if (marker != null && portalPos.equals(marker.getPortalPos())) {
                    toRemove.add(chunk.getReferenceTo(i));
                }
            }
        });
        for (Ref<EntityStore> ref : toRemove) {
            store.removeEntity(ref, RemoveReason.REMOVE);
        }
    }

    private static void spawn(@Nonnull World world, @Nonnull Vector3i portalPos, @Nonnull String text) {
        Vector3d holoPos = new Vector3d(portalPos.x, portalPos.y, portalPos.z).add(ANCHOR_OFFSET);

        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        ProjectileComponent proj = new ProjectileComponent("Projectile");
        holder.putComponent(ProjectileComponent.getComponentType(), proj);
        holder.putComponent(TransformComponent.getComponentType(), new TransformComponent(holoPos, new Rotation3f()));
        holder.ensureComponent(UUIDComponent.getComponentType());

        if (proj.getProjectile() == null) {
            proj.initialize();
            if (proj.getProjectile() == null) {
                LOGGER.at(Level.WARNING).log("[WorldJumps] ProjectileComponent failed to initialize — hologram not spawned at %s", portalPos);
                return;
            }
        }

        Store<EntityStore> store = world.getEntityStore().getStore();
        holder.addComponent(NetworkId.getComponentType(),
            new NetworkId(store.getExternalData().takeNextNetworkId()));
        holder.addComponent(Nameplate.getComponentType(), new Nameplate(text));
        holder.addComponent(PortalHologramMarker.getComponentType(), new PortalHologramMarker(new Vector3i(portalPos)));

        store.addEntity(holder, AddReason.SPAWN);
    }
}
