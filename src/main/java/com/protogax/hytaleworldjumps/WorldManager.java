package com.protogax.hytaleworldjumps;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class WorldManager {

    public static final String FLAT_CREATIVE = "flat-creative";
    public static final String HYTALE_CREATIVE = "hytale-creative";

    @Nonnull
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Nonnull
    public CompletableFuture<World> getOrCreateWorld(@Nonnull String name, @Nullable String worldGenType) {
        Universe universe = Universe.get();
        World existing = universe.getWorld(name);
        if (existing != null && existing.isAlive()) {
            return CompletableFuture.completedFuture(existing);
        }

        if (universe.isWorldLoadable(name)) {
            return universe.loadWorld(name);
        }

        String genType = worldGenType != null ? worldGenType : "Flat";
        CompletableFuture<World> future = universe.addWorld(name, genType, null);
        future.thenAccept(world -> {
            WorldConfig config = world.getWorldConfig();
            config.setGameMode(GameMode.Creative);
            if (config.getDisplayName() == null) {
                config.setDisplayName(WorldConfig.formatDisplayName(name));
            }
            LOGGER.at(Level.INFO).log("[WorldJumps] Created world '%s' with gen type '%s'", name, genType);
        });
        return future;
    }

    @Nullable
    public World getDefaultWorld() {
        return Universe.get().getDefaultWorld();
    }

    public boolean isCreativeWorld(@Nullable String worldName) {
        return FLAT_CREATIVE.equals(worldName) || HYTALE_CREATIVE.equals(worldName);
    }
}
