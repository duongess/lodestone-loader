package com.duongess.lodestone;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.duongess.lodestone.event.InventoryChangedCallback;

public class LodestoneLoader implements ModInitializer {

    // If using /forceload or other paths that change components which the mixin
    // cannot catch, this safety net ensures it is re-scanned after at most 1 second.
    private static final int SAFETY_NET_INTERVAL_TICKS = 20;
    private int tickCounter = 0;

    // Chunks that each specific player currently needs - to calculate diff when inventory changes
    private final Map<UUID, Set<String>> playerRequiredChunks = new HashMap<>();

    // Count of players needing 1 chunk - to avoid unloading errors when 2 people point to the same lodestone
    private final Map<String, Integer> chunkRefCount = new HashMap<>();

    @Override
    public void onInitialize() {
        // 1. Scan when the player joins the game
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                scanInventoryAndLoad(handler.player));

        // 2. Release all chunks of the player when they disconnect
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                releasePlayerChunks(handler.player));

        // 3. Re-scan when inventory changes (emitted by InventoryMixin)
        InventoryChangedCallback.EVENT.register((inventory) -> {
            if (inventory.player instanceof ServerPlayer player) {
                scanInventoryAndLoad(player);
            }
        });

        // 4. When player clicks Lodestone with compass: vanilla applies LodestoneTracker
        //    directly to existing ItemStack (does not call setItem), so our mixin
        //    cannot catch it. Must proactively schedule a re-scan after vanilla handles it.
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClientSide()) {
                BlockPos pos = hitResult.getBlockPos();
                if (world.getBlockState(pos).is(Blocks.LODESTONE)) {
                    ItemStack stack = player.getItemInHand(hand);
                    if (stack.is(Items.COMPASS) && player instanceof ServerPlayer serverPlayer) {
                        player.sendSystemMessage(Component.literal("§a[LodestoneLoader] Chunk loader set at: " + pos.toShortString()));
                        // Wait for vanilla to finish setting the component, then queue at the end of the tick
                        world.getServer().execute(() -> scanInventoryAndLoad(serverPlayer));
                    }
                }
            }
            return InteractionResult.PASS;
        });

        // 5. Safety net: re-scan all online players every second, in case
        //    some other path changed the component that none of the above paths could catch
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            if (tickCounter >= SAFETY_NET_INTERVAL_TICKS) {
                tickCounter = 0;
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    scanInventoryAndLoad(player);
                }
            }
        });
    }

    /**
     * Scans a player's inventory, calculates the diff from their previous scan,
     * and updates the ref-count / force-load accordingly. Does not share a single Set for
     * each player, so one player's inventory change does not affect
     * another's chunks.
     */
    private void scanInventoryAndLoad(ServerPlayer player) {
        if (player == null) return;

        MinecraftServer server = player.level().getServer();
        if (server == null) return;

        Set<String> newRequiredChunks = computeRequiredChunks(player);
        Set<String> oldRequiredChunks = playerRequiredChunks.getOrDefault(player.getUUID(), Set.of());

        for (String chunkKey : oldRequiredChunks) {
            if (!newRequiredChunks.contains(chunkKey)) {
                releaseChunk(server, chunkKey);
            }
        }
        for (String chunkKey : newRequiredChunks) {
            if (!oldRequiredChunks.contains(chunkKey)) {
                acquireChunk(server, chunkKey);
            }
        }

        playerRequiredChunks.put(player.getUUID(), newRequiredChunks);
    }

    private void releasePlayerChunks(ServerPlayer player) {
        if (player == null) return;

        MinecraftServer server = player.level().getServer();
        Set<String> oldRequiredChunks = playerRequiredChunks.remove(player.getUUID());
        if (server == null || oldRequiredChunks == null) return;

        for (String chunkKey : oldRequiredChunks) {
            releaseChunk(server, chunkKey);
        }
    }

    private Set<String> computeRequiredChunks(ServerPlayer player) {
        Set<String> requiredChunks = new HashSet<>();

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);

            if (!stack.is(Items.COMPASS) || !stack.has(DataComponents.LODESTONE_TRACKER)) {
                continue;
            }

            LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
            if (tracker == null || tracker.target().isEmpty()) {
                continue;
            }

            var globalPos = tracker.target().get();
            BlockPos lodestonePos = globalPos.pos();
            // Use full toString() "namespace:path", do not use getPath() as it loses namespace
            String dimensionId = globalPos.dimension().identifier().toString();

            // ChunkPos(BlockPos) automatically converts block coordinates -> chunk coordinates
            ChunkPos centerChunk = new ChunkPos(lodestonePos.getX(), lodestonePos.getZ());
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    // Use comma to separate Dimension ID and coordinates
                    String chunkKey = makeChunkKey(dimensionId, centerChunk.getBlockX(i) + x, centerChunk.getBlockZ(i) + z);  
                    requiredChunks.add(chunkKey);
                }
            }
        }

        return requiredChunks;
    }

    private void acquireChunk(MinecraftServer server, String chunkKey) {
        int newCount = chunkRefCount.merge(chunkKey, 1, Integer::sum);
        if (newCount == 1) {
            setChunkForceState(server, chunkKey, true);
        }
    }

    private void releaseChunk(MinecraftServer server, String chunkKey) {
        int newCount = chunkRefCount.merge(chunkKey, -1, Integer::sum);
        if (newCount <= 0) {
            chunkRefCount.remove(chunkKey);
            setChunkForceState(server, chunkKey, false);
        }
    }

    private String makeChunkKey(String dimensionId, int chunkX, int chunkZ) {
        return dimensionId + "|" + chunkX + "|" + chunkZ;
    }

    private void setChunkForceState(MinecraftServer server, String chunkKey, boolean isForced) {
        try {
            String[] parts = chunkKey.split("\\|");
            if (parts.length != 3) return;

            String dimId = parts[0];
            int cX = Integer.parseInt(parts[1]);
            int cZ = Integer.parseInt(parts[2]);

            ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimId));
            ServerLevel targetWorld = server.getLevel(dimKey);

            if (targetWorld != null) {
                targetWorld.setChunkForced(cX, cZ, isForced);
                System.out.println("[LodestoneLoader] " + (isForced ? "Force loading" : "Unloading") + " chunk: " + chunkKey);
            } else {
                System.out.println("[LodestoneLoader] ERROR: World not found for dimension: " + dimId);
            }
        } catch (Exception e) {
            System.out.println("[LodestoneLoader] ERROR parsing chunk key: " + chunkKey);
            e.printStackTrace();
        }
    }
}