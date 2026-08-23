package com.legions.client.mixin;

import com.legions.client.LegionsFeatures;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.atomics.client.TierWeightManager")
public abstract class AtomicsTierWeightManagerMixin {
    @Inject(method = "getNameSuffix", at = @At("HEAD"), cancellable = true, remap = false)
    private static void legions_client$replaceAtomicsTierSuffixWithLegionsScore(PlayerEntity player, CallbackInfoReturnable<Text> cir) {
        Text replacement = LegionsFeatures.getAtomicsTierSlotReplacement(player);
        if (replacement != null) {
            cir.setReturnValue(replacement);
        }
    }

    @Inject(method = "getNameSuffix", at = @At("RETURN"), cancellable = true, remap = false)
    private static void legions_client$useQuestionMarkWhenAtomicsHasNoTier(PlayerEntity player, CallbackInfoReturnable<Text> cir) {
        if (cir.getReturnValue() == null) {
            Text fallback = LegionsFeatures.getAtomicsUnknownTierSlotFallback(player);
            if (fallback != null) {
                cir.setReturnValue(fallback);
            }
        }
    }
}
