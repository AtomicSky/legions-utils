package com.legions.client.mixin;

import com.legions.client.LegionsClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.atomics.client.AtomicsClient")
public abstract class AtomicsFriendFoeOverlayMixin {
    @Inject(method = "getPlayerFriendFoeOverlayColor", at = @At("HEAD"), cancellable = true, remap = false)
    private static void legions_client$suppressAtomicsFriendFoeOverlay(Player player, CallbackInfoReturnable<Integer> cir) {
        if (LegionsClient.enabled(Minecraft.getInstance())) {
            cir.setReturnValue(-1);
        }
    }
}
