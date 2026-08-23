package com.legions.client.mixin;

import com.legions.client.render.LegionsPlayerOverlayColorContext;
import net.minecraft.client.render.command.BatchingRenderCommandQueue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(BatchingRenderCommandQueue.class)
public class RenderCommandQueueMixin {
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
