package com.legions.client.mixin;

import com.legions.client.LegionsPingController;
import com.legions.client.LegionsWorldBorder;
import net.minecraft.client.renderer.debug.GameTestBlockHighlightRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameTestBlockHighlightRenderer.class)
public class GameTestDebugRendererMixin {
    @Inject(method = "emitGizmos", at = @At("TAIL"))
    private void legions_client$renderBlockPingHighlights(CallbackInfo ci) {
        LegionsPingController.renderBlockHighlights();
        LegionsWorldBorder.render();
    }
}
