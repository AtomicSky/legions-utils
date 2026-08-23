package com.legions.client.mixin;

import com.legions.client.render.LegionsPlayerOverlayColorContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public class ArmorFeatureRendererMixin {
    @Inject(
            method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",
            at = @At("HEAD")
    )
    private void legions_client$beginArmorOverlay(PoseStack matrices, SubmitNodeCollector queue, ItemStack stack,
                                                  EquipmentSlot slot, int light, HumanoidRenderState state,
                                                  CallbackInfo ci) {
        LegionsPlayerOverlayColorContext.pushArmorRender();
    }

    @Inject(
            method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",
            at = @At("RETURN")
    )
    private void legions_client$endArmorOverlay(PoseStack matrices, SubmitNodeCollector queue, ItemStack stack,
                                                EquipmentSlot slot, int light, HumanoidRenderState state,
                                                CallbackInfo ci) {
        LegionsPlayerOverlayColorContext.popArmorRender();
    }
}
