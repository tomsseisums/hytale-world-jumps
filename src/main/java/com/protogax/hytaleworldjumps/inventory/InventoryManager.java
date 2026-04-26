package com.protogax.hytaleworldjumps.inventory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.InventoryUtils;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class InventoryManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Nonnull
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Path dataDir;
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, PlayerInventoryData>> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> pendingLoad = new ConcurrentHashMap<>();
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "WorldJumps-IO");
        t.setDaemon(true);
        return t;
    });

    public InventoryManager(@Nonnull Path dataDir) {
        this.dataDir = dataDir;
    }

    public void saveInventory(@Nonnull Player player, @Nonnull UUID playerId, @Nonnull String worldName) {
        PlayerInventoryData snapshot = PlayerInventoryData.fromPlayer(player);
        cache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(worldName, snapshot);
        LOGGER.at(Level.FINE).log("[WorldJumps] SAVE: player=%s world='%s' items=%d", playerId, worldName, snapshot.getItems().size());
        ioExecutor.submit(() -> saveToDisk(playerId, worldName, snapshot));
    }

    public void setPendingLoad(@Nonnull UUID playerId, @Nonnull String worldName) {
        LOGGER.at(Level.FINE).log("[WorldJumps] PENDING-LOAD SET: player=%s world='%s'", playerId, worldName);
        pendingLoad.put(playerId, worldName);
    }

    public void applyPendingLoad(@Nonnull Player player, @Nonnull UUID playerId) {
        String worldName = pendingLoad.remove(playerId);
        if (worldName == null) {
            return;
        }

        // Try cache first
        ConcurrentHashMap<String, PlayerInventoryData> playerCache = cache.get(playerId);
        if (playerCache != null && playerCache.containsKey(worldName)) {
            PlayerInventoryData cached = playerCache.get(worldName);
            LOGGER.at(Level.FINE).log("[WorldJumps] LOAD (cache): player=%s world='%s' items=%d", playerId, worldName, cached.getItems().size());
            cached.applyToPlayer(player);
            return;
        }

        // Try disk
        PlayerInventoryData data = loadFromDisk(playerId, worldName);
        if (data != null) {
            LOGGER.at(Level.FINE).log("[WorldJumps] LOAD (disk): player=%s world='%s' items=%d", playerId, worldName, data.getItems().size());
            cache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(worldName, data);
            data.applyToPlayer(player);
            return;
        }

        // First visit: clear inventory
        LOGGER.at(Level.FINE).log("[WorldJumps] First visit to '%s' for player=%s — clearing inventory", worldName, playerId);
        Ref<EntityStore> ref = player.getReference();
        if (ref != null && ref.isValid()) {
            try {
                InventoryUtils.clear(ref, ref.getStore());
            } catch (Exception ignored) {
            }
        }
    }

    public void evict(@Nonnull UUID playerId) {
        pendingLoad.remove(playerId);
        ConcurrentHashMap<String, PlayerInventoryData> playerCache = cache.remove(playerId);
        if (playerCache != null) {
            LOGGER.at(Level.FINE).log("[WorldJumps] EVICTED cache for player=%s", playerId);
        }
    }

    public void saveAll() {
        LOGGER.at(Level.INFO).log("[WorldJumps] Flushing %d player(s) to disk...", cache.size());
        for (Map.Entry<UUID, ConcurrentHashMap<String, PlayerInventoryData>> playerEntry : cache.entrySet()) {
            UUID playerId = playerEntry.getKey();
            for (Map.Entry<String, PlayerInventoryData> worldEntry : playerEntry.getValue().entrySet()) {
                saveToDisk(playerId, worldEntry.getKey(), worldEntry.getValue());
            }
        }
        LOGGER.at(Level.INFO).log("[WorldJumps] Flush complete.");
    }

    public void shutdown() {
        ioExecutor.shutdown();
        try {
            if (!ioExecutor.awaitTermination(10L, TimeUnit.SECONDS)) {
                LOGGER.at(Level.WARNING).log("[WorldJumps] IO executor did not terminate in 10s — forcing shutdown.");
                ioExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ioExecutor.shutdownNow();
        }
    }

    private void saveToDisk(UUID playerId, String worldName, PlayerInventoryData data) {
        try {
            Path inventoriesDir = dataDir.resolve("inventories");
            Files.createDirectories(inventoriesDir);

            Path playerDir = inventoriesDir.resolve(playerId.toString());
            Files.createDirectories(playerDir);

            File file = playerDir.resolve(worldName + ".json").toFile();
            File temp = playerDir.resolve(worldName + ".json." + Thread.currentThread().getId() + ".tmp").toFile();

            try (FileWriter writer = new FileWriter(temp)) {
                GSON.toJson(data, writer);
            }

            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            LOGGER.at(Level.FINE).log("[WorldJumps] DISK-WRITE: player=%s world='%s' OK", playerId, worldName);
        } catch (Exception e) {
            LOGGER.at(Level.SEVERE).log("[WorldJumps] DISK-WRITE FAILED: player=%s world='%s' error=%s", playerId, worldName, e.getMessage());
        }
    }

    private PlayerInventoryData loadFromDisk(UUID playerId, String worldName) {
        try {
            File file = dataDir.resolve("inventories").resolve(playerId.toString()).resolve(worldName + ".json").toFile();
            if (!file.exists()) {
                return null;
            }
            if (file.length() > 2_000_000L) {
                LOGGER.at(Level.SEVERE).log("[WorldJumps] Inventory file too large: %s", file.getAbsolutePath());
                return null;
            }

            try (FileReader reader = new FileReader(file)) {
                return GSON.fromJson(reader, PlayerInventoryData.class);
            }
        } catch (Exception e) {
            LOGGER.at(Level.SEVERE).log("[WorldJumps] DISK-READ FAILED: player=%s world='%s' error=%s", playerId, worldName, e.getMessage());
            return null;
        }
    }
}
