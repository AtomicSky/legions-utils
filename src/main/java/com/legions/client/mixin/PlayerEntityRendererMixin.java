package com.legions.client.mixin;

import com.legions.client.access.LegionsPlayerOverlayRenderStateAccess;
import com.legions.client.LegionsFeatures;
import com.legions.client.render.LegionsPlayerOverlayColorContext;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AvatarRenderer.class, priority = 500)
public class PlayerEntityRendererMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void legions_client$addLegionsNametagAndOutline(Avatar player, AvatarRenderState state, float tickProgress, CallbackInfo ci) {
        if (!(player instanceof Player playerEntity)) {
            return;
        }
        if (LegionsFeatures.shouldHidePlayerModel(playerEntity)) {
            state.isInvisible = true;
            state.isInvisibleToPlayer = true;
            state.nameTag = null;
            state.nameTagAttachment = null;
            state.shadowRadius = 0.0f;
            state.outlineColor = 0;
            if (state instanceof LegionsPlayerOverlayRenderStateAccess access) {
                access.legions_client$setFoeOverlayColor(-1);
                access.legions_client$setFoeOverlayStyle(LegionsPlayerOverlayColorContext.STYLE_OUTLINE);
            }
            return;
        }
        if (state.nameTag != null) {
            state.nameTag = LegionsFeatures.customizeNametag(playerEntity, state.nameTag);
        }
        int overlayColor = LegionsFeatures.getOutlineColor(playerEntity);
        int overlayStyle = LegionsFeatures.getOverlayStyle(playerEntity);
        int filledOverlayColor = LegionsFeatures.getFilledOverlayColor(playerEntity, overlayColor, overlayStyle);
        if (state instanceof LegionsPlayerOverlayRenderStateAccess access) {
            access.legions_client$setFoeOverlayColor(filledOverlayColor);
            access.legions_client$setFoeOverlayStyle(overlayStyle);
        }
        if (shouldDrawOutline(overlayColor, overlayStyle)) {
            state.outlineColor = overlayColor;
        }
    }

    private static boolean shouldDrawOutline(int color, int style) {
        return color != 0
                && (style == LegionsPlayerOverlayColorContext.STYLE_OUTLINE
                || style == LegionsPlayerOverlayColorContext.STYLE_OUTLINE_FULL);
    }
}
