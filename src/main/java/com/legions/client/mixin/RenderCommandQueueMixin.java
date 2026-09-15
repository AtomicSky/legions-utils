package com.legions.client.mixin;

import com.legions.client.render.LegionsPlayerOverlayColorContext;
import com.legions.client.render.LegionsQuipOutline;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.BatchingRenderCommandQueue;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.RenderCommandQueue;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BatchingRenderCommandQueue.class)
public class RenderCommandQueueMixin {
    @Inject(
            method = "submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/texture/Sprite;ILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V",
            at = @At("TAIL"), require = 1
    )
    private <S> void legions_client$submitQuipOutline(Model<? super S> model, S state, MatrixStack matrices,
            RenderLayer layer, int light, int overlay, int color, Sprite sprite, int outlineColor,
            ModelCommandRenderer.CrumblingOverlayCommand crumbling, CallbackInfo ci) {
        LegionsQuipOutline.submit((RenderCommandQueue) (Object) this, model, state, matrices);
    }

    @ModifyArg(
            method = "submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/texture/Sprite;ILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueueImpl$ModelCommand;<init>(Lnet/minecraft/client/util/math/MatrixStack$Entry;Lnet/minecraft/client/model/Model;Ljava/lang/Object;IIILnet/minecraft/client/texture/Sprite;ILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V"
            ),
            index = 5
    )
    private int legions_client$applyFoeOverlay(int originalColor) {
        return LegionsPlayerOverlayColorContext.apply(originalColor);
    }
}
