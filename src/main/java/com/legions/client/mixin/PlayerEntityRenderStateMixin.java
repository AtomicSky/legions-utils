package com.legions.client.mixin;

import com.legions.client.access.LegionsPlayerOverlayRenderStateAccess;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.HashSet;
import java.util.Set;

@Mixin(PlayerEntityRenderState.class)
public class PlayerEntityRenderStateMixin implements LegionsPlayerOverlayRenderStateAccess {
    @Unique
    private int legions_client$quipOutlineColor;
    @Unique
    private float legions_client$quipOutlineExpansion;
    @Unique
    private final Set<Object> legions_client$outlinedPasses = new HashSet<>();

    @Override
    public void legions_client$setQuipOutline(int color, float expansion) {
        legions_client$quipOutlineColor = color;
        legions_client$quipOutlineExpansion = expansion;
        legions_client$outlinedPasses.clear();
    }

    @Override
    public int legions_client$getQuipOutlineColor() {
        return legions_client$quipOutlineColor;
    }

    @Override
    public float legions_client$getQuipOutlineExpansion() {
        return legions_client$quipOutlineExpansion;
    }

    @Override
    public boolean legions_client$markQuipOutlinePass(Object passKey) {
        return legions_client$outlinedPasses.add(passKey);
    }

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
