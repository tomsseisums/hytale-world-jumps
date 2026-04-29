package com.protogax.hytale.worldjumps.marker;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.protocol.packets.worldmap.TintComponent;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.codec.ProtocolCodecs;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerBuilder;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MarkersCollector;
import com.protogax.hytale.worldjumps.WorldJumpsConfig;
import com.protogax.hytale.worldjumps.WorldJumpsPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

import org.joml.Vector3i;

public class PortalMapMarker implements Component<ChunkStore> {

    public static final BuilderCodec<PortalMapMarker> CODEC = BuilderCodec.builder(PortalMapMarker.class, PortalMapMarker::new)
        .append(new KeyedCodec<>("Name", Codec.STRING), (o, v) -> o.name = v, o -> o.name)
        .addValidator(Validators.nonNull())
        .add()
        .append(new KeyedCodec<>("Icon", Codec.STRING), (o, v) -> o.icon = v, o -> o.icon)
        .addValidator(Validators.nonNull())
        .add()
        .append(new KeyedCodec<>("Tint", ProtocolCodecs.COLOR), (o, v) -> o.tint = v, o -> o.tint)
        .addValidator(Validators.nonNull())
        .add()
        .append(new KeyedCodec<>("TargetWorld", Codec.STRING, true), (o, v) -> o.targetWorld = v, o -> o.targetWorld)
        .add()
        .build();

    private String name;
    private String icon;
    private Color tint;
    private String targetWorld;

    public PortalMapMarker() {
    }

    public PortalMapMarker(String name, String icon, Color tint, String targetWorld) {
        this.name = name;
        this.icon = icon;
        this.tint = tint;
        this.targetWorld = targetWorld;
    }

    public static ComponentType<ChunkStore, PortalMapMarker> getComponentType() {
        return WorldJumpsPlugin.getPortalMarkerComponentType();
    }

    public String getName() {
        return this.name;
    }

    public String getIcon() {
        return this.icon;
    }

    public Color getTint() {
        return this.tint;
    }

    public String getTargetWorld() {
        return this.targetWorld;
    }

    @Nullable
    @Override
    public Component<ChunkStore> clone() {
        return new PortalMapMarker(this.name, this.icon, this.tint, this.targetWorld);
    }

    /**
     * Overwrites this marker's fields with the given spec. Returns true if anything changed,
     * so callers can mark the chunk dirty to persist the migration.
     */
    public boolean migrateToSpec(@Nonnull String name, @Nonnull String icon, @Nonnull Color tint, @Nonnull String targetWorld) {
        boolean changed = !Objects.equals(this.name, name)
            || !Objects.equals(this.icon, icon)
            || !Objects.equals(this.targetWorld, targetWorld)
            || !Objects.equals(this.tint, tint);
        if (changed) {
            this.name = name;
            this.icon = icon;
            this.tint = tint;
            this.targetWorld = targetWorld;
        }
        return changed;
    }

    @Nonnull
    public static String resolveDisplayName(@Nullable String targetWorld) {
        if (targetWorld == null) {
            return "?";
        }
        String resolvedName = WorldJumpsConfig.resolveWorldName(targetWorld);
        if (resolvedName != null) {
            World loaded = Universe.get().getWorld(resolvedName);
            if (loaded != null) {
                String dn = loaded.getWorldConfig().getDisplayName();
                if (dn != null && !dn.isEmpty()) return dn;
            }
        }
        WorldJumpsConfig config = WorldJumpsPlugin.getPluginConfig();
        if (config != null) {
            if ("Default".equals(targetWorld)) {
                String dn = config.getDefaultWorldDisplayName();
                if (dn != null && !dn.isEmpty()) return dn;
            } else if (resolvedName != null) {
                String dn = config.getDisplayName(resolvedName);
                if (dn != null && !dn.isEmpty()) return dn;
            }
        }
        return targetWorld;
    }

    public static class MarkerProvider implements WorldMapManager.MarkerProvider {
        public static final MarkerProvider INSTANCE = new MarkerProvider();

        public MarkerProvider() {
        }

        @Override
        public void update(@Nonnull World world, @Nonnull Player player, @Nonnull MarkersCollector collector) {
            PortalMapMarkersResource resource = world.getChunkStore().getStore().getResource(PortalMapMarkersResource.getResourceType());
            for (PortalMapMarkersResource.PortalMarkerData data : resource.getMarkers().values()) {
                Vector3i pos = data.getPosition();
                String displayName = resolveDisplayName(data.getTargetWorld());
                MapMarker marker = new MapMarkerBuilder(data.getMarkerId(), data.getIcon(), new Transform(pos))
                    .withName(Message.translation(data.getName()).param("worldName", displayName))
                    .withComponent(new TintComponent(data.getTint()))
                    .build();
                collector.add(marker);
            }
        }
    }

