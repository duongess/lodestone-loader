package com.duongess.lodestone;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

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

                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    for (int i = 0; i < player.getInventory().size(); i++) {
                        ItemStack stack = player.getInventory().getStack(i);
                        
                        // Kiem tra dung chuan Data Component moi cua Mojang
                        if (stack.isOf(Items.COMPASS) && stack.contains(DataComponentTypes.LODESTONE_TRACKER)) {
                            LodestoneTrackerComponent tracker = stack.get(DataComponentTypes.LODESTONE_TRACKER);
                            
                            if (tracker != null && tracker.target().isPresent()) {
                                LodestoneTrackerComponent.Target target = tracker.target().get();
                                BlockPos lodestonePos = target.pos();
                                String dimensionId = target.dimension().getValue().toString();
                                
                                ChunkPos centerChunk = new ChunkPos(lodestonePos);

                                for (int x = -1; x <= 1; x++) {
                                    for (int z = -1; z <= 1; z++) {
                                        String chunkKey = dimensionId + "_" + (centerChunk.x + x) + "_" + (centerChunk.z + z);
                                        currentRequiredChunks.add(chunkKey);
                                    }
                                }
                            }
                        }
                    }
                }

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
            String[] parts = chunkKey.split("_");
            if (parts.length != 3) return;

            String dimensionId = parts[0] + ":" + parts[1]; // Noi thanh 'minecraft:overworld'
            int chunkX = Integer.parseInt(parts[1]);
            int chunkZ = Integer.parseInt(parts[2]);

            // Truyen du lieu cho phien ban moi
            RegistryKey<World> dimKey = RegistryKey.of(RegistryKeys.WORLD, Identifier.tryParse(parts[0]));
            ServerWorld targetWorld = server.getWorld(dimKey);

            if (targetWorld != null) {
                targetWorld.setChunkForced(chunkX, chunkZ, isForced);
            }
        } catch (Exception ignored) {}
    }
}