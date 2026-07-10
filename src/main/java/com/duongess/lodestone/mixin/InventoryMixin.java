package com.duongess.lodestone.mixin;

import com.duongess.lodestone.event.InventoryChangedCallback;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Inventory.class)
public abstract class InventoryMixin {

    @Inject(method = "setItem", at = @At("TAIL"))
    private void lodestoneLoader$onSetItem(int slot, ItemStack stack, CallbackInfo ci) {
        InventoryChangedCallback.EVENT.invoker().onInventoryChanged((Inventory) (Object) this);
    }
}