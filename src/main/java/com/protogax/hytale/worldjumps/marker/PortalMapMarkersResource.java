package com.protogax.hytale.worldjumps.marker;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.math.block.BlockUtil;
import com.hypixel.hytale.math.vector.Vector3iUtil;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.server.core.codec.ProtocolCodecs;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.protogax.hytale.worldjumps.WorldJumpsPlugin;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

import org.joml.Vector3i;

public class PortalMapMarkersResource implements Resource<ChunkStore> {

    public static final BuilderCodec<PortalMapMarkersResource> CODEC = BuilderCodec.builder(PortalMapMarkersResource.class, PortalMapMarkersResource::new)
        .append(new KeyedCodec<>("Markers", new MapCodec<>(PortalMarkerData.CODEC, HashMap::new), true), (o, markersMap) -> {
            if (markersMap != null) {
                for (Map.Entry<String, PortalMarkerData> entry : markersMap.entrySet()) {
                    o.markers.put(Long.parseLong(entry.getKey()), entry.getValue());
                }
            }
        }, o -> {
            HashMap<String, PortalMarkerData> result = new HashMap<>(o.markers.size());
            for (Map.Entry<Long, PortalMarkerData> entry : o.markers.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        })
        .add()
        .build();

    private Long2ObjectMap<PortalMarkerData> markers = new Long2ObjectOpenHashMap<>();

    public PortalMapMarkersResource() {
    }

    public PortalMapMarkersResource(Long2ObjectMap<PortalMarkerData> markers) {
        this.markers = markers;
    }

    public static ResourceType<ChunkStore, PortalMapMarkersResource> getResourceType() {
        return WorldJumpsPlugin.getPortalMarkersResourceType();
    }

    @Nonnull
    public Long2ObjectMap<PortalMarkerData> getMarkers() {
        return this.markers;
    }

    public void addMarker(@Nonnull Vector3i position, @Nonnull String name, @Nonnull String icon, @Nonnull Color tint, @Nonnull String targetWorld) {
        long key = BlockUtil.pack(position);
        String markerId = "WorldJumpsPortal-" + position.x + "_" + position.y + "_" + position.z;
        this.markers.put(key, new PortalMarkerData(position, name, icon, tint, targetWorld, markerId));
    }

    public void removeMarker(@Nonnull Vector3i position) {
        long key = BlockUtil.pack(position);
        this.markers.remove(key);
    }

    @Override
    public Resource<ChunkStore> clone() {
        return new PortalMapMarkersResource(new Long2ObjectOpenHashMap<>(this.markers));
    }

    public static class PortalMarkerData {
        public static final BuilderCodec<PortalMarkerData> CODEC = BuilderCodec.builder(PortalMarkerData.class, PortalMarkerData::new)
            .append(new KeyedCodec<>("Position", Vector3iUtil.CODEC), (o, v) -> o.position = v, o -> o.position)
            .add()
            .append(new KeyedCodec<>("Name", Codec.STRING), (o, v) -> o.name = v, o -> o.name)
            .add()
            .append(new KeyedCodec<>("Icon", Codec.STRING), (o, v) -> o.icon = v, o -> o.icon)
            .add()
            .append(new KeyedCodec<>("Tint", ProtocolCodecs.COLOR), (o, v) -> o.tint = v, o -> o.tint)
            .add()
            .append(new KeyedCodec<>("TargetWorld", Codec.STRING, true), (o, v) -> o.targetWorld = v, o -> o.targetWorld)
            .add()
            .append(new KeyedCodec<>("MarkerId", Codec.STRING), (o, v) -> o.markerId = v, o -> o.markerId)
            .add()
            .build();

        private Vector3i position;
        private String name;
        private String icon;
        private Color tint;
        private String targetWorld;
        private String markerId;

        public PortalMarkerData() {
        }

        public PortalMarkerData(Vector3i position, String name, String icon, Color tint, String targetWorld, String markerId) {
            this.position = position;
            this.name = name;
            this.icon = icon;
            this.tint = tint;
            this.targetWorld = targetWorld;
            this.markerId = markerId;
        }

        public Vector3i getPosition() {
            return this.position;
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

        public String getMarkerId() {
            return this.markerId;
        }
    }
}