    public static class OnAddRemove extends RefSystem<ChunkStore> {
        private static final ComponentType<ChunkStore, PortalMapMarker> COMPONENT_TYPE = PortalMapMarker.getComponentType();
        private static final ResourceType<ChunkStore, PortalMapMarkersResource> RESOURCE_TYPE = PortalMapMarkersResource.getResourceType();

        public OnAddRemove() {
        }

        @Override
        public void onEntityAdded(
            @Nonnull Ref<ChunkStore> ref, @Nonnull AddReason reason, @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> commandBuffer
        ) {
            BlockModule.BlockStateInfo blockInfo = commandBuffer.getComponent(ref, BlockModule.BlockStateInfo.getComponentType());

            assert blockInfo != null;

            Ref<ChunkStore> chunkRef = blockInfo.getChunkRef();
            if (chunkRef.isValid()) {
                PortalMapMarker marker = commandBuffer.getComponent(ref, COMPONENT_TYPE);

                assert marker != null;

                WorldChunk wc = commandBuffer.getComponent(chunkRef, WorldChunk.getComponentType());
                Vector3i pos = new Vector3i(
                    ChunkUtil.worldCoordFromLocalCoord(wc.getX(), ChunkUtil.xFromBlockInColumn(blockInfo.getIndex())),
                    ChunkUtil.yFromBlockInColumn(blockInfo.getIndex()),
                    ChunkUtil.worldCoordFromLocalCoord(wc.getZ(), ChunkUtil.zFromBlockInColumn(blockInfo.getIndex()))
                );
                PortalMapMarkersResource resource = commandBuffer.getResource(RESOURCE_TYPE);
                resource.addMarker(pos, marker.getName(), marker.getIcon(), marker.getTint(), marker.getTargetWorld());

                // Hologram lifecycle: spawn on new placement; on chunk LOAD, refresh text
                // (in case display-name config changed since the hologram was last saved).
                // Legacy markers may have a null TargetWorld — refreshing with the resulting "?"
                // would clobber any good text already set by reindex, so skip the refresh in
                // that case and let the saved Nameplate stand.
                World world = commandBuffer.getExternalData().getWorld();
                if (world != null) {
                    String targetWorld = marker.getTargetWorld();
                    if (reason == AddReason.SPAWN) {
                        PortalHologramService.ensureSpawned(world, pos, resolveDisplayName(targetWorld));
                    } else if (targetWorld != null) {
                        PortalHologramService.refreshText(world, pos, resolveDisplayName(targetWorld));
                    }
                }
            }
        }

        @Override
        public void onEntityRemove(
            @Nonnull Ref<ChunkStore> ref, @Nonnull RemoveReason reason, @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> commandBuffer
        ) {
            if (reason == RemoveReason.REMOVE) {
                BlockModule.BlockStateInfo blockInfo = commandBuffer.getComponent(ref, BlockModule.BlockStateInfo.getComponentType());

                assert blockInfo != null;

                Ref<ChunkStore> chunkRef = blockInfo.getChunkRef();
                if (chunkRef.isValid()) {
                    WorldChunk wc = commandBuffer.getComponent(chunkRef, WorldChunk.getComponentType());
                    Vector3i pos = new Vector3i(
                        ChunkUtil.worldCoordFromLocalCoord(wc.getX(), ChunkUtil.xFromBlockInColumn(blockInfo.getIndex())),
                        ChunkUtil.yFromBlockInColumn(blockInfo.getIndex()),
                        ChunkUtil.worldCoordFromLocalCoord(wc.getZ(), ChunkUtil.zFromBlockInColumn(blockInfo.getIndex()))
                    );
                    PortalMapMarkersResource resource = commandBuffer.getResource(RESOURCE_TYPE);
                    resource.removeMarker(pos);

                    World world = commandBuffer.getExternalData().getWorld();
                    if (world != null) {
                        PortalHologramService.despawn(world, pos);
                    }
                }
            }
        }

        @Nullable
        @Override
        public Query<ChunkStore> getQuery() {
            return COMPONENT_TYPE;
        }
    }
}
