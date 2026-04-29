package com.protogax.hytale.worldjumps;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class WorldJumpsConfig {

    public static final String HYTALE_CREATIVE_WORLD = "worldjumps-hytale-creative";
    public static final String FLAT_CREATIVE_WORLD = "worldjumps-flat-creative";

    public static final BuilderCodec<WorldJumpsConfig> CODEC = BuilderCodec.builder(
            WorldJumpsConfig.class, WorldJumpsConfig::new
        )
        .<String>append(
            new KeyedCodec<>("HytaleCreativeDisplayName", Codec.STRING),
            (c, v) -> c.hytaleCreativeDisplayName = v, c -> c.hytaleCreativeDisplayName
        ).add()
        .<String>append(
            new KeyedCodec<>("FlatCreativeDisplayName", Codec.STRING),
            (c, v) -> c.flatCreativeDisplayName = v, c -> c.flatCreativeDisplayName
        ).add()
        .<String>append(
            new KeyedCodec<>("DefaultWorldDisplayName", Codec.STRING),
            (c, v) -> c.defaultWorldDisplayName = v, c -> c.defaultWorldDisplayName
        ).add()
        .<Boolean>append(
            new KeyedCodec<>("AllowClearInventoryInCreative", Codec.BOOLEAN),
            (c, v) -> c.allowClearInventoryInCreative = v, c -> c.allowClearInventoryInCreative
        ).add()
        .build();

    private String hytaleCreativeDisplayName = "Hytale Creative";
    private String flatCreativeDisplayName = "Flat Creative";
    private String defaultWorldDisplayName = "";
    private boolean allowClearInventoryInCreative = false;

    public WorldJumpsConfig() {
    }

    public String getHytaleCreativeDisplayName() {
        return resolveTemplate(hytaleCreativeDisplayName);
    }

    public String getFlatCreativeDisplayName() {
        return resolveTemplate(flatCreativeDisplayName);
    }

    public String getDefaultWorldDisplayName() {
        return defaultWorldDisplayName;
    }

    public boolean isAllowClearInventoryInCreative() {
        return allowClearInventoryInCreative;
    }

    public boolean isCreativeWorld(@Nullable String worldName) {
        return HYTALE_CREATIVE_WORLD.equals(worldName) || FLAT_CREATIVE_WORLD.equals(worldName);
    }

    @Nullable
    public static String resolveWorldName(@Nullable String targetWorld) {
        if (targetWorld == null) return null;
        return switch (targetWorld) {
            case "Default" -> "default";
            case "HytaleCreative" -> HYTALE_CREATIVE_WORLD;
            case "FlatCreative" -> FLAT_CREATIVE_WORLD;
            default -> null;
        };
    }

    @Nullable
    public String getDisplayName(@Nonnull String worldName) {
        if (HYTALE_CREATIVE_WORLD.equals(worldName)) {
            return getHytaleCreativeDisplayName();
        }
        if (FLAT_CREATIVE_WORLD.equals(worldName)) {
            return getFlatCreativeDisplayName();
        }
        return null;
    }

    private static String resolveTemplate(String template) {
        if (template == null || !template.contains("{")) {
            return template;
        }

        String result = template;

        if (result.contains("{ServerName}")) {
            try {
                String serverName = HytaleServer.get().getServerName();
                result = result.replace("{ServerName}", serverName != null ? serverName : "Server");
            } catch (Exception e) {
                result = result.replace("{ServerName}", "Server");
            }
        }

        if (result.contains("{DefaultWorldDisplayName}")) {
            try {
                World defaultWorld = Universe.get().getDefaultWorld();
                String name = defaultWorld != null ? defaultWorld.getWorldConfig().getDisplayName() : null;
                result = result.replace("{DefaultWorldDisplayName}", name != null ? name : "Default");
            } catch (Exception e) {
                result = result.replace("{DefaultWorldDisplayName}", "Default");
            }
        }

        return result;
    }
}
