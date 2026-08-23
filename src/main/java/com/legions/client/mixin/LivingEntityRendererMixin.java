package com.legions.client.mixin;

import com.legions.client.access.LegionsPlayerOverlayRenderStateAccess;
import com.legions.client.LegionsFeatures;
import com.legions.client.render.LegionsPlayerOverlayColorContext;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LivingEntityRenderer.class, priority = 500)
public class LivingEntityRendererMixin {
    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void legions_client$hideFilteredPlayerModels(LivingEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState, CallbackInfo ci) {
        if (state instanceof PlayerEntityRenderState playerState && LegionsFeatures.shouldHidePlayerRenderState(playerState)) {
            LegionsPlayerOverlayColorContext.clear();
            ci.cancel();
            return;
        }
        if (state instanceof LegionsPlayerOverlayRenderStateAccess access) {
            LegionsPlayerOverlayColorContext.set(access.legions_client$getFoeOverlayColor(), access.legions_client$getFoeOverlayStyle());
        } else {
            LegionsPlayerOverlayColorContext.clear();
        }
    }

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("RETURN")
    )
    private void legions_client$clearPlayerOverlay(LivingEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState, CallbackInfo ci) {
        LegionsPlayerOverlayColorContext.clear();
    }
}
