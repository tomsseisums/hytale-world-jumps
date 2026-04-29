package com.protogax.hytale.worldjumps.command;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.block.BlockUtil;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.util.ColorParseUtil;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import com.protogax.hytale.worldjumps.marker.PortalMapMarkersResource;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.logging.Level;

import org.joml.Vector3i;

public class WorldJumpsReindexCommand extends AbstractWorldCommand {

    @Nonnull
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final int CHUNK_SIZE = 32;
    private static final int SECTION_HEIGHT = 32;
    private static final int SECTION_COUNT = 10;

    private static final Map<String, PortalDef> PORTAL_DEFS = Map.of(
        "WorldJumps_Portal_Default", new PortalDef("worldjumps.portal.marker", "Warp.png", "#404040", "Default"),
        "WorldJumps_Portal_HytaleCreative", new PortalDef("worldjumps.portal.marker", "Warp.png", "#ff8a00", "HytaleCreative"),
        "WorldJumps_Portal_FlatCreative", new PortalDef("worldjumps.portal.marker", "Warp.png", "#00ff00", "FlatCreative")
    );

    public WorldJumpsReindexCommand() {
        super("reindex", "server.commands.worldjumps.reindex.desc");
        this.setPermissionGroups("hytale:Admin");
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull World world, @Nonnull Store<EntityStore> entityStore) {
        IntList portalBlockIds = new IntArrayList(PORTAL_DEFS.size());
        Int2ObjectMap<PortalDef> idToDef = new Int2ObjectOpenHashMap<>(PORTAL_DEFS.size());
        for (Map.Entry<String, PortalDef> entry : PORTAL_DEFS.entrySet()) {
            int blockId = BlockType.getBlockIdOrUnknown(entry.getKey(),
                "[WorldJumps] reindex: missing BlockType '%s'", entry.getKey());
            portalBlockIds.add(blockId);
            idToDef.put(blockId, entry.getValue());
        }

        WorldResult result;
        try {
            result = scanWorld(world, portalBlockIds, idToDef);
        } catch (Throwable t) {
            LOGGER.at(Level.SEVERE).withCause(t).log("[WorldJumps] reindex failed for world '%s'", world.getName());
            context.sendMessage(Message.raw("[WorldJumps] Reindex failed for world '" + world.getName() + "': " + t.getClass().getSimpleName()));
            return;
        }

        String summary = "[WorldJumps] Reindex complete in '" + result.worldName + "' — cleared "
            + result.cleared + ", added " + result.added + " marker(s) across " + result.chunks + " loaded chunk(s)";
        LOGGER.at(Level.INFO).log("%s", summary);
        context.sendMessage(Message.raw(summary));
    }

    private static WorldResult scanWorld(@Nonnull World world, @Nonnull IntList portalBlockIds, @Nonnull Int2ObjectMap<PortalDef> idToDef) {
        ChunkStore chunkStore = world.getChunkStore();
        Store<ChunkStore> chunkStoreStore = chunkStore.getStore();
        int[] addedCount = {0};
        int[] scannedChunks = {0};
        int[] clearedCount = {0};

        // Track which chunks we've actually scanned and which portal positions we found.
        // Anything outside scanned chunks is left alone (chunks we can't see, can't migrate).
        LongSet scannedChunkCoords = new LongOpenHashSet();
        Long2ObjectMap<FoundPortal> foundPortals = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();

        chunkStoreStore.forEachChunk(BlockChunk.getComponentType(), (archetypeChunk, commandBuffer) -> {
            int size = archetypeChunk.size();
            for (int i = 0; i < size; i++) {
                BlockChunk bc = archetypeChunk.getComponent(i, BlockChunk.getComponentType());
                WorldChunk wc = archetypeChunk.getComponent(i, WorldChunk.getComponentType());
                if (bc == null || wc == null) {
                    continue;
                }
                scannedChunks[0]++;
                int chunkX = wc.getX();
                int chunkZ = wc.getZ();
                scannedChunkCoords.add(packChunk(chunkX, chunkZ));

                int chunkBaseX = chunkX << 5;
                int chunkBaseZ = chunkZ << 5;

                for (int s = 0; s < SECTION_COUNT; s++) {
                    BlockSection section = bc.getSectionAtIndex(s);
                    if (section == null || !section.containsAny(portalBlockIds)) {
                        continue;
                    }

                    int yStart = s * SECTION_HEIGHT;
                    int yEnd = yStart + SECTION_HEIGHT;
                    for (int x = 0; x < CHUNK_SIZE; x++) {
                        for (int z = 0; z < CHUNK_SIZE; z++) {
                            for (int y = yStart; y < yEnd; y++) {
                                int blockId = bc.getBlock(x, y, z);
                                PortalDef def = idToDef.get(blockId);
                                if (def == null) {
                                    continue;
                                }
                                // Skip filler cells of multi-block placements; only emit one marker
                                // per portal at the anchor cell (filler == NO_FILLER).
                                if (section.getFiller(x, y - yStart, z) != FillerBlockUtil.NO_FILLER) {
                                    continue;
                                }
                                Vector3i pos = new Vector3i(chunkBaseX + x, y, chunkBaseZ + z);
                                foundPortals.put(BlockUtil.pack(pos), new FoundPortal(pos, def));
                            }
                        }
                    }
                }
            }
        });

        // Apply: drop stale markers in scanned chunks, then re-add all found portals.
        PortalMapMarkersResource resource = chunkStoreStore.getResource(PortalMapMarkersResource.getResourceType());
        var iter = resource.getMarkers().long2ObjectEntrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            Vector3i markerPos = entry.getValue().getPosition();
            long chunkKey = packChunk(markerPos.x >> 5, markerPos.z >> 5);
            if (scannedChunkCoords.contains(chunkKey) && !foundPortals.containsKey(entry.getLongKey())) {
                iter.remove();
                clearedCount[0]++;
            }
        }
        for (FoundPortal fp : foundPortals.values()) {
            resource.addMarker(fp.position, fp.def.name, fp.def.icon, fp.def.tint(), fp.def.targetWorld);
            addedCount[0]++;
        }

        return new WorldResult(world.getName(), addedCount[0], scannedChunks[0], clearedCount[0], null);
    }

    private static long packChunk(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    private record FoundPortal(Vector3i position, PortalDef def) {
    }

    private record PortalDef(String name, String icon, String tintHex, String targetWorld) {
        Color tint() {
            Color c = ColorParseUtil.parseColor(this.tintHex);
            if (c == null) {
                throw new IllegalStateException("Invalid hex color: " + this.tintHex);
            }
            return c;
        }
    }

    private record WorldResult(String worldName, int added, int chunks, int cleared, Throwable error) {
    }
}
