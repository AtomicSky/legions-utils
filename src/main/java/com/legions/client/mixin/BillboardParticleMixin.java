package com.legions.client.mixin;

import com.legions.client.LegionsWorldBorder;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SingleQuadParticle.class)
public abstract class BillboardParticleMixin extends Particle {
    @Shadow
    protected TextureAtlasSprite sprite;

    @Unique
    private boolean legions_client$worldBorderGlitterCaptured;

    protected BillboardParticleMixin(ClientLevel world, double x, double y, double z) {
        super(world, x, y, z);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void legions_client$captureWorldBorderGlitter(CallbackInfo ci) {
        legions_client$worldBorderGlitterCaptured =
                LegionsWorldBorder.captureGlitterParticle(sprite, x, y, z);
    }

    @Inject(method = "extract(Lnet/minecraft/client/renderer/state/level/QuadParticleRenderState;Lnet/minecraft/client/Camera;F)V",
            at = @At("HEAD"), cancellable = true)
    private void legions_client$toggleWorldBorderGlitter(QuadParticleRenderState submittable,
                                                          Camera camera, float tickProgress,
                                                          CallbackInfo ci) {
        // Animated particles can change sprites after construction, so give them one more
        // chance here. Once captured, do not turn their movement into a trail of samples.
        if (!legions_client$worldBorderGlitterCaptured) {
            legions_client$worldBorderGlitterCaptured =
                    LegionsWorldBorder.captureGlitterParticle(sprite, x, y, z);
        }
        if (LegionsWorldBorder.shouldHideGlitterParticle(sprite)) {
            ci.cancel();
        }
    }
}
