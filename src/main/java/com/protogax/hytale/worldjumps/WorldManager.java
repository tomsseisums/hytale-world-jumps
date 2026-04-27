package com.protogax.hytale.worldjumps;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.logging.Level;

public class WorldManager {

    @Nonnull
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final WorldJumpsConfig config;

    public WorldManager(@Nonnull WorldJumpsConfig config) {
        this.config = config;
    }

    public void applyDisplayNames() {
        LOGGER.at(Level.INFO).log("[WorldJumps] Applying display names on boot...");
        Universe universe = Universe.get();

        // Default world
        String defaultDisplayName = config.getDefaultWorldDisplayName();
        if (defaultDisplayName != null && !defaultDisplayName.isEmpty()) {
            World defaultWorld = universe.getDefaultWorld();
            if (defaultWorld != null) {
                defaultWorld.getWorldConfig().setDisplayName(defaultDisplayName);
                LOGGER.at(Level.INFO).log("[WorldJumps] Default world '%s' -> display name '%s'", defaultWorld.getName(), defaultDisplayName);
            } else {
                LOGGER.at(Level.WARNING).log("[WorldJumps] Default world is null — cannot set display name");
            }
        } else {
            LOGGER.at(Level.INFO).log("[WorldJumps] DefaultWorldDisplayName not configured, skipping");
        }

        // Creative worlds
        applyDisplayNameIfLoaded(universe, WorldJumpsConfig.HYTALE_CREATIVE_WORLD, config.getHytaleCreativeDisplayName());
        applyDisplayNameIfLoaded(universe, WorldJumpsConfig.FLAT_CREATIVE_WORLD, config.getFlatCreativeDisplayName());

        LOGGER.at(Level.INFO).log("[WorldJumps] Display names applied.");
    }

    public void applyDisplayName(@Nonnull World world) {
        String displayName = config.getDisplayName(world.getName());
        if (displayName != null) {
            world.getWorldConfig().setDisplayName(displayName);
            LOGGER.at(Level.FINE).log("[WorldJumps] Set display name for '%s' to '%s'", world.getName(), displayName);
        }
    }

    public boolean isCreativeWorld(@Nullable String worldName) {
        return config.isCreativeWorld(worldName);
    }

    private void applyDisplayNameIfLoaded(@Nonnull Universe universe, @Nonnull String worldName, @Nullable String displayName) {
        if (displayName == null) return;
        World world = universe.getWorld(worldName);
        if (world != null) {
            world.getWorldConfig().setDisplayName(displayName);
            LOGGER.at(Level.INFO).log("[WorldJumps] Set display name for '%s' to '%s'", worldName, displayName);
        } else {
            LOGGER.at(Level.WARNING).log("[WorldJumps] World '%s' is null — cannot set display name", worldName);
        }
    }
}
