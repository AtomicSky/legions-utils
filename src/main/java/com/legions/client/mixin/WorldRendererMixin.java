package com.legions.client.mixin;

import com.legions.client.LegionsFeatures;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
    // Reject filtered players before getAndUpdateRenderState, not after model preparation.
    // Keep this scoped to world rendering: inventory previews and entity ticking are untouched.
    @WrapOperation(
            method = "fillEntityRenderStates(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/Frustum;Lnet/minecraft/client/render/RenderTickCounter;Lnet/minecraft/client/render/state/WorldRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderManager;shouldRender(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/Frustum;DDD)Z"),
            require = 1
    )
    private boolean legions_client$cullBeforeRenderState(EntityRenderManager manager, Entity entity,
            Frustum frustum, double x, double y, double z, Operation<Boolean> original) {
        if (entity instanceof PlayerEntity player && LegionsFeatures.shouldHidePlayerModel(player)) {
            return false;
        }
        return original.call(manager, entity, frustum, x, y, z);
    }
}
