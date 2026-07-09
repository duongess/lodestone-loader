package com.duongess.lodestone;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
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

import java.util.HashSet;
import java.util.Set;

public class LodestoneLoader implements ModInitializer {

    private int tickCounter = 0;
    private final Set<String> activeLoadedChunks = new HashSet<>();

    @Override
    public void onInitialize() {
        
        // Log official action when clicking a Lodestone
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClientSide()) {
                BlockPos pos = hitResult.getBlockPos();
                if (world.getBlockState(pos).is(Blocks.LODESTONE)) {
                    ItemStack stack = player.getItemInHand(hand);
                    if (stack.is(Items.COMPASS)) {
                        System.out.println("[LodestoneLoader] Set chunk loader at Lodestone position: " + pos.toShortString());
                    }
                }
            }
            return InteractionResult.PASS;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            
            if (tickCounter >= 20) {
                tickCounter = 0;
                Set<String> currentRequiredChunks = new HashSet<>();

                for (ServerPlayer player : server.getPlayerCount() > 0 ? server.getPlayerList().getPlayers() : java.util.Collections.<ServerPlayer>emptyList()) {
                    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                        ItemStack stack = player.getInventory().getItem(i);
                        
                        if (stack.is(Items.COMPASS) && stack.has(DataComponents.LODESTONE_TRACKER)) {
                            LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
                            
                            if (tracker != null && tracker.target().isPresent()) {
                                var globalPos = tracker.target().get();
                                BlockPos lodestonePos = globalPos.pos();
                                
                                String dimensionId = globalPos.dimension().identifier().getPath().toString();
                                // Clean calculation of ChunkPos
                                ChunkPos centerChunk = new ChunkPos(lodestonePos.getX(), lodestonePos.getZ());

                                for (int x = -1; x <= 1; x++) {
                                    for (int z = -1; z <= 1; z++) {
                                        String chunkKey = dimensionId + ":" + (centerChunk.getBlockX(i) + x) + ":" + (centerChunk.getBlockZ(z));
                                        currentRequiredChunks.add(chunkKey);
                                    }
                                }
                            }
                        }
                    }
                }

                // Log only when state changes
                for (String chunkKey : activeLoadedChunks) {
                    if (!currentRequiredChunks.contains(chunkKey)) {
                        System.out.println("[LodestoneLoader] Unloading chunk: " + chunkKey);
                        setChunkForceState(server, chunkKey, false);
                    }
                }
                for (String chunkKey : currentRequiredChunks) {
                    if (!activeLoadedChunks.contains(chunkKey)) {
                        System.out.println("[LodestoneLoader] Force loading chunk: " + chunkKey);
                        setChunkForceState(server, chunkKey, true);
                    }
                }

                activeLoadedChunks.clear();
                activeLoadedChunks.addAll(currentRequiredChunks);
            }
        });
    }

    private void setChunkForceState(MinecraftServer server, String chunkKey, boolean isForced) {
        try {
            int lastColon = chunkKey.lastIndexOf(":");
            int secondToLastColon = chunkKey.lastIndexOf(":", lastColon - 1);
            
            String dimId = chunkKey.substring(0, secondToLastColon);
            int cX = Integer.parseInt(chunkKey.substring(secondToLastColon + 1, lastColon));
            int cZ = Integer.parseInt(chunkKey.substring(lastColon + 1));

            ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimId));
            ServerLevel targetWorld = server.getLevel(dimKey);

            if (targetWorld != null) {
                targetWorld.setChunkForced(cX, cZ, isForced);
            } else {
                System.out.println("[LodestoneLoader] ERROR: World not found for dimension: " + dimId);
            }
        } catch (Exception e) {
            System.out.println("[LodestoneLoader] ERROR parsing chunk key: " + chunkKey);
            e.printStackTrace();
        }
    }
}