package com.duongess.lodestone;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier; //ResourceLocation
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;

public class LodestoneLoader implements ModInitializer {

    private int tickCounter = 0;
    private final Set<String> activeLoadedChunks = new HashSet<>();

    @Override
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            
            if (tickCounter >= 20) {
                tickCounter = 0;
                Set<String> currentRequiredChunks = new HashSet<>();

                // Đổi thành ServerPlayer theo Mojang Mappings
                for (ServerPlayer player : server.getPlayerCount() > 0 ? server.getPlayerList().getPlayers() : java.util.Collections.<ServerPlayer>emptyList()) {
                    for (int i = 0; i < player.getInventory().getContainerSize(); i++) { // Đổi .size() thành .getContainerSize()
                        ItemStack stack = player.getInventory().getItem(i); // Đổi .getStack() thành .getItem()
                        
                        // Kiểm tra Component theo chuẩn Mojang 26.1 (.has() và lớp LodestoneTracker)
                        if (stack.is(Items.COMPASS) && stack.has(DataComponents.LODESTONE_TRACKER)) {
                            LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
                            
                            if (tracker != null && tracker.target().isPresent()) {
                                var globalPos = tracker.target().get();
                                BlockPos lodestonePos = globalPos.pos();
                                
                                // Lấy chuỗi ID thế giới chuẩn qua .location()
                                String dimensionId = globalPos.dimension().registryKey().toString();
                                ChunkPos centerChunk = new ChunkPos(lodestonePos.getX() >> 4, lodestonePos.getZ() >> 4);

                                for (int x = -1; x <= 1; x++) {
                                    for (int z = -1; z <= 1; z++) {
                                        String chunkKey = dimensionId + ":" + (centerChunk.getBlockX(i) + x) + ":" + (centerChunk.getBlockZ(i) + z);
                                        currentRequiredChunks.add(chunkKey);
                                    }
                                }
                            }
                        }
                    }
                }

                // Đồng bộ chunk trạng thái
                for (String chunkKey : activeLoadedChunks) {
                    if (!currentRequiredChunks.contains(chunkKey)) {
                        setChunkForceState(server, chunkKey, false);
                    }
                }
                for (String chunkKey : currentRequiredChunks) {
                    if (!activeLoadedChunks.contains(chunkKey)) {
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

            // Đổi RegistryKey và parse ResourceLocation theo chuẩn mới
            ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimId));
            ServerLevel targetWorld = server.getLevel(dimKey);

            if (targetWorld != null) {
                targetWorld.setChunkForced(cX, cZ, isForced);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
