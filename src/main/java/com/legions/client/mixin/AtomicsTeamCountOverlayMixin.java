package com.legions.client.mixin;

import com.legions.client.LegionsClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.atomics.client.ClientFeatureManager")
public abstract class AtomicsTeamCountOverlayMixin {
    @Inject(method = "renderTeamCountOverlay", at = @At("HEAD"), cancellable = true, remap = false)
    private static void legions_client$suppressAtomicsTeamCountOverlay(DrawContext context, MinecraftClient client,
                                                                       int configuredX, int configuredY, boolean preview,
                                                                       CallbackInfo ci) {
        if (LegionsClient.CONFIG != null) {
            ci.cancel();
        }
    }
}
