package com.legions.client.mixin;

import com.legions.client.render.LegionsPlayerOverlayColorContext;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmorFeatureRenderer.class)
public class ArmorFeatureRendererMixin {
    @Inject(
            method = "renderArmor(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EquipmentSlot;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;)V",
            at = @At("HEAD")
    )
    private void legions_client$beginArmorOverlay(MatrixStack matrices, OrderedRenderCommandQueue queue, ItemStack stack,
                                                  EquipmentSlot slot, int light, BipedEntityRenderState state,
                                                  CallbackInfo ci) {
        LegionsPlayerOverlayColorContext.pushArmorRender();
    }

    @Inject(
            method = "renderArmor(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EquipmentSlot;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;)V",
            at = @At("RETURN")
    )
    private void legions_client$endArmorOverlay(MatrixStack matrices, OrderedRenderCommandQueue queue, ItemStack stack,
                                                EquipmentSlot slot, int light, BipedEntityRenderState state,
                                                CallbackInfo ci) {
        LegionsPlayerOverlayColorContext.popArmorRender();
    }
}
