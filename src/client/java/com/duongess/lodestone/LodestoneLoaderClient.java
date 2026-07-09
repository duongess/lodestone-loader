package com.duongess.lodestone;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;

public class LodestoneLoaderClient implements ClientModInitializer {
    
    @Override
    public void onInitializeClient() {
        // Append Lodestone coordinate tooltip
        ItemTooltipCallback.EVENT.register((stack, context, type, tooltip) -> {
            if (stack.is(Items.COMPASS) && stack.has(DataComponents.LODESTONE_TRACKER)) {
                LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
                if (tracker != null && tracker.target().isPresent()) {
                    var globalPos = tracker.target().get();
                    var pos = globalPos.pos();
                    
                    String dimensionId = globalPos.dimension().identifier().getPath().toString();
                    
                    tooltip.add(Component.literal("§7Lodestone: §e" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()));
                    tooltip.add(Component.literal("§7Dimension: §b" + dimensionId));
                }
            }
        });
    }
}