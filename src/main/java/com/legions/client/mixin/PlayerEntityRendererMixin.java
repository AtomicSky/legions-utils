package com.legions.client.mixin;

import com.legions.client.access.LegionsPlayerOverlayRenderStateAccess;
import com.legions.client.LegionsFeatures;
import com.legions.client.render.LegionsPlayerOverlayColorContext;
import com.legions.client.render.LegionsQuipOutline;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PlayerEntityRenderer.class, priority = 500)
public class PlayerEntityRendererMixin {
    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void legions_client$addLegionsNametagAndOutline(PlayerLikeEntity player, PlayerEntityRenderState state, float tickProgress, CallbackInfo ci) {
        if (!(player instanceof PlayerEntity playerEntity)) {
            return;
        }
        int quipOutlineColor = LegionsFeatures.getQuipOutlineColor(playerEntity);
        if (LegionsFeatures.shouldHidePlayerModel(playerEntity)) {
            state.invisible = true;
            state.invisibleToPlayer = true;
            state.displayName = null;
            state.nameLabelPos = null;
            state.shadowRadius = 0.0f;
            state.outlineColor = 0;
            if (state instanceof LegionsPlayerOverlayRenderStateAccess access) {
                access.legions_client$setQuipOutline(0, 0.0F);
                access.legions_client$setFoeOverlayColor(-1);
                access.legions_client$setFoeOverlayStyle(LegionsPlayerOverlayColorContext.STYLE_OUTLINE);
            }
            return;
        }
        if (state.displayName != null) {
            state.displayName = LegionsFeatures.customizeNametag(playerEntity, state.displayName);
        }
        int overlayColor = LegionsFeatures.getOutlineColor(playerEntity);
        int overlayStyle = LegionsFeatures.getOverlayStyle(playerEntity);
        boolean foeOutline = shouldDrawOutline(overlayColor, overlayStyle);
        int filledOverlayColor = LegionsFeatures.getFilledOverlayColor(playerEntity, overlayColor, overlayStyle);
        if (state instanceof LegionsPlayerOverlayRenderStateAccess access) {
            access.legions_client$setQuipOutline(foeOutline ? 0 : quipOutlineColor,
                    LegionsQuipOutline.expansionFor(playerEntity));
            access.legions_client$setFoeOverlayColor(filledOverlayColor);
            access.legions_client$setFoeOverlayStyle(overlayStyle);
        }
        if (foeOutline) {
            state.outlineColor = opaque(overlayColor);
        }
    }

    private static int opaque(int color) {
        return color | 0xFF000000;
    }

    private static boolean shouldDrawOutline(int color, int style) {
        return color != 0
                && (style == LegionsPlayerOverlayColorContext.STYLE_OUTLINE
                || style == LegionsPlayerOverlayColorContext.STYLE_OUTLINE_FULL);
    }
}
