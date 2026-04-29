package com.protogax.hytale.worldjumps.marker;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.vector.Vector3iUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.protogax.hytale.worldjumps.WorldJumpsPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3i;

/**
 * Tags a hologram entity so we can find and remove it when its portal block is broken.
 */
public class PortalHologramMarker implements Component<EntityStore> {

    public static final BuilderCodec<PortalHologramMarker> CODEC = BuilderCodec.builder(PortalHologramMarker.class, PortalHologramMarker::new)
        .append(new KeyedCodec<>("PortalPos", Vector3iUtil.CODEC), (o, v) -> o.portalPos = v, o -> o.portalPos)
        .addValidator(Validators.nonNull())
        .add()
        .build();

    private Vector3i portalPos;

    public PortalHologramMarker() {
    }

    public PortalHologramMarker(@Nonnull Vector3i portalPos) {
        this.portalPos = portalPos;
    }

    public static ComponentType<EntityStore, PortalHologramMarker> getComponentType() {
        return WorldJumpsPlugin.getPortalHologramMarkerComponentType();
    }

    public Vector3i getPortalPos() {
        return this.portalPos;
    }

    @Nullable
    @Override
    public Component<EntityStore> clone() {
        return new PortalHologramMarker(new Vector3i(this.portalPos));
    }
}
