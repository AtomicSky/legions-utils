package com.legions.client.mixin;

import com.legions.client.access.LegionsPlayerOverlayRenderStateAccess;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PlayerEntityRenderState.class)
public class PlayerEntityRenderStateMixin implements LegionsPlayerOverlayRenderStateAccess {
    @Unique
    private int legions_client$foeOverlayColor = -1;
    @Unique
    private int legions_client$foeOverlayStyle = 0;

    @Override
    public void legions_client$setFoeOverlayColor(int color) {
        this.legions_client$foeOverlayColor = color;
    }

    @Override
    public int legions_client$getFoeOverlayColor() {
        return this.legions_client$foeOverlayColor;
    }

    @Override
    public void legions_client$setFoeOverlayStyle(int style) {
        this.legions_client$foeOverlayStyle = style;
    }

    @Override
    public int legions_client$getFoeOverlayStyle() {
        return this.legions_client$foeOverlayStyle;
    }
}
