package com.legions.client.mixin;

import com.legions.client.LegionsPingController;
import com.legions.client.LegionsWorldBorder;
import net.minecraft.client.render.debug.GameTestDebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameTestDebugRenderer.class)
public class GameTestDebugRendererMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void legions_client$renderBlockPingHighlights(CallbackInfo ci) {
        LegionsPingController.renderBlockHighlights();
        LegionsWorldBorder.render();
    }
}
