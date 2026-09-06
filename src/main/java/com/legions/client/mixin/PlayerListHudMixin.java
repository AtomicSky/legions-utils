package com.legions.client.mixin;

import com.legions.client.LegionsFeatures;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerListHud.class)
public class PlayerListHudMixin {
    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true, require = 1)
    private void legions_client$addRating(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        cir.setReturnValue(LegionsFeatures.customizeTabListName(entry, cir.getReturnValue()));
    }
}
